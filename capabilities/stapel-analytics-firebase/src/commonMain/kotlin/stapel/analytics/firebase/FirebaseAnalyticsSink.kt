package stapel.analytics.firebase

import kotlinx.coroutines.CancellationException
import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.ConsentPurpose
import stapel.analytics.EventKind
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt

/**
 * The vendor half of the Firebase sink: thin calls into the Firebase Analytics SDK, no decisions.
 * Android implements it inside this artifact over `FirebaseAnalytics`; on iOS the app shell
 * implements it in Swift (`ios-bridges/firebase/StapelFirebaseAnalytics.swift`, emitted by the
 * scaffold) and hands it to `firebaseAnalyticsModule(bridge)`. Only `String`, `Boolean` and
 * `Map<String, String>` cross it, so it exports to Objective-C as a plain protocol. Every name and
 * value reaching it is already within Firebase's limits.
 */
public interface FirebaseAnalyticsBridge {
    /** False when the Firebase config file is absent or unreadable; nothing else is called then. */
    public fun configured(): Boolean

    /** `setAnalyticsCollectionEnabled`. */
    public fun setCollectionEnabled(enabled: Boolean)

    /** `logEvent(name, params)` with string parameters. */
    public fun logEvent(name: String, params: Map<String, String>)

    /** A `screen_view` event with `screen_name` = [name]. */
    public fun logScreen(name: String)

    /** `setUserId`; null clears it. */
    public fun setUserId(id: String?)

    /** `setUserProperty`; null clears it. */
    public fun setUserProperty(name: String, value: String?)

    /** `resetAnalyticsData`: clears the user id, user properties and the app instance id. */
    public fun reset()
}

/**
 * Version of [FirebaseAnalyticsBridge]'s shape. The scaffold records it next to the Swift bridge it
 * emitted, so a library release that changes the protocol fails with "re-run the scaffold" before
 * Xcode reports a missing member.
 */
public const val FIREBASE_ANALYTICS_BRIDGE_ABI: Int = 1

/**
 * The Firebase [AnalyticsSink] (`id = "firebase"`) over [bridge].
 *
 * @param bridge the vendor half, or null where there is none (desktop, or an iOS shell without the
 *   Swift bridge).
 * @param whenMissing what [AnalyticsSink.availability] reports when [bridge] is null.
 * @param configFile the config file named by `Unconfigured` when the bridge says it is not configured
 *   (`google-services.json`, `GoogleService-Info.plist`).
 */
public fun firebaseAnalyticsSink(
    bridge: FirebaseAnalyticsBridge?,
    whenMissing: SinkAvailability,
    configFile: String,
): AnalyticsSink = FirebaseAnalyticsSink(bridge, whenMissing, configFile)

internal class FirebaseAnalyticsSink(
    private val bridge: FirebaseAnalyticsBridge?,
    private val whenMissing: SinkAvailability,
    private val configFile: String,
) : AnalyticsSink {
    private var userId: String? = null

    override val id: String get() = "firebase"

    override val availability: SinkAvailability
        get() {
            val b = bridge ?: return whenMissing
            return if (configured(b)) SinkAvailability.Ready else SinkAvailability.Unconfigured(configFile)
        }

    override val purposes: Set<ConsentPurpose> get() = setOf(ConsentPurpose.Analytics)

    override fun setCollectionEnabled(enabled: Boolean) {
        ready()?.setCollectionEnabled(enabled)
    }

    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        val b = ready() ?: return SinkReceipt.Retry("unavailable")
        val rejected = LinkedHashMap<String, String>()
        try {
            for (e in batch) {
                if (e.userHash != userId) {
                    b.setUserId(e.userHash)
                    userId = e.userHash
                }
                when (e.kind) {
                    EventKind.Identify -> Unit
                    EventKind.Page -> b.logScreen(FirebaseNames.value(e.name))
                    EventKind.Track -> {
                        val name = FirebaseNames.event(e.name)
                        if (name == null) rejected[e.id] = "firebase: event name has no usable character" else b.logEvent(name, FirebaseNames.params(e.props))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SinkReceipt.Retry("firebase: ${e::class.simpleName}")
        }
        return if (rejected.isEmpty()) SinkReceipt.Accepted else SinkReceipt.Partial(rejected)
    }

    override fun setUserProperty(name: String, value: String?) {
        val b = ready() ?: return
        val key = FirebaseNames.userProperty(name) ?: return
        b.setUserProperty(key, value?.take(FirebaseNames.USER_PROPERTY_VALUE))
    }

    override fun reset() {
        val b = ready() ?: return
        b.reset()
        userId = null
    }

    private fun ready(): FirebaseAnalyticsBridge? = bridge?.takeIf { configured(it) }

    private fun configured(b: FirebaseAnalyticsBridge): Boolean = try {
        b.configured()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

/**
 * Firebase's naming rules (Google Analytics collection limits): event and parameter names up to 40
 * characters, user property names up to 24, letters, digits and underscores, starting with a
 * letter, never with `firebase_`, `google_` or `ga_`; at most 25 parameters per event; parameter
 * values up to 100 characters, user property values up to 36. Hub names (`flow.auth.login.CodeSent`)
 * become `flow_auth_login_CodeSent`; a reserved name gets the prefix `x_`.
 */
internal object FirebaseNames {
    const val NAME: Int = 40
    const val USER_PROPERTY_NAME: Int = 24
    const val VALUE: Int = 100
    const val USER_PROPERTY_VALUE: Int = 36
    const val PARAMS: Int = 25

    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    private val RESERVED_EVENTS = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_query", "ad_reward", "adunit_exposure", "app_background",
        "app_clear_data", "app_exception", "app_remove", "app_store_refund", "app_store_subscription_cancel",
        "app_store_subscription_convert", "app_store_subscription_renew", "app_update", "app_upgrade",
        "dynamic_link_app_open", "dynamic_link_app_update", "dynamic_link_first_open", "error", "first_open",
        "first_visit", "in_app_purchase", "notification_dismiss", "notification_foreground", "notification_open",
        "notification_receive", "os_update", "screen_view", "session_start", "session_start_with_rollout",
        "user_engagement",
    )

    fun event(name: String): String? = identifier(name, NAME)?.let { if (it in RESERVED_EVENTS) "x_$it".take(NAME) else it }

    fun userProperty(name: String): String? = identifier(name, USER_PROPERTY_NAME)

    fun value(text: String): String = text.take(VALUE)

    fun params(props: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in props.entries.sortedBy { it.key }) {
            if (out.size == PARAMS) break
            val key = identifier(k, NAME) ?: continue
            if (key !in out) out[key] = value(v)
        }
        return out
    }

    private fun identifier(raw: String, max: Int): String? {
        val mapped = raw.map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_') it else '_' }.joinToString("")
        if (mapped.none { it != '_' }) return null
        var name = if (mapped.first() in 'a'..'z' || mapped.first() in 'A'..'Z') mapped else "e_$mapped"
        if (RESERVED_PREFIXES.any { name.lowercase().startsWith(it) }) name = "x_$name"
        return name.take(max)
    }
}
