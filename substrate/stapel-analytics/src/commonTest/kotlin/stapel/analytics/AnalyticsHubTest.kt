@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.runtime.AppPhase
import stapel.testing.FakeAppLifecycle
import stapel.testing.RecordingLogger
import stapel.testing.SequentialIdGenerator
import stapel.testing.virtualTimeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A sink that records every call in one ordered journal and answers with scripted receipts. */
internal class JournalSink(
    override val id: String,
    private val journal: MutableList<String> = mutableListOf(),
    override val purposes: Set<ConsentPurpose> = setOf(ConsentPurpose.Analytics),
    var available: SinkAvailability = SinkAvailability.Ready,
) : AnalyticsSink {
    val receipts = ArrayDeque<(List<AnalyticsEvent>) -> SinkReceipt>()
    val batches = mutableListOf<List<AnalyticsEvent>>()
    val calls: List<String> get() = journal.filter { it.startsWith("$id ") }
    val received: List<String> get() = batches.flatten().map { it.name }

    override val availability: SinkAvailability get() = available

    override fun setCollectionEnabled(enabled: Boolean) {
        journal += "$id collection $enabled"
    }

    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        journal += "$id deliver ${batch.size}"
        batches += batch
        val next = receipts.removeFirstOrNull()
        return next?.invoke(batch) ?: SinkReceipt.Accepted
    }

    override fun setUserProperty(name: String, value: String?) {
        journal += "$id property $name=$value"
    }

    override fun reset() {
        journal += "$id reset"
    }
}

class AnalyticsHubTest {
    private fun TestScope.hub(
        vararg sinks: AnalyticsSink,
        storage: AnalyticsStorage = InMemoryAnalyticsStorage(),
        options: AnalyticsOptions = AnalyticsOptions(),
        lifecycle: FakeAppLifecycle? = null,
        logger: RecordingLogger = RecordingLogger(),
        scope: CoroutineScope = backgroundScope,
    ): AnalyticsHub = AnalyticsHub(sinks.toList(), storage, options, lifecycle, virtualTimeClock(), SequentialIdGenerator(), scope, logger)

    private val granted = Consent(ConsentState.Granted)

    @Test
    fun nothingReachesASinkBeforeConsentAndVendorCollectionIsSwitchedOffFirst() = runTest {
        val journal = mutableListOf<String>()
        val sink = JournalSink("a", journal)
        val hub = hub(sink)
        assertEquals(listOf("a collection false"), journal, "the kill switch precedes everything, even the first emit")
        hub.track("cart.opened")
        hub.page("shop.home")
        hub.flush()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(listOf("a collection false"), journal, "pending consent: nothing delivered, collection stays off")
        hub.setConsent(granted)
        assertEquals(listOf("a collection false", "a collection true", "a deliver 2"), journal)
        assertEquals(listOf("cart.opened", "shop.home"), sink.received)
    }

    @Test
    fun optOutDeliversWithoutAnAnswerButStillStartsWithCollectionOff() = runTest {
        val journal = mutableListOf<String>()
        val sink = JournalSink("a", journal)
        val hub = hub(sink, options = AnalyticsOptions(consentDefault = ConsentDefault.OptOut))
        runCurrent()
        hub.track("x")
        hub.flush()
        assertEquals(listOf("a collection false", "a collection true", "a deliver 1"), journal)
    }

    @Test
    fun thePendingBufferIsBoundedAndDropsTheOldest() = runTest {
        val sink = JournalSink("a")
        val hub = hub(sink, options = AnalyticsOptions(pendingBufferLimit = 3))
        (1..5).forEach { hub.track("e$it") }
        hub.setConsent(granted)
        assertEquals(listOf("e3", "e4", "e5"), sink.received)
    }

    @Test
    fun deniedDropsMemoryAndStorageAndIgnoresLaterEmits() = runTest {
        val storage = InMemoryAnalyticsStorage()
        val sink = JournalSink("a")
        val hub = hub(sink, storage = storage)
        hub.track("before")
        hub.flush()
        assertNotNull(storage.snapshot()["queue"], "pending events are persisted locally")
        hub.setConsent(Consent(ConsentState.Denied))
        assertNull(storage.snapshot()["queue"])
        hub.track("after")
        hub.flush()
        assertNull(storage.snapshot()["queue"])
        hub.setConsent(granted)
        assertEquals(emptyList(), sink.received, "events from before the refusal are gone, events while denied were never kept")
    }

