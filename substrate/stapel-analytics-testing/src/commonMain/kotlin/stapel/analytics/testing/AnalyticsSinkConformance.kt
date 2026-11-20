package stapel.analytics.testing

import kotlinx.coroutines.CancellationException
import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.EventKind
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt
import kotlin.time.Instant

/**
 * A sink under test and the vendor side it talks to.
 *
 * @property sink the provider's sink.
 * @property probe what its vendor received (a recording bridge on iOS, a fake of the thin vendor
 *   wrapper on Android).
 */
public class SinkUnderTest(public val sink: AnalyticsSink, public val probe: VendorProbe)

/**
 * The conformance suite every analytics provider artifact subclasses, once per target it ships
 * (`commonTest` when the provider's sink is common code, else each platform's test set):
 *
 * ```kotlin
 * class FirebaseSinkConformanceTest : AnalyticsSinkConformance() {
 *     override fun configured() = RecordingFirebaseBridge().let { SinkUnderTest(firebaseAnalyticsSink(it), it) }
 *     override fun unconfigured() = RecordingFirebaseBridge(configured = false).let { SinkUnderTest(firebaseAnalyticsSink(it), it) }
 *     @Test fun conformance() = runTest { assertEquals(caseNames.size, verify()) }
 * }
 * ```
 *
 * The suite does not depend on a test framework: broken rules are reported as one [AssertionError]
 * naming every failed case. Cases (each gets fresh subjects):
 * - `availability-honest` — an unconfigured or unsupported sink says so and every member is a
 *   no-op that never throws and reaches no vendor event;
 * - `collection-idempotent` — the kill switch reaches the vendor, twice in a row is harmless;
 * - `events-in-order` — every track event of a batch reaches the vendor once, in order, accepted;
 * - `page-is-a-screen` — a page event becomes the provider's screen call;
 * - `identity-follows-and-resets` — an identify event sets the vendor user id to the hash,
 *   [AnalyticsSink.reset] clears it;
 * - `user-property` — a user property reaches the vendor;
 * - `failure-is-retry` — a vendor failure is [SinkReceipt.Retry], never an exception, and the next
 *   delivery works again.
 */
public abstract class AnalyticsSinkConformance {
    /** A configured sink and its vendor probe; called once per case. */
    protected abstract fun configured(): SinkUnderTest

    /** A sink without its configuration, or null when the provider has no such state on this target. */
    protected abstract fun unconfigured(): SinkUnderTest?

    /** The vendor-side name of hub event [name] (providers that sanitise names override it). */
    protected open fun vendorEventName(name: String): String = name

    /** The vendor call a page event named [name] becomes. */
    protected open fun vendorPage(name: String): VendorCall = VendorCall.Screen(name)

    /** The vendor-side name of user property [name]. */
    protected open fun vendorPropertyName(name: String): String = name

    /** Names of the cases [verify] runs. */
    public val caseNames: List<String> get() = cases.map { it.first }

