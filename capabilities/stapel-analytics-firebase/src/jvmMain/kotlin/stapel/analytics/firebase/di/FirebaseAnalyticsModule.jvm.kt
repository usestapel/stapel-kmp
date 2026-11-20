package stapel.analytics.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.analytics.firebase.firebaseAnalyticsSink
import stapel.di.contribute

/** Desktop: contributes the Firebase sink as `Unsupported("desktop")` — the app compiles, nothing is sent. */
public fun firebaseAnalyticsModule(): Module = module {
    contribute<AnalyticsSink>("analytics.firebase") { firebaseAnalyticsSink(null, SinkAvailability.Unsupported("desktop"), "none") }
}