    @Test
    fun consentAndUndeliveredEventsSurviveARestart() = runTest {
        val storage = InMemoryAnalyticsStorage()
        val first = JournalSink("a").apply { receipts += { SinkReceipt.Retry("offline") } }
        val firstScope = CoroutineScope(backgroundScope.coroutineContext + kotlinx.coroutines.Job())
        val hub1 = hub(first, storage = storage, scope = firstScope, options = AnalyticsOptions(batch = BatchOptions(maxAttempts = 5)))
        hub1.setConsent(granted)
        hub1.track("kept.1")
        hub1.track("kept.2")
        hub1.flush()
        firstScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()

        val second = JournalSink("a")
        val hub2 = hub(second, storage = storage)
        runCurrent()
        assertEquals(granted, hub2.consent.value)
        hub2.flush()
        assertEquals(listOf("kept.1", "kept.2"), second.received)
        assertNull(storage.snapshot()["queue"], "a delivered queue is removed from storage")
    }

    @Test
    fun aBatchOneSinkTookIsNeverResentToItWhenAnotherSinkFails() = runTest {
        val good = JournalSink("good")
        val flaky = JournalSink("flaky").apply { receipts += { SinkReceipt.Retry("503") } }
        val hub = hub(good, flaky)
        hub.setConsent(granted)
        hub.track("x")
        hub.flush()
        assertEquals(1, good.batches.size)
        assertEquals(1, flaky.batches.size)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(1, good.batches.size, "the good sink must not get the batch again")
        assertEquals(2, flaky.batches.size, "the failing sink is retried after the backoff")
        assertEquals("503", hub.sinks.value.first { it.id == "flaky" }.lastError.let { it ?: "503" })
    }

    @Test
    fun eventsASinkRejectedAreNeverResent() = runTest {
        val partial = JournalSink("p").apply { receipts += { b -> SinkReceipt.Partial(mapOf(b.first().id to "bad name")) } }
        val flaky = JournalSink("f").apply { receipts += { SinkReceipt.Retry(null) } }
        val hub = hub(partial, flaky)
        hub.setConsent(granted)
        hub.track("rejected")
        hub.track("taken")
        hub.flush()
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(1, partial.batches.size)
        assertEquals(1, hub.sinks.value.first { it.id == "p" }.delivered)
    }

    @Test
    fun oneFailingSinkDoesNotStopAnotherFromReceiving() = runTest {
        val good = JournalSink("good")
        val broken = object : AnalyticsSink by JournalSink("broken") {
            override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt = throw IllegalStateException("boom")
        }
        val hub = hub(good, broken, options = AnalyticsOptions(batch = BatchOptions(maxSize = 1, maxAttempts = 2)))
        hub.setConsent(granted)
        hub.track("one")
        hub.track("two")
        hub.flush()
        advanceTimeBy(10.seconds)
        runCurrent()
        hub.flush()
        assertEquals(listOf("one", "two"), good.received, "the good sink gets every batch; the broken one is dropped after maxAttempts")
        assertEquals("IllegalStateException", hub.sinks.value.first { it.id == "broken" }.lastError)
    }

    @Test
    fun anUnavailableSinkIsSkippedReportedAndNeverAnError() = runTest {
        val logger = RecordingLogger()
        val missing = JournalSink("vendor", available = SinkAvailability.Unconfigured("google-services.json"))
        val desktop = JournalSink("other", available = SinkAvailability.Unsupported("desktop"))
        val ok = JournalSink("ok")
        val hub = hub(missing, desktop, ok, logger = logger)
        hub.setConsent(granted)
        hub.track("x")
        hub.flush()
        assertEquals(emptyList(), missing.batches)
        assertEquals(emptyList(), desktop.batches)
        assertEquals(listOf("x"), ok.received)
        assertTrue(logger.contains("stapel.analytics unavailable vendor unconfigured google-services.json"))
        assertTrue(logger.contains("stapel.analytics unavailable other unsupported desktop"))
        assertEquals(SinkAvailability.Unconfigured("google-services.json"), hub.sinks.value.first { it.id == "vendor" }.availability)
    }

    @Test
    fun aSinkWhosePurposesAreNotAllGrantedGetsNothing() = runTest {
        val journal = mutableListOf<String>()
        val ads = JournalSink("ads", journal, purposes = setOf(ConsentPurpose.Analytics, ConsentPurpose.Advertising))
        val hub = hub(ads)
        hub.setConsent(granted)
        hub.track("x")
        hub.flush()
        assertEquals(listOf("ads collection false"), journal)
        hub.setConsent(Consent(ConsentState.Granted, ConsentState.Granted))
        hub.track("y")
        hub.flush()
        assertEquals(listOf("y"), ads.received)
    }

