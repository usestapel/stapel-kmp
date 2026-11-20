package stapel.analytics.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.analytics.firebase.FirebaseAnalyticsBridge
import stapel.analytics.firebase.firebaseAnalyticsSink
import stapel.di.contribute

/**
 * Contributes the Firebase sink (`analytics.firebase`) over the app shell's Swift [bridge]
 * (`StapelFirebaseAnalytics.shared`). A null bridge reports `Unconfigured("FirebaseAnalyticsBridge")`;
 * a bridge without `GoogleService-Info.plist` reports `Unconfigured("GoogleService-Info.plist")`.
 */
public fun firebaseAnalyticsModule(bridge: FirebaseAnalyticsBridge?): Module = module {
    contribute<AnalyticsSink>("analytics.firebase") {
        firebaseAnalyticsSink(bridge, SinkAvailability.Unconfigured("FirebaseAnalyticsBridge"), "GoogleService-Info.plist")
    }
}
