package stapel.analytics.appmetrica

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import io.appmetrica.analytics.profile.Attribute
import io.appmetrica.analytics.profile.UserProfile
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.runtime.PlatformContext
import stapel.runtime.application

/**
 * The Android AppMetrica sink over the vendor SDK. A null or blank [apiKey] reports
 * `Unconfigured("AppMetrica API key")`: a working no-op. The SDK is activated on first use with data
 * sending, advertising identifiers and location tracking off; the hub turns data sending on after
 * consent.
 */
public fun appMetricaSink(platform: PlatformContext, apiKey: String?): AnalyticsSink =
    appMetricaSink(AndroidAppMetricaBridge(platform.application, apiKey), SinkAvailability.Unconfigured("AppMetrica API key"))

/** Thin calls into `AppMetrica`; the decisions live in the common sink. */
internal class AndroidAppMetricaBridge(private val application: Application, private val apiKey: String?) : AppMetricaAnalyticsBridge {
    private var activated: Boolean? = null

    override fun configured(): Boolean = activated ?: run {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return@run false
        val config = AppMetricaConfig.newConfigBuilder(key)
            .withDataSendingEnabled(false)
            .withAdvIdentifiersTracking(false)
            .withLocationTracking(false)
            .build()
        AppMetrica.activate(application, config)
        AppMetrica.enableActivityAutoTracking(application)
        true
    }.also { activated = it }

    override fun setDataSendingEnabled(enabled: Boolean) {
        AppMetrica.setDataSendingEnabled(enabled)
    }

    override fun reportEvent(name: String, params: Map<String, String>) {
        AppMetrica.reportEvent(name, params)
    }

    override fun setUserProfileId(id: String?) {
        AppMetrica.setUserProfileID(id)
    }

    override fun setProfileAttribute(name: String, value: String?) {
        val attribute = Attribute.customString(name)
        val update = if (value == null) attribute.withValueReset() else attribute.withValue(value)
        AppMetrica.reportUserProfile(UserProfile.newBuilder().apply(update).build())
    }
}
