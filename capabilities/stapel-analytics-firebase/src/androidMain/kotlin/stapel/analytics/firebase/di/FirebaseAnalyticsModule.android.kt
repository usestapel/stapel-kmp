package stapel.analytics.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.firebase.firebaseAnalyticsSink
import stapel.di.contribute

/** Contributes the Firebase sink (`analytics.firebase`) over the vendor SDK; needs `PlatformContext` from `runtimeModule`. */
public fun firebaseAnalyticsModule(): Module = module {
    contribute<AnalyticsSink>("analytics.firebase") { firebaseAnalyticsSink(get<stapel.runtime.PlatformContext>()) }
}
