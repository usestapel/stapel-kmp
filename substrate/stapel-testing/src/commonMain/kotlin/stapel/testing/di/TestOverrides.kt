package stapel.testing.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.di.StapelQualifiers
import stapel.runtime.AppLifecycle
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.TimeZoneProvider
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
): Module = module {
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
