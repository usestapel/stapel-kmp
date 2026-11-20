package stapel.analytics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A person's answer for one purpose. Stored per installation; survives sign-out; reset by uninstall. */
public enum class ConsentState(
    /** The stored value. */
    public val wire: String,
) {
    /** Not asked yet, or not answered. */
    Pending("pending"),

    /** Agreed. */
    Granted("granted"),

    /** Refused. */
    Denied("denied"),
}

/** What a sink uses the data for; every purpose a sink lists must be granted before it receives anything. */
public enum class ConsentPurpose {
    /** Product analytics: events, screens, funnels. */
    Analytics,

    /** Advertising identifiers and attribution. Not offered by any provider in v0. */
    Advertising,
}

/**
 * What a person agreed to.
 *
 * @property analytics the answer for [ConsentPurpose.Analytics].
 * @property advertising the answer for [ConsentPurpose.Advertising]; denied unless asked for explicitly.
 */
public data class Consent(
    public val analytics: ConsentState,
    public val advertising: ConsentState = ConsentState.Denied,
) {
    /** The answer for [purpose]. */
    public fun stateOf(purpose: ConsentPurpose): ConsentState = when (purpose) {
        ConsentPurpose.Analytics -> analytics
        ConsentPurpose.Advertising -> advertising
    }

    /** True when every purpose in [purposes] is granted. */
    public fun grants(purposes: Set<ConsentPurpose>): Boolean = purposes.all { stateOf(it) == ConsentState.Granted }
}

/** What the hub assumes before a person has chosen. */
public enum class ConsentDefault {
    /** Pending: events are buffered (bounded), nothing reaches a sink, vendor collection is off. */
    OptIn,

    /** Granted until the person objects. Only where the project's legal posture allows it. */
    OptOut,
}

/** Kind of an event, with the value the first-party collector receives. */
public enum class EventKind(
    /** The wire value (`track`, `page`, `identify`). */
    public val wire: String,
) {
    /** A named event. */
    Track("track"),

    /** A screen view. */
    Page("page"),

    /** The (hashed) user became known. */
    Identify("identify"),
}

/**
 * One event as sinks receive it: after the registry check and the PII guard, with the user id
 * already hashed.
 *
 * @property id `<epoch ms>-<sequence>`, unique per installation: the sink's de-duplication key.
 * @property kind track, page or identify.
 * @property name the event name (`flow.auth.login.CodeSent`), the screen's destination id for a page.
 * @property props flat string properties, PII-guarded.
 * @property userHash SHA-256 hex of the account id ([sha256Hex]), or null while signed out.
 * @property anonId the installation's anonymous id.
 * @property sessionId the analytics session; a new one after 30 minutes in the background.
 * @property timestamp when the app emitted the event.
 */
public class AnalyticsEvent(
    public val id: String,
    public val kind: EventKind,
    public val name: String,
    public val props: Map<String, String>,
    public val userHash: String?,
    public val anonId: String,
    public val sessionId: String,
    public val timestamp: Instant,
) {
    /** Compares every field. */
    override fun equals(other: Any?): Boolean = other is AnalyticsEvent && id == other.id && kind == other.kind &&
        name == other.name && props == other.props && userHash == other.userHash && anonId == other.anonId &&
        sessionId == other.sessionId && timestamp == other.timestamp

    /** Hash of [id]. */
    override fun hashCode(): Int = id.hashCode()

    /** Short form without the property values. */
    override fun toString(): String = "AnalyticsEvent($id ${kind.wire} $name ${props.keys})"
}

/** Whether a sink can deliver on this installation. */
public sealed interface SinkAvailability {
    /** Configured and able to deliver. */
    public data object Ready : SinkAvailability

    /**
     * The project chose this sink but its configuration is absent (no `google-services.json`, no
     * `GoogleService-Info.plist`, no API key).
     *
     * @property reason what is missing, e.g. `google-services.json`.
     */
    public data class Unconfigured(public val reason: String) : SinkAvailability

    /**
     * This platform has no implementation (desktop for a vendor SDK).
     *
     * @property reason e.g. `desktop`.
     */
    public data class Unsupported(public val reason: String) : SinkAvailability
}

/** What a sink did with one batch. */
public sealed interface SinkReceipt {
    /** Every event was taken. */
    public data object Accepted : SinkReceipt

    /**
     * Nothing was taken; the hub retries this batch for this sink with backoff.
     *
     * @property detail a short reason for the diagnostics screen (never a payload).
     */
    public data class Retry(public val detail: String?) : SinkReceipt

    /**
     * Taken in part. Rejected events are never re-sent.
     *
     * @property rejected event id → reason.
     */
    public data class Partial(public val rejected: Map<String, String>) : SinkReceipt
}

/**
 * A fan-out target of the hub. Implemented by provider artifacts (`stapel-analytics-firebase`,
 * `stapel-analytics-appmetrica`, `stapel-analytics-collector`) and contributed to Koin with
 * `contribute<AnalyticsSink>("analytics.<id>")`; never called by app or brick code.
 *
 * Every member must be safe to call whatever [availability] says: an unavailable sink answers with
 * no-ops, never an exception.
 */
