package stapel.analytics.collector

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.koin.dsl.module
import stapel.analytics.AnalyticsControl
import stapel.analytics.AnalyticsStorage
import stapel.analytics.Consent
import stapel.analytics.ConsentState
import stapel.analytics.InMemoryAnalyticsStorage
import stapel.analytics.collector.di.analyticsCollectorModule
import stapel.analytics.di.analyticsModule
import stapel.di.stapelKoin
import stapel.network.di.networkModule
import stapel.runtime.Analytics
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.testing.FakeTransport
import stapel.testing.TestDispatchers
import stapel.testing.di.testOverridesModule
import stapel.testing.testEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** runtime + network + analytics hub + collector composed through `stapelKoin`; nothing leaves before consent. */
class GraphTest {
    @Test
    fun theCollectorIsContributedOnTheBareClientAndSendsOnlyAfterConsent() = runTest {
        val wire = FakeTransport().apply { enqueueJson(202, """{"accepted":1}""") }
        val app = stapelKoin(
            listOf(
                runtimeModule(object : PlatformContext {}),
                networkModule(testEnvironment()),
                analyticsModule(),
                analyticsCollectorModule(CollectorOptions(source = "desktop")),
                module { single<AnalyticsStorage> { InMemoryAnalyticsStorage() } },
            ),
            overrides = listOf(testOverridesModule(backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), transport = wire)),
            global = false,
        )
        val analytics = app.koin.get<Analytics>()
        val control = app.koin.get<AnalyticsControl>()
        assertEquals(listOf("stapel"), control.sinks.value.map { it.id })
        analytics.track("checkout.started")
        control.flush()
        assertEquals(0, wire.requests.size, "nothing is sent before consent")
        control.setConsent(Consent(ConsentState.Granted))
        val request = wire.requests.single()
        assertEquals("https://api.test/analytics/api/v1/events", request.url)
        assertNull(request.header("Authorization"))
        app.close()
    }
}
