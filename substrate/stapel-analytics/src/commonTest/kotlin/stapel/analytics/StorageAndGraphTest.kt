package stapel.analytics

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.koin.dsl.module
import stapel.analytics.di.analyticsModule
import stapel.di.contribute
import stapel.di.stapelKoin
import stapel.runtime.Analytics
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.testing.RecordingLogger
import stapel.testing.TestDispatchers
import stapel.testing.di.testOverridesModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StorageAndGraphTest {
    @Test
    fun fileStorageRoundTripsReplacesAndDeletes() = runTest {
        val dir = Path(SystemTemporaryDirectory, "stapel-analytics-test-${kotlin.random.Random(42).nextLong().toULong()}")
        val storage = FileAnalyticsStorage(dir.toString(), StandardTestDispatcher(testScheduler))
        assertNull(storage.read("queue"))
        storage.write("queue", "{\"v\":1}")
        storage.write("queue", "{\"v\":2}")
        assertEquals("{\"v\":2}", FileAnalyticsStorage(dir.toString(), StandardTestDispatcher(testScheduler)).read("queue"))
        assertFalse(SystemFileSystem.exists(Path(dir, "queue.tmp")), "the temporary file is moved into place")
        storage.write("queue", null)
        assertNull(storage.read("queue"))
        SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(dir)
    }

    @Test
    fun theModuleBindsOneHubAsAnalyticsAndControlFromContributedSinks() = runTest {
        val logger = RecordingLogger()
        val app = stapelKoin(
            listOf(
                runtimeModule(object : PlatformContext {}),
                analyticsModule(),
                module {
                    single<AnalyticsStorage> { InMemoryAnalyticsStorage() }
                    contribute<AnalyticsSink>("analytics.log") { LogSink(get()) }
                    contribute<AnalyticsSink>("analytics.other") { LogSink(get(), id = "other") }
                },
            ),
            overrides = listOf(
                testOverridesModule(appScope = backgroundScope, dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)), logger = logger),
            ),
            global = false,
        )
        val analytics = app.koin.get<Analytics>()
        val control = app.koin.get<AnalyticsControl>()
        assertSame<Any>(analytics, control)
        assertEquals(listOf("log", "other"), control.sinks.value.map { it.id })
        control.setConsent(Consent(ConsentState.Granted))
        analytics.track("graph.ok")
        control.flush()
        assertTrue(logger.contains("stapel.analytics event track graph.ok"))
        assertTrue(logger.contains("stapel.analytics collection log false"))
        app.close()
    }
}
