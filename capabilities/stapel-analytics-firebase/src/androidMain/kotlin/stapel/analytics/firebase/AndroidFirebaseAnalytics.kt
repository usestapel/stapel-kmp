package stapel.analytics.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.runtime.PlatformContext
import stapel.runtime.application

/**
 * The Android Firebase sink over the vendor SDK. Without `google-services.json` (the
 * `com.google.gms.google-services` plugin applied only when the file exists) Firebase has no default
 * app and the sink reports `Unconfigured("google-services.json")`: a working no-op, never a crash.
 */
public fun firebaseAnalyticsSink(platform: PlatformContext): AnalyticsSink =
    firebaseAnalyticsSink(AndroidFirebaseAnalyticsBridge(platform.application), SinkAvailability.Unconfigured(ANDROID_CONFIG_FILE), ANDROID_CONFIG_FILE)

private const val ANDROID_CONFIG_FILE = "google-services.json"

/** Thin calls into `FirebaseAnalytics`; the decisions live in the common sink. */
internal class AndroidFirebaseAnalyticsBridge(private val context: Context) : FirebaseAnalyticsBridge {
    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }
    private var known: Boolean? = null

    override fun configured(): Boolean = known ?: (FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null).also { known = it }

    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        analytics.logEvent(name, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }

    override fun logScreen(name: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, name) })
    }

    override fun setUserId(id: String?) {
        analytics.setUserId(id)
    }

    override fun setUserProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    override fun reset() {
        analytics.resetAnalyticsData()
    }
}
