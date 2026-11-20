package stapel.analytics.collector.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.collector.CollectorOptions
import stapel.analytics.collector.CollectorSink
import stapel.di.contribute
import stapel.network.di.NetworkQualifiers
import stapel.wire.ApiTransport

/**
 * Contributes the first-party [CollectorSink] (`analytics.stapel`) to the analytics hub, on the bare
 * transport of `networkModule`. Load it next to `analyticsModule(...)`.
 */
public fun analyticsCollectorModule(options: CollectorOptions): Module = module {
    contribute<AnalyticsSink>("analytics.stapel") { CollectorSink(get<ApiTransport>(NetworkQualifiers.Bare), options) }
}
