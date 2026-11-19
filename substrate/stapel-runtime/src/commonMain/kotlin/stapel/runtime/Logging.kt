package stapel.runtime

import kotlin.time.Duration

/** Severity of a log record. */
public enum class LogLevel {
    /** Wire-level detail (bodies, only under an explicit opt-in). */
    Verbose,

    /** Developer diagnostics. */
    Debug,

    /** Notable events. */
    Info,

    /** Something unexpected that the code handled. */
    Warn,

    /** A failure. */
    Error,
}

/**
 * The logging seam. No third-party logger is a dependency; an app adapts its own.
 *
 * Callers never pass a token, an `Authorization` value, a refresh token, a phone number or an
 * e-mail address — not even at [LogLevel.Verbose].
 */
public interface StapelLogger {
    /** Whether a record at [level] for [tag] would be written; check before building costly text. */
    public fun isEnabled(level: LogLevel, tag: String): Boolean

    /** Writes one record. */
    public fun log(
        level: LogLevel,
        tag: String,
        message: String,
        error: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    )
}

/** Logs at [LogLevel.Debug], building the message only when enabled. */
public inline fun StapelLogger.debug(tag: String, message: () -> String) {
    if (isEnabled(LogLevel.Debug, tag)) log(LogLevel.Debug, tag, message())
}

/** Logs at [LogLevel.Info], building the message only when enabled. */
public inline fun StapelLogger.info(tag: String, message: () -> String) {
    if (isEnabled(LogLevel.Info, tag)) log(LogLevel.Info, tag, message())
}

/** Logs at [LogLevel.Warn], building the message only when enabled. */
public inline fun StapelLogger.warn(tag: String, error: Throwable? = null, message: () -> String) {
    if (isEnabled(LogLevel.Warn, tag)) log(LogLevel.Warn, tag, message(), error)
}

/** Logs at [LogLevel.Error], building the message only when enabled. */
public inline fun StapelLogger.error(tag: String, error: Throwable? = null, message: () -> String) {
    if (isEnabled(LogLevel.Error, tag)) log(LogLevel.Error, tag, message(), error)
}

/** Discards everything. */
public object NoopLogger : StapelLogger {
    override fun isEnabled(level: LogLevel, tag: String): Boolean = false

    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {}
}

/** The platform log (Android Logcat, iOS `NSLog`, desktop standard error), writing [minLevel] and above. */
public expect fun platformLogger(minLevel: LogLevel): StapelLogger

/** Formats one record as a single line; shared by the platform loggers. */
internal fun formatRecord(message: String, error: Throwable?, fields: Map<String, String>): String = buildString {
    append(message)
    if (fields.isNotEmpty()) fields.entries.joinTo(this, prefix = " {", postfix = "}") { "${it.key}=${it.value}" }
    if (error != null) append(" | ").append(error.toString())
}

/**
 * One logical HTTP call, reported once when it settles.
 *
 * @property operationId the operation (`auth_otp_request`).
 * @property method HTTP method.
 * @property status final HTTP status, null when no response was received.
 * @property errorCode the failure's code, null on success.
 * @property duration wall time of the whole call including retries.
 * @property attempts how many times the request was sent.
 */
public data class HttpCallEvent(
    public val operationId: String,
    public val method: String,
    public val status: Int?,
    public val errorCode: String?,
    public val duration: Duration,
    public val attempts: Int,
)

/**
 * A session lifecycle event (`refreshed`, `refresh_unavailable`, `ended`, `rotation_interrupted`).
 *
 * @property name the event name.
 * @property fields extra non-identifying detail (a cause, a generation number).
 */
public data class SessionTelemetryEvent(
    public val name: String,
    public val fields: Map<String, String> = emptyMap(),
)

/** Observability seam for metrics and crash tools. Every method defaults to doing nothing. */
public interface StapelTelemetry {
    /** A logical HTTP call settled. */
    public fun httpCall(event: HttpCallEvent) {}

    /** A session event happened. */
    public fun sessionEvent(event: SessionTelemetryEvent) {}

    /** A breadcrumb for crash reports. */
    public fun breadcrumb(category: String, message: String, fields: Map<String, String> = emptyMap()) {}

    /** Holder of [Noop]. */
    public companion object {
        /** Records nothing. */
        public val Noop: StapelTelemetry = object : StapelTelemetry {}
    }
}
