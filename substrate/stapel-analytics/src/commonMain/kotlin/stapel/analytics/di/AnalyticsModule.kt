package stapel.analytics.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module
import stapel.analytics.AnalyticsAnonymousId
import stapel.analytics.AnalyticsControl
import stapel.analytics.AnalyticsHub
import stapel.analytics.AnalyticsOptions
import stapel.analytics.AnalyticsSink
import stapel.analytics.AnalyticsStorage
import stapel.analytics.platformAnalyticsStorage
import stapel.di.StapelQualifiers
import stapel.runtime.Analytics
import stapel.runtime.AppLifecycle

/**
 * Binds [Analytics] (what bricks and presenters emit through) and [AnalyticsControl] (consent,
 * flush, diagnostics) to one [AnalyticsHub] built from every sink contributed with
 * `contribute<AnalyticsSink>("analytics.<id>")`.
 *
 * Optional bindings it reads: [AnalyticsStorage] (default [platformAnalyticsStorage] under
 * [AnalyticsOptions.storageNamespace]), [AnalyticsAnonymousId] (default the hub's own id),
 * [AppLifecycle]. Needs `runtimeModule` (clock, ids, logger, dispatchers, app scope).
 *
 * Without this module nothing is bound and consumers fall back to `Analytics.Noop`.
 */
public fun analyticsModule(options: AnalyticsOptions = AnalyticsOptions()): Module = module {
    single {
        AnalyticsHub(
            sinks = getAll<AnalyticsSink>().sortedBy { it.id },
            storage = getOrNull<AnalyticsStorage>() ?: platformAnalyticsStorage(get(), get(), options.storageNamespace),
            options = options,
            lifecycle = getOrNull<AppLifecycle>(),
            clock = get(),
            ids = get(),
            scope = get<CoroutineScope>(StapelQualifiers.AppScope),
            logger = get(),
            anonymousId = getOrNull<AnalyticsAnonymousId>(),
        )
    } binds arrayOf(Analytics::class, AnalyticsControl::class)
}
