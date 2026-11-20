package stapel.push.testing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import stapel.push.ForegroundPresentation
import stapel.push.Importance
import stapel.push.ProviderAvailability
import stapel.push.PushChannelSpec
import stapel.push.PushError
import stapel.push.PushEvent
import stapel.push.PushMessage
import stapel.push.PushPermission
import stapel.push.PushProvider
import stapel.push.allowsDelivery
import stapel.result.StapelResult
import kotlin.time.Duration.Companion.seconds

/**
 * Drives a provider under test from the outside: what the OS and the vendor SDK would do. A
 * provider artifact implements it over its fake of the vendor (a recording bridge on iOS, a fake of
 * the thin wrapper around the vendor statics on Android).
 */
public interface PushProviderHarness {
    /** The provider under test. */
    public val provider: PushProvider

    /** The OS grants notifications (the person said yes). */
    public suspend fun grant()

    /** The vendor SDK rotates its token on its own. */
    public suspend fun rotate()

    /** A notification arrives while the process is alive. */
    public suspend fun deliver(message: PushMessage, foreground: Boolean)

    /** The person taps a notification. */
    public suspend fun tap(message: PushMessage)
}

/**
 * The behaviour every [PushProvider] must show, on every target it ships for.
 *
 * ```
 * @Test fun ready() = runTest { PushProviderContract.verify { MyHarness() } }
 * @Test fun unconfigured() = runTest { PushProviderContract.verifyUnavailable(myProvider(configured = false)) }
 * ```
 * A broken rule is reported as one [AssertionError] naming every failed case.
 */
public object PushProviderContract {
    /** Names of the cases [verify] runs, in order. */
    public val caseNames: List<String> get() = cases.map { it.first }

    /** Runs every case against a fresh harness (one per case); returns how many ran. */
    public suspend fun verify(newHarness: suspend () -> PushProviderHarness): Int {
        val failures = mutableListOf<String>()
        for ((name, case) in cases) {
            try {
                case(newHarness())
            } catch (e: AssertionError) {
                failures += "$name: ${e.message}"
            } catch (e: TimeoutCancellationException) {
                failures += "$name: nothing arrived in time (${e.message})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$name: unexpected ${e::class.simpleName}: ${e.message}"
            }
        }
        report(failures, cases.size)
        return cases.size
    }

    /**
     * A provider that is not [ProviderAvailability.Ready] (no configuration, desktop) answers every
     * call without throwing and without showing a prompt.
     */
    public suspend fun verifyUnavailable(provider: PushProvider) {
        val failures = mutableListOf<String>()
        suspend fun check(name: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: AssertionError) {
                failures += "$name: ${e.message}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$name: threw ${e::class.simpleName}: ${e.message}"
            }
        }
        check("availability is not Ready") { expect(provider.availability != ProviderAvailability.Ready) { "use verify() for a ready provider" } }
        check("permission is Unsupported") {
            expect(provider.permission() == PushPermission.Unsupported) { "permission() = ${provider.permission()}" }
        }
        check("request shows nothing") {
            expect(provider.requestPermission(false) == PushPermission.Unsupported) { "requestPermission answered a real permission" }
        }
        check("token is Unavailable") {
            val token = provider.token()
            expect(token is StapelResult.Err && token.error is PushError.Unavailable) { "token() = $token" }
        }
        check("deleteToken does not throw") { provider.deleteToken() }
        check("presentation and channels do not throw") {
            provider.setForegroundPresentation(ForegroundPresentation.InApp)
            provider.ensureChannels(listOf(PushChannelSpec("default", "Default", Importance.Default)))
        }
        report(failures, 6)
    }

    private fun report(failures: List<String>, total: Int) {
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "PushProviderContract: ${failures.size} of $total cases failed:\n" + failures.joinToString("\n") { "  - $it" },
            )
        }
    }

    private inline fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    private suspend fun PushProvider.mustToken() = when (val t = token()) {
        is StapelResult.Ok -> t.value
        is StapelResult.Err -> throw AssertionError("token() failed: ${t.error.code.value}")
    }

    private val message = PushMessage(
        id = "contract-1",
        title = "Contract",
        body = "Body",
        data = mapOf("notification_type" to "contract", "url" to "app://contract/1", "group" to "system"),
        provider = "contract",
    )

    private val cases: List<Pair<String, suspend (PushProviderHarness) -> Unit>> = listOf(
        "identity: a ready provider has an id and a platform" to { h ->
            expect(h.provider.availability == ProviderAvailability.Ready) { "availability = ${h.provider.availability}" }
            expect(h.provider.id.isNotBlank()) { "blank id" }
            expect(h.provider.platform != null) { "a ready provider must name its platform" }
        },
        "permission: granted by the OS reads as allowing delivery" to { h ->
            h.grant()
            val p = h.provider.permission()
            expect(p.allowsDelivery) { "permission() = $p after grant" }
        },
        "token: stable until deleted" to { h ->
            h.grant()
            val a = h.provider.mustToken()
            val b = h.provider.mustToken()
            expect(a.value == b.value) { "two calls minted two tokens" }
            expect(a.provider == h.provider.id) { "token names provider ${a.provider}, not ${h.provider.id}" }
        },
        "deleteToken: the next token is a new one" to { h ->
            h.grant()
            val a = h.provider.mustToken()
            val deleted = h.provider.deleteToken()
            expect(deleted is StapelResult.Ok) { "deleteToken() = $deleted" }
            val b = h.provider.mustToken()
            expect(a.value != b.value) { "the token survived deleteToken()" }
        },
        "rotation: the tokens flow reports the new token" to { h ->
            h.grant()
            val a = h.provider.mustToken()
            val rotated = coroutineScope {
                val next = async(start = CoroutineStart.UNDISPATCHED) { h.provider.tokens.first() }
                h.rotate()
                withTimeout(5.seconds) { next.await() }
            }
            expect(rotated.value != a.value) { "rotation reported the old token" }
            expect(h.provider.mustToken().value == rotated.value) { "token() does not answer the rotated token" }
        },
        "events: a notification before the first collector is kept" to { h ->
            h.deliver(message, foreground = true)
            val event = withTimeout(5.seconds) { h.provider.events.first() }
            expect(event is PushEvent.Received && event.foreground) { "first event = $event" }
            expect(event.message.data["url"] == "app://contract/1") { "data lost: ${event.message.data}" }
            expect(event.message.data["notification_type"] == "contract") { "data lost: ${event.message.data}" }
        },
        "events: a tap is delivered as Opened with its data" to { h ->
            h.tap(message)
            val event = withTimeout(5.seconds) { h.provider.events.first() }
            expect(event is PushEvent.Opened) { "first event = $event" }
            expect(event.message.data["url"] == "app://contract/1") { "data lost: ${event.message.data}" }
        },
        "presentation and channels do not throw" to { h ->
            h.provider.setForegroundPresentation(ForegroundPresentation.InApp)
            h.provider.setForegroundPresentation(ForegroundPresentation.System)
            h.provider.ensureChannels(listOf(PushChannelSpec("system", "System", Importance.Default)))
        },
    )
}