    /** Runs every case; throws one [AssertionError] listing the failed ones. Returns the number of cases that ran. */
    public suspend fun verify(): Int {
        val failures = mutableListOf<String>()
        for ((name, case) in cases) {
            try {
                case()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$name: threw ${e::class.simpleName}: ${e.message}"
            } catch (e: AssertionError) {
                failures += "$name: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("analytics sink conformance failed (${failures.size}/${cases.size}):\n" + failures.joinToString("\n") { "  - $it" })
        }
        return cases.size
    }

    private var seq = 0

    private fun event(kind: EventKind, name: String, userHash: String? = null, props: Map<String, String> = emptyMap()): AnalyticsEvent {
        seq++
        return AnalyticsEvent("1767225600000-$seq", kind, name, props, userHash, "anon-1", "session-1", Instant.fromEpochMilliseconds(1_767_225_600_000L + seq))
    }

    private val cases: List<Pair<String, suspend () -> Unit>> = listOf(
        "availability-honest" to {
            val subject = unconfigured()
            if (subject != null) {
                val s = subject.sink
                val a = s.availability
                check(a is SinkAvailability.Unconfigured || a is SinkAvailability.Unsupported) { "an unconfigured sink reported $a" }
                s.setCollectionEnabled(false)
                s.setCollectionEnabled(true)
                s.setUserProperty("plan", "pro")
                val receipt = s.deliver(listOf(event(EventKind.Track, "unconfigured.event")))
                s.reset()
                check(receipt != SinkReceipt.Accepted) { "an unavailable sink claimed to have delivered" }
                val leaked = subject.probe.calls.filter { it is VendorCall.Event || it is VendorCall.Screen }
                check(leaked.isEmpty()) { "an unavailable sink reached the vendor: $leaked" }
            }
            val ready = configured().sink.availability
            check(ready == SinkAvailability.Ready) { "a configured sink reported $ready" }
        },
        "collection-idempotent" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(false)
            subject.sink.setCollectionEnabled(false)
            check(subject.probe.collection() == false) { "collection off did not reach the vendor: ${subject.probe.calls}" }
            subject.sink.setCollectionEnabled(true)
            subject.sink.setCollectionEnabled(true)
            check(subject.probe.collection() == true) { "collection on did not reach the vendor: ${subject.probe.calls}" }
        },
        "events-in-order" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(true)
            val batch = listOf("checkout.started", "checkout.paid", "flow.auth.login.CodeSent").map { event(EventKind.Track, it, props = mapOf("step" to "1")) }
            val receipt = subject.sink.deliver(batch)
            check(receipt == SinkReceipt.Accepted) { "a healthy vendor gave $receipt" }
            val names = subject.probe.calls.filterIsInstance<VendorCall.Event>().map { it.name }
            val expected = batch.map { vendorEventName(it.name) }
            check(names == expected) { "vendor saw $names, expected $expected" }
        },
        "page-is-a-screen" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(true)
            subject.sink.deliver(listOf(event(EventKind.Page, "shop.home")))
            val expected = vendorPage("shop.home")
            check(expected in subject.probe.calls) { "vendor saw ${subject.probe.calls}, expected $expected" }
        },
        "identity-follows-and-resets" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(true)
            val hash = "c6c289e49e9c05b2145860387b73bcb18df43fb09a1e4a4a9713c76c88bb541b"
            subject.sink.deliver(listOf(event(EventKind.Identify, "identify", userHash = hash), event(EventKind.Track, "signed.in", userHash = hash)))
            check(subject.probe.userId() == hash) { "vendor user id is ${subject.probe.userId()}, expected the hash" }
            subject.sink.reset()
            check(VendorCall.Reset in subject.probe.calls) { "reset did not reach the vendor" }
            check(subject.probe.userId() == null) { "the vendor user id survived reset" }
        },
        "user-property" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(true)
            subject.sink.setUserProperty("plan", "pro")
            val expected = VendorCall.UserProperty(vendorPropertyName("plan"), "pro")
            check(expected in subject.probe.calls) { "vendor saw ${subject.probe.calls}, expected $expected" }
        },
        "failure-is-retry" to {
            val subject = configured()
            subject.sink.setCollectionEnabled(true)
            subject.probe.failNext()
            val failed = subject.sink.deliver(listOf(event(EventKind.Track, "will.fail")))
            check(failed is SinkReceipt.Retry) { "a failing vendor gave $failed" }
            val next = subject.sink.deliver(listOf(event(EventKind.Track, "works.again")))
            check(next == SinkReceipt.Accepted) { "the sink did not recover: $next" }
        },
    )

    private fun VendorProbe.collection(): Boolean? = calls.filterIsInstance<VendorCall.Collection>().lastOrNull()?.enabled

    private fun VendorProbe.userId(): String? = calls.filterIsInstance<VendorCall.UserId>().lastOrNull()?.id

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }
}
