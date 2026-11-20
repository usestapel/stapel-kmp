package stapel.analytics.appmetrica.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.appmetrica.appMetricaSink
import stapel.di.contribute

/** Contributes the AppMetrica sink (`analytics.appmetrica`); [apiKey] from the app's secrets, null = unconfigured. */
public fun appMetricaAnalyticsModule(apiKey: String?): Module = module {
    contribute<AnalyticsSink>("analytics.appmetrica") { appMetricaSink(get<stapel.runtime.PlatformContext>(), apiKey) }
}
