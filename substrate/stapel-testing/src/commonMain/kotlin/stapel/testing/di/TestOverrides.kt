package stapel.testing.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.di.StapelQualifiers
import stapel.network.HttpTransport
import stapel.runtime.AppLifecycle
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.TimeZoneProvider
import stapel.session.vault.TokenVault
import stapel.testing.FakeAppLifecycle
import stapel.testing.FakeConnectivity
import stapel.testing.FixedLocaleProvider
import stapel.testing.FixedTimeZoneProvider
import stapel.testing.RecordingLogger
import stapel.testing.SequentialIdGenerator
import stapel.testing.TestClock
import kotlin.time.Clock

/**
 * Overrides every binding of `runtimeModule` with deterministic fakes. Pass it in
 * `stapelKoin(overrides = listOf(testOverridesModule(...)))` for graph tests.
 *
 * @param appScope the scope bound under [StapelQualifiers.AppScope] (usually `backgroundScope`).
 * @param transport when given, replaces the platform [HttpTransport] of `networkModule` (pass a
 *   [stapel.testing.FakeTransport] to run the real pipeline against scripted answers).
 * @param vault when given, replaces the platform [TokenVault] of `sessionModule`
 *   (`InMemoryTokenVault`).
 */
public fun testOverridesModule(
    appScope: CoroutineScope,
    dispatchers: StapelDispatchers,
    clock: Clock = TestClock(),
    ids: IdGenerator = SequentialIdGenerator(),
    logger: StapelLogger = RecordingLogger(),
    connectivity: Connectivity = FakeConnectivity(),
    locale: LocaleProvider = FixedLocaleProvider(),
    timeZone: TimeZoneProvider = FixedTimeZoneProvider(),
    lifecycle: AppLifecycle = FakeAppLifecycle(),
    transport: HttpTransport? = null,
    vault: TokenVault? = null,
): Module = module {
    if (transport != null) single<HttpTransport> { transport }
    if (vault != null) single<TokenVault> { vault }
    single<StapelDispatchers> { dispatchers }
    single<Clock> { clock }
    single<IdGenerator> { ids }
    single<StapelLogger> { logger }
    single<CoroutineScope>(StapelQualifiers.AppScope) { appScope }
    single<Connectivity> { connectivity }
    single<LocaleProvider> { locale }
    single<TimeZoneProvider> { timeZone }
    single<AppLifecycle> { lifecycle }
}
