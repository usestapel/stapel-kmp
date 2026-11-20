package stapel.runtime.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import stapel.di.StapelQualifiers
import stapel.runtime.AppLifecycle
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocalePreference
import stapel.runtime.LocalePreferenceStorage
import stapel.runtime.LocaleProvider
import stapel.runtime.LogLevel
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.StoredLocalePreference
import stapel.runtime.TimeZoneProvider
import stapel.runtime.defaultDispatchers
import stapel.runtime.platformAppLifecycle
import stapel.runtime.platformConnectivity
import stapel.runtime.platformLocalePreferenceStorage
import stapel.runtime.platformLocaleProvider
import stapel.runtime.platformLogger
import stapel.runtime.platformTimeZoneProvider
import stapel.runtime.randomIdGenerator
import stapel.runtime.storedLocaleTag
import stapel.runtime.systemClock
import kotlin.time.Clock

/**
 * Binds the platform seams: [PlatformContext], [StapelDispatchers], [Clock], [IdGenerator],
 * [LocaleProvider] (starting in the [LocalePreference] stored on this device, when there is one),
 * [LocalePreference] over the platform's [LocalePreferenceStorage], [TimeZoneProvider],
 * [AppLifecycle], [Connectivity], [StapelLogger], and the
 * app-lifetime `CoroutineScope` under [StapelQualifiers.AppScope] (a `SupervisorJob` on the default
 * dispatcher, cancelled when the Koin application closes).
 *
 * `Analytics` and `StapelTelemetry` are deliberately not bound: the optional artifacts that
 * implement them bind them, and consumers fall back to `Analytics.Noop` / no telemetry.
 */
public fun runtimeModule(
    platform: PlatformContext,
    logger: StapelLogger = platformLogger(LogLevel.Info),
): Module = module {
    single<PlatformContext> { platform }
    single<StapelDispatchers> { defaultDispatchers() }
    single<Clock> { systemClock() }
    single<IdGenerator> { randomIdGenerator() }
    single<StapelLogger> { logger }
    single<CoroutineScope>(StapelQualifiers.AppScope) {
        CoroutineScope(SupervisorJob() + get<StapelDispatchers>().default)
    } onClose { it?.cancel() }
    single<LocalePreferenceStorage> { platformLocalePreferenceStorage(platform) }
    // The stored signed-out choice applies before anything reads the locale: the first frame is in it.
    single<LocaleProvider> {
        platformLocaleProvider(platform).also { provider -> storedLocaleTag(get())?.let(provider::setAppLocale) }
    }
    single<LocalePreference> { StoredLocalePreference(get(), get()) }
    single<TimeZoneProvider> { platformTimeZoneProvider(platform) }
    single<AppLifecycle> { platformAppLifecycle(platform, get(StapelQualifiers.AppScope)) }
    single<Connectivity> { platformConnectivity(platform, get(StapelQualifiers.AppScope)) }
}
