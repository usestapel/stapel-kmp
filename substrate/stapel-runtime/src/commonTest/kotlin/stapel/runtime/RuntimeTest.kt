package stapel.runtime

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.result.text.LocaleTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RuntimeTest {
    @Test
    fun withoutAnOsObserverReportsDriveTheStateAndOfflineDecays() = runTest {
        val c = ConnectivityState(backgroundScope, osObserved = false, offlineDecay = 5.seconds)
        assertEquals(NetworkStatus.Unknown, c.status.value)
        c.reportReachable()
        assertEquals(NetworkStatus.Online(), c.status.value)
        c.reportUnreachable()
        assertEquals(NetworkStatus.Offline, c.status.value)
        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(NetworkStatus.Offline, c.status.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(NetworkStatus.Unknown, c.status.value, "an offline mark with no observer must decay, or nothing is ever sent again")
    }

    @Test
    fun aSuccessCancelsThePendingDecay() = runTest {
        val c = ConnectivityState(backgroundScope, osObserved = false, offlineDecay = 5.seconds)
        c.reportUnreachable()
        c.reportReachable()
        c.reportUnreachable()
        advanceTimeBy(3.seconds)
        c.reportReachable()
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(NetworkStatus.Online(), c.status.value)
    }

    @Test
    fun withAnOsObserverTheOsIsAuthoritative() = runTest {
        val c = ConnectivityState(backgroundScope, osObserved = true)
        c.reportReachable()
        assertEquals(NetworkStatus.Online(), c.status.value, "a success settles an unknown state")
        c.osChanged(NetworkStatus.Offline)
        c.reportReachable()
        assertEquals(NetworkStatus.Offline, c.status.value)
        c.osChanged(NetworkStatus.Online(metered = true))
        c.reportUnreachable()
        assertEquals(NetworkStatus.Online(metered = true), c.status.value, "one failed request does not override a validated network")
    }

    @Test
    fun appLocaleOverridesTheDeviceUntilCleared() {
        val p = DeviceLocaleProvider(LocaleTag("en-US"))
        p.setAppLocale(LocaleTag("ru"))
        p.deviceLocaleChanged(LocaleTag("de-DE"))
        assertEquals(LocaleTag("ru"), p.locale.value)
        p.setAppLocale(null)
        assertEquals(LocaleTag("de-DE"), p.locale.value)
    }

    @Test
    fun flowStepEventShapeMatchesTheWeb() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val a = object : Analytics {
            override fun track(event: String, props: Map<String, String>) {
                events += event to props
            }
            override fun page(name: String, props: Map<String, String>) {}
            override fun identify(userHash: String?) {}
        }
        a.trackFlowStep("login", "otp", FlowStepPhase.Failed, mapOf("method" to "email"))
        assertEquals("flow.login.otp" to mapOf("phase" to "failed", "method" to "email"), events.single())
    }

    @Test
    fun idsAreUniqueCanonicalUuids() {
        val ids = randomIdGenerator()
        val a = ids.uuid()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(a), a)
        assertNotEquals(a, ids.uuid())
    }

    @Test
    fun loggingHelpersBuildTextOnlyWhenEnabled() {
        var built = 0
        NoopLogger.debug("t") { built++; "x" }
        assertEquals(0, built)
        val records = mutableListOf<String>()
        val logger = object : StapelLogger {
            override fun isEnabled(level: LogLevel, tag: String) = level >= LogLevel.Info
            override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
                records += "$level $message"
            }
        }
        logger.debug("t") { built++; "d" }
        logger.warn("t") { built++; "w" }
        assertEquals(listOf("Warn w"), records)
        assertEquals(1, built)
        assertEquals("m {a=1}", formatRecord("m", null, mapOf("a" to "1")))
        assertFalse(NoopLogger.isEnabled(LogLevel.Error, "t"))
    }

    @Test
    fun defaultDispatchersAreDistinctObjects() {
        val d = defaultDispatchers()
        assertTrue(d.io === d.io)
        assertTrue(d.default === d.default)
    }
}