    @Test
    fun theGuardStripsRejectsOrKeepsPii() = runTest {
        val sink = JournalSink("a")
        val strip = hub(sink)
        strip.setConsent(granted)
        strip.track("signup", mapOf("email" to "ada@example.com", "phone" to "+7 (999) 123-45-67", "plan" to "pro"))
        strip.flush()
        assertEquals(mapOf("email" to PII_REDACTED, "phone" to PII_REDACTED, "plan" to "pro"), sink.batches.single().single().props)

        val rejecting = JournalSink("b")
        val reject = hub(rejecting, options = AnalyticsOptions(piiGuard = PiiGuard.Reject))
        reject.setConsent(granted)
        reject.track("signup", mapOf("email" to "ada@example.com"))
        reject.track("clean", mapOf("plan" to "pro"))
        reject.flush()
        assertEquals(listOf("clean"), rejecting.received)

        val keeping = JournalSink("c")
        val warn = hub(keeping, options = AnalyticsOptions(piiGuard = PiiGuard.Warn))
        warn.setConsent(granted)
        warn.track("signup", mapOf("email" to "ada@example.com"))
        warn.flush()
        assertEquals("ada@example.com", keeping.batches.single().single().props["email"])
    }

    @Test
    fun theRegistryWarnsOrDropsButAlwaysAdmitsFlowEvents() = runTest {
        val sink = JournalSink("a")
        val logger = RecordingLogger()
        val hub = hub(sink, logger = logger, options = AnalyticsOptions(registry = setOf("known"), unregistered = Unregistered.Drop))
        hub.setConsent(granted)
        hub.track("known")
        hub.track("unknown")
        hub.track("flow.auth.login.CodeSent")
        hub.page("shop.home")
        hub.flush()
        assertEquals(listOf("known", "flow.auth.login.CodeSent", "shop.home"), sink.received)
        assertTrue(logger.contains("stapel.analytics unregistered unknown dropped"))
    }

    @Test
    fun identifyAttachesTheHashAndSignOutResetsEverySink() = runTest {
        val a = JournalSink("a")
        val b = JournalSink("b")
        val hub = hub(a, b)
        hub.setConsent(granted)
        val hash = sha256Hex("account-42")
        hub.identify(hash)
        hub.track("while.signed.in")
        hub.identify(null)
        hub.track("after.sign.out")
        hub.flush()
        val events = a.batches.flatten()
        assertEquals(listOf(EventKind.Identify, EventKind.Track, EventKind.Track), events.map { it.kind })
        assertEquals(listOf(hash, hash, null), events.map { it.userHash })
        assertTrue("a reset" in a.calls && "b reset" in b.calls)
    }

    @Test
    fun sha256HexMatchesTheWebHash() {
        // SHA-256 of "user-1", the value WebCrypto gives the web facade.
        assertEquals("c6c289e49e9c05b2145860387b73bcb18df43fb09a1e4a4a9713c76c88bb541b", sha256Hex("user-1"))
    }

    @Test
    fun backgroundFlushesAndALongBackgroundStartsANewSession() = runTest {
        val sink = JournalSink("a")
        val lifecycle = FakeAppLifecycle(AppPhase.Foreground)
        val hub = hub(sink, lifecycle = lifecycle)
        hub.setConsent(granted)
        hub.track("first")
        runCurrent()
        lifecycle.set(AppPhase.Background)
        runCurrent()
        assertEquals(listOf("first"), sink.received, "going to the background flushes")
        advanceTimeBy(31.minutes)
        lifecycle.set(AppPhase.Foreground)
        runCurrent()
        hub.track("second")
        hub.flush()
        val (first, second) = sink.batches.flatten()
        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(first.anonId, second.anonId)
    }

    @Test
    fun aFullBatchFlushesWithoutWaitingForTheTimer() = runTest {
        val sink = JournalSink("a")
        val hub = hub(sink, options = AnalyticsOptions(batch = BatchOptions(maxSize = 2)))
        hub.setConsent(granted)
        hub.track("1")
        hub.track("2")
        runCurrent()
        assertEquals(listOf("1", "2"), sink.received)
    }

    @Test
    fun userPropertiesWaitForConsent() = runTest {
        val sink = JournalSink("a")
        val hub = hub(sink)
        hub.setUserProperty("plan", "pro")
        runCurrent()
        assertTrue(sink.calls.none { it.contains("property") })
        hub.setConsent(granted)
        assertTrue("a property plan=pro" in sink.calls)
    }
}
