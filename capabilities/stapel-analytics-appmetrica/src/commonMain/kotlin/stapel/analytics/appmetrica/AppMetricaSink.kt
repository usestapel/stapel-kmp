package stapel.analytics.appmetrica

import kotlinx.coroutines.CancellationException
import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.ConsentPurpose
import stapel.analytics.EventKind
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt

/**
 * The vendor half of the AppMetrica sink: thin calls into the AppMetrica SDK, no decisions. Android
 * implements it inside this artifact; on iOS the app shell implements it in Swift
 * (`ios-bridges/appmetrica/StapelAppMetricaAnalytics.swift`). Only `String`, `Boolean` and
 * `Map<String, String>` cross it.
 *
 * The SDK is activated with data sending off; [setDataSendingEnabled] is the kill switch the hub
 * turns on after consent.
 */
public interface AppMetricaAnalyticsBridge {
    /** False when no API key was supplied (or activation failed); nothing else is called then. */
    public fun configured(): Boolean

    /** `setDataSendingEnabled`. */
    public fun setDataSendingEnabled(enabled: Boolean)

    /** `reportEvent(name, attributes)`. */
    public fun reportEvent(name: String, params: Map<String, String>)

    /** `userProfileID`; null clears it. */
    public fun setUserProfileId(id: String?)

    /** A custom string profile attribute; null resets it. */
    public fun setProfileAttribute(name: String, value: String?)
}

/** Version of [AppMetricaAnalyticsBridge]'s shape (see `FIREBASE_ANALYTICS_BRIDGE_ABI`). */
public const val APPMETRICA_ANALYTICS_BRIDGE_ABI: Int = 1

/** What a page event becomes in AppMetrica, which has no screen API: this event with [SCREEN_PARAM]. */
public const val APPMETRICA_SCREEN_EVENT: String = "screen_view"

/** The parameter of [APPMETRICA_SCREEN_EVENT] carrying the destination id. */
public const val APPMETRICA_SCREEN_PARAM: String = "screen_name"

/**
 * The AppMetrica [AnalyticsSink] (`id = "appmetrica"`) over [bridge].
 *
 * AppMetrica has no "reset analytics data": [AnalyticsSink.reset] clears the user profile id; the
 * device's AppMetrica id stays (documented gap, not hidden).
 *
 * @param bridge the vendor half, or null where there is none (desktop, an iOS shell without the bridge).
 * @param whenMissing what [AnalyticsSink.availability] reports when [bridge] is null.
 */
public fun appMetricaSink(bridge: AppMetricaAnalyticsBridge?, whenMissing: SinkAvailability): AnalyticsSink = AppMetricaSink(bridge, whenMissing)

internal class AppMetricaSink(private val bridge: AppMetricaAnalyticsBridge?, private val whenMissing: SinkAvailability) : AnalyticsSink {
    private var userId: String? = null

    override val id: String get() = "appmetrica"

    override val availability: SinkAvailability
        get() {
            val b = bridge ?: return whenMissing
            return if (configured(b)) SinkAvailability.Ready else SinkAvailability.Unconfigured("AppMetrica API key")
        }

    override val purposes: Set<ConsentPurpose> get() = setOf(ConsentPurpose.Analytics)

    override fun setCollectionEnabled(enabled: Boolean) {
        ready()?.setDataSendingEnabled(enabled)
    }

    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        val b = ready() ?: return SinkReceipt.Retry("unavailable")
        try {
            for (e in batch) {
                if (e.userHash != userId) {
                    b.setUserProfileId(e.userHash)
                    userId = e.userHash
                }
                when (e.kind) {
                    EventKind.Identify -> Unit
                    EventKind.Page -> b.reportEvent(APPMETRICA_SCREEN_EVENT, mapOf(APPMETRICA_SCREEN_PARAM to e.name))
                    EventKind.Track -> b.reportEvent(e.name, e.props)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SinkReceipt.Retry("appmetrica: ${e::class.simpleName}")
        }
        return SinkReceipt.Accepted
    }

    override fun setUserProperty(name: String, value: String?) {
        ready()?.setProfileAttribute(name, value?.take(PROFILE_VALUE))
    }

    override fun reset() {
        val b = ready() ?: return
        b.setUserProfileId(null)
        userId = null
    }

    private fun ready(): AppMetricaAnalyticsBridge? = bridge?.takeIf { configured(it) }

    private fun configured(b: AppMetricaAnalyticsBridge): Boolean = try {
        b.configured()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private companion object {
        /** AppMetrica's custom string attribute limit. */
        const val PROFILE_VALUE = 200
    }
}