public interface AnalyticsSink {
    /** Stable id: `firebase`, `appmetrica`, `stapel`, `log`. Unique within an app. */
    public val id: String

    /** Whether this sink can deliver here; read on every delivery. */
    public val availability: SinkAvailability

    /** Every listed purpose must be granted before [deliver] or [setCollectionEnabled] with true. */
    public val purposes: Set<ConsentPurpose>

    /**
     * The vendor's kill switch for what its SDK collects by itself (`first_open`, automatic screen
     * views). The hub calls it with false before anything else, and with true only once consent
     * covers [purposes]. Idempotent.
     */
    public fun setCollectionEnabled(enabled: Boolean)

    /** Delivers one batch in order. A thrown exception counts as [SinkReceipt.Retry]. */
    public suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt

    /** Sets (null: removes) a user property; called only while consent covers [purposes]. */
    public fun setUserProperty(name: String, value: String?)

    /** Sign-out: forget the user id and every vendor-side identifier that can be reset. */
    public fun reset()
}

/**
 * Delivery state of one sink, for a diagnostics screen.
 *
 * @property id the sink's id.
 * @property availability what the sink reported last.
 * @property lastError the last retry reason, null after a successful delivery.
 * @property delivered events this sink has taken since start.
 */
public data class SinkStatus(
    public val id: String,
    public val availability: SinkAvailability,
    public val lastError: String?,
    public val delivered: Int,
)

/** What the PII guard does with a property value that looks like an e-mail address or a phone number. */
public enum class PiiGuard {
    /** Drops the whole event and logs its name. */
    Reject,

    /** Replaces the value with [PII_REDACTED] (the web's default). */
    Strip,

    /** Keeps the value and warns once per event name. */
    Warn,

    /** Does nothing. */
    Off,
}

/** What happens to an event whose name is not in [AnalyticsOptions.registry]. */
public enum class Unregistered {
    /** Delivered; one warning per name. */
    Warn,

    /** Dropped; one warning per name. */
    Drop,
}

/**
 * Batching and retry (the web facade's numbers).
 *
 * @property maxSize events per batch; a full queue flushes at once.
 * @property flushInterval period of the background flush.
 * @property maxAttempts attempts per batch before it is dropped for the sinks that did not take it.
 * @property backoffBase first retry delay; doubles per attempt, capped at 30 s.
 */
public class BatchOptions(
    public val maxSize: Int = 20,
    public val flushInterval: Duration = 10.seconds,
    public val maxAttempts: Int = 5,
    public val backoffBase: Duration = 500.milliseconds,
) {
    init {
        require(maxSize > 0 && maxAttempts > 0) { "maxSize and maxAttempts must be positive" }
    }

    /** Delay before retry number [attempt] (1-based): `backoffBase · 2^(attempt-1)`, at most 30 s. */
    public fun backoff(attempt: Int): Duration {
        var d = backoffBase
        repeat((attempt - 1).coerceIn(0, 16)) { d *= 2 }
        return if (d > 30.seconds) 30.seconds else d
    }
}

/**
 * Options of the hub.
 *
 * @property consentDefault what holds before a person has chosen; opt-in unless the project's
 *   posture says otherwise.
 * @property piiGuard what happens to e-mail- or phone-like property values.
 * @property registry known event names (the generated `events.json` registries concatenated);
 *   names starting with `flow.` are always admitted; null disables the check.
 * @property unregistered what happens to a name outside [registry].
 * @property batch batching and retry.
 * @property pendingBufferLimit events kept while not yet delivered (consent pending or sinks
 *   failing); beyond it the oldest are dropped and counted.
 * @property sessionTimeout background time after which the next foreground starts a new session.
 * @property storageNamespace directory (Android, iOS) or folder name (desktop) of the default
 *   file storage.
 */
public class AnalyticsOptions(
    public val consentDefault: ConsentDefault = ConsentDefault.OptIn,
    public val piiGuard: PiiGuard = PiiGuard.Strip,
    public val registry: Set<String>? = null,
    public val unregistered: Unregistered = Unregistered.Warn,
    public val batch: BatchOptions = BatchOptions(),
    public val pendingBufferLimit: Int = 500,
    public val sessionTimeout: Duration = 30.minutes,
    public val storageNamespace: String = "stapel-analytics",
) {
    init {
        require(pendingBufferLimit > 0) { "pendingBufferLimit must be positive" }
    }
}

/**
 * Source of the anonymous id carried by every event. Without a binding the hub keeps its own random
 * id in its storage; an app that wants the auth installation id instead binds
 * `AnalyticsAnonymousId { installationIdentity.deviceId() }`.
 */
public fun interface AnalyticsAnonymousId {
    /** The id; called once per hub. */
    public suspend fun get(): String
}
