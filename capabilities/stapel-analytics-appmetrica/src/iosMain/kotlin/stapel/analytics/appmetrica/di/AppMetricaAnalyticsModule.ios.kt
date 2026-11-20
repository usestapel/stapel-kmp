package stapel.analytics.appmetrica.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.analytics.appmetrica.AppMetricaAnalyticsBridge
import stapel.analytics.appmetrica.appMetricaSink
import stapel.di.contribute

/**
 * Contributes the AppMetrica sink (`analytics.appmetrica`) over the app shell's Swift [bridge]
 * (`StapelAppMetricaAnalytics.shared`, which holds the API key). Null reports
 * `Unconfigured("AppMetricaAnalyticsBridge")`.
 */
public fun appMetricaAnalyticsModule(bridge: AppMetricaAnalyticsBridge?): Module = module {
    contribute<AnalyticsSink>("analytics.appmetrica") { appMetricaSink(bridge, SinkAvailability.Unconfigured("AppMetricaAnalyticsBridge")) }
}
