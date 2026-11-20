package stapel.analytics.appmetrica.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.analytics.appmetrica.appMetricaSink
import stapel.di.contribute

/** Desktop: contributes the AppMetrica sink as `Unsupported("desktop")`; [apiKey] is ignored. */
public fun appMetricaAnalyticsModule(apiKey: String?): Module = module {
    contribute<AnalyticsSink>("analytics.appmetrica") { appMetricaSink(null, SinkAvailability.Unsupported("desktop")) }
}
