package shop

import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import stapel.analytics.AnalyticsControl
import stapel.analytics.AnalyticsStorage
import stapel.analytics.Consent
import stapel.analytics.ConsentState
import stapel.analytics.InMemoryAnalyticsStorage
import stapel.runtime.Analytics
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The app's analytics wiring: one hub, opt-in, the log sink; consent persisted in the hub's storage. */
class AnalyticsWiringTest {
    @Test
    fun theHubIsBoundOptInWithTheLogSinkAndRecordsTheAnswer() = runBlocking {
        val storage = InMemoryAnalyticsStorage()
        val app = createShopApp(testPlatformContext(), testTarget, "http://127.0.0.1:9/") {
            overrides(module {
                single<TokenVault> { InMemoryTokenVault() }
                single<AnalyticsStorage> { storage }
            })
            global(false)
        }
        try {
            val control = app.koin.get<AnalyticsControl>()
            assertSame<Any>(app.koin.get<Analytics>(), control)
            assertEquals(ConsentState.Pending, control.consent.value.analytics)
            assertEquals(listOf("log"), control.sinks.value.map { it.id })
            control.setConsent(Consent(ConsentState.Granted))
            assertEquals("""{"analytics":"granted","advertising":"denied"}""", storage.snapshot()["consent"])
        } finally {
            app.close()
        }
    }
}
