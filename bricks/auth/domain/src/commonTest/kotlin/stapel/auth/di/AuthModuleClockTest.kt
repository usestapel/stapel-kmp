package stapel.auth.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import stapel.auth.StapelAuth
import stapel.cache.ManagedStapelCache
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.di.StapelQualifiers
import stapel.network.di.NetworkQualifiers
import stapel.session.InstallationIdentity
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.TokenRefresher
import stapel.session.vault.InMemoryTokenVault
import stapel.testing.FakeApiTransport
import stapel.testing.virtualTimeClock
import stapel.wire.ApiTransport
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.time.Clock

/** `authModule()` takes its time source from the graph's runtime [Clock], never from the system. */
class AuthModuleClockTest {
    @Test
    fun theAuthFacadeUsesTheGraphsClock() = runTest {
        val clock = virtualTimeClock()
        val scope: CoroutineScope = backgroundScope
        val substrate = module {
            single<Clock> { clock }
            single<CoroutineScope>(StapelQualifiers.AppScope) { scope }
            single<ApiTransport> { FakeApiTransport() }
            single<ApiTransport>(NetworkQualifiers.Bare) { FakeApiTransport() }
            single<SessionManager> { SessionManager(SessionDependencies(InMemoryTokenVault(), scope, clock), get<TokenRefresher>()) }
            single<ManagedStapelCache> { StapelCache(StapelCacheConfig(json = WireJson, scope = scope, clock = clock)) }
        }
        val identity = module { single<InstallationIdentity> { object : InstallationIdentity { override suspend fun deviceId() = "device" } } }
        val koin = koinApplication {
            allowOverride(true)
            modules(substrate, authModule(bindCacheToSession = false), identity)
        }.koin
        try {
            assertSame(clock, koin.get<StapelAuth>().clock)
        } finally {
            koin.close()
        }
    }
}
