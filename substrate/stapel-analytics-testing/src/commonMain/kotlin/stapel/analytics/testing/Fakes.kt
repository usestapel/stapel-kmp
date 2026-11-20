package stapel.analytics.testing

import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.ConsentPurpose
import stapel.analytics.EventKind
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt
import stapel.runtime.Analytics

/** One call a vendor SDK received, as a [VendorProbe] records it. */
public sealed interface VendorCall {
    /**
     * A logged event.
     *
     * @property name the event name after the provider's mapping.
     * @property params the parameters after the provider's mapping.
     */
    public data class Event(public val name: String, public val params: Map<String, String>) : VendorCall

    /**
     * A screen view.
     *
     * @property name the screen name after the provider's mapping.
     */
    public data class Screen(public val name: String) : VendorCall

    /**
     * The vendor's user id was set (null: cleared).
     *
     * @property id the id.
     */
    public data class UserId(public val id: String?) : VendorCall

    /**
     * A user property was set (null: removed).
     *
     * @property name the property name after the provider's mapping.
     * @property value the value.
     */
    public data class UserProperty(public val name: String, public val value: String?) : VendorCall

    /**
     * The vendor's own collection was switched.
     *
     * @property enabled on or off.
     */
    public data class Collection(public val enabled: Boolean) : VendorCall

    /** Vendor-side identifiers were reset. */
    public data object Reset : VendorCall
}

/** What a provider test exposes of the vendor side: the calls it received and a way to make it fail. */
public interface VendorProbe {
    /** Every vendor call so far, oldest first. */
    public val calls: List<VendorCall>

    /** Makes the next vendor call that delivers data throw. */
    public fun failNext()
}

/**
 * A scriptable [AnalyticsSink] for tests that is its own [VendorProbe]: every batch is kept in
 * [batches], every call in [calls]; [receipts] answers the next deliveries (default accepted).
 *
 * @param id the sink id.
 * @property reported what the sink reports as its availability; changeable.
 * @param purposes the consent purposes it needs.
 */
public class FakeAnalyticsSink(
    override val id: String = "fake",
    public var reported: SinkAvailability = SinkAvailability.Ready,
    override val purposes: Set<ConsentPurpose> = setOf(ConsentPurpose.Analytics),
) : AnalyticsSink, VendorProbe {
    private val recorded = mutableListOf<VendorCall>()
    private val delivered = mutableListOf<List<AnalyticsEvent>>()
    private var fail = false

    /** Answers for the next deliveries, consumed in order; empty = accepted. */
    public val receipts: ArrayDeque<(List<AnalyticsEvent>) -> SinkReceipt> = ArrayDeque()

    /** Every batch delivered, oldest first. */
    public val batches: List<List<AnalyticsEvent>> get() = delivered.toList()

    /** Every delivered event, flattened. */
    public val events: List<AnalyticsEvent> get() = delivered.flatten()

    override val calls: List<VendorCall> get() = recorded.toList()

    /** [reported]. */
    override val availability: SinkAvailability get() = reported

    override fun failNext() {
        fail = true
    }

    /** Records [VendorCall.Collection]. */
    override fun setCollectionEnabled(enabled: Boolean) {
        recorded += VendorCall.Collection(enabled)
    }

    /** Records the batch and one vendor call per event; retries while unavailable or after [failNext]; else the next scripted receipt. */
    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        if (reported != SinkAvailability.Ready) return SinkReceipt.Retry("unavailable")
        if (fail) {
            fail = false
            return SinkReceipt.Retry("scripted failure")
        }
        delivered += batch
        batch.forEach { e ->
            when (e.kind) {
                EventKind.Track -> recorded += VendorCall.Event(e.name, e.props)
                EventKind.Page -> recorded += VendorCall.Screen(e.name)
                EventKind.Identify -> recorded += VendorCall.UserId(e.userHash)
            }
        }
        return receipts.removeFirstOrNull()?.invoke(batch) ?: SinkReceipt.Accepted
    }

    /** Records [VendorCall.UserProperty]. */
    override fun setUserProperty(name: String, value: String?) {
        recorded += VendorCall.UserProperty(name, value)
    }

    /** Records [VendorCall.Reset] and a cleared user id. */
    override fun reset() {
        recorded += VendorCall.Reset
        recorded += VendorCall.UserId(null)
    }
}

/**
 * An [Analytics] that records every call, for presenter and flow tests (`flow.<id>.<step>` events
 * included).
 */
public class RecordingAnalytics : Analytics {
    /**
     * One recorded call.
     *
     * @property kind track, page or identify.
     * @property name the event or screen name; the hash for identify (null when cleared).
     * @property props the properties.
     */
    public data class Call(public val kind: EventKind, public val name: String?, public val props: Map<String, String>)

    private val recorded = mutableListOf<Call>()

    /** Every call so far, oldest first. */
    public val calls: List<Call> get() = recorded.toList()

    /** The names of every tracked event, in order. */
    public val tracked: List<String> get() = recorded.filter { it.kind == EventKind.Track }.mapNotNull { it.name }

    /** Records a track call. */
    override fun track(event: String, props: Map<String, String>) {
        recorded += Call(EventKind.Track, event, props)
    }

    /** Records a page call. */
    override fun page(name: String, props: Map<String, String>) {
        recorded += Call(EventKind.Page, name, props)
    }

    /** Records an identify call. */
    override fun identify(userHash: String?) {
        recorded += Call(EventKind.Identify, userHash, emptyMap())
    }
}
