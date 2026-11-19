package stapel.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import stapel.di.StapelQualifiers
import stapel.di.stapelKoin
import stapel.result.text.LocaleTag
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LogLevel
import stapel.runtime.NetworkStatus
import stapel.runtime.PlatformContext
import stapel.runtime.StapelLogger
import stapel.runtime.di.runtimeModule
import stapel.runtime.warn
import stapel.testing.di.testOverridesModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FakesTest {
    @Test
    fun testClockMovesOnlyWhenTold() {
        val clock = TestClock()
        val t0 = clock.now()
        assertEquals(t0, clock.now())
        clock.advance(5.seconds)
        assertEquals(t0 + 5.seconds, clock.now())
    }

    @Test
    fun virtualTimeClockFollowsTheScheduler() = runTest {
        val clock = virtualTimeClock()
        val t0 = clock.now()
        advanceTimeBy(3.seconds)
        assertEquals(t0 + 3.seconds, clock.now())
    }

    @Test
    fun sequentialIdsAreDeterministic() {
        val ids = SequentialIdGenerator()
        assertEquals("00000000-0000-4000-8000-000000000001", ids.uuid())
        assertEquals("00000000-0000-4000-8000-000000000002", ids.uuid())
    }

    @Test
    fun recordingLoggerFindsLeakedSecrets() {
        val logger = RecordingLogger(LogLevel.Info)
        logger.log(LogLevel.Debug, "t", "dropped token-1")
        logger.warn("net") { "refresh failed" }
        logger.log(LogLevel.Error, "net", "x", fields = mapOf("auth" to "Bearer token-2"))
        assertEquals(2, logger.records.size)
        assertFalse(logger.contains("token-1"))
        assertTrue(logger.contains("token-2"))
    }

    @Test
    fun fakesBehaveLikeTheRealSeams() {
        val c = FakeConnectivity(NetworkStatus.Offline)
        c.reportUnreachable()
        assertEquals(1, c.unreachableReports)
        assertEquals(NetworkStatus.Offline, c.status.value)
        val l = FixedLocaleProvider(LocaleTag("en"))
        l.setAppLocale(LocaleTag("ru"))
        l.setDeviceLocale(LocaleTag("de"))
        assertEquals(LocaleTag("ru"), l.locale.value)
        l.setAppLocale(null)
        assertEquals(LocaleTag("de"), l.locale.value)
        assertEquals("https://api.test/auth/api/v1/x/", testEnvironment().url("auth", "/auth/api/v1/x/"))
    }

    @Test
    fun overridesReplaceEveryRuntimeBinding() = runTest {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val connectivity = FakeConnectivity()
        val app = stapelKoin(
            modules = listOf(runtimeModule(PlatformContextForTest)),
            overrides = listOf(testOverridesModule(backgroundScope, dispatchers, connectivity = connectivity)),
            global = false,
        )
        val koin = app.koin
        assertSame(connectivity, koin.get<Connectivity>())
        assertIs<TestClock>(koin.get<Clock>())
        assertIs<SequentialIdGenerator>(koin.get<IdGenerator>())
        assertIs<RecordingLogger>(koin.get<StapelLogger>())
        assertSame(backgroundScope, koin.get<CoroutineScope>(StapelQualifiers.AppScope))
        app.close()
    }
}

/** runtimeModule needs a handle; the overrides replace everything that would use it. */
private object PlatformContextForTest : PlatformContext
