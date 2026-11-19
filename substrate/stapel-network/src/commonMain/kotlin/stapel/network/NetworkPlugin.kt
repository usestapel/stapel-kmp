package stapel.network

import stapel.result.StapelError
import stapel.result.TransportFailure
import stapel.wire.OperationSpec
import kotlin.time.Duration

/**
 * The seam optional capability artifacts (captcha, trace, …) attach through. A plug-in may decorate
 * every attempt and may turn one failure of a logical call into exactly one retry.
 */
public interface NetworkPlugin {
    /** Stable id; a recovery fires at most once per id per logical call. */
    public val id: String

    /** Lower runs first. Built-ins: authentication 100, verification 200; plug-ins from 300. */
    public val order: Int get() = 500

    /** Called before every attempt, after the default and auth headers are set. */
    public suspend fun decorate(context: RequestContext) {}

    /**
     * Called when an attempt failed with [error] (a server refusal or a transport failure). Return
     * [Recovery.Retry] to send once more, [Recovery.Fail] to stop with another error, or
     * [Recovery.Pass] to let the next recovery look at it.
     */
    public suspend fun recover(context: RequestContext, error: StapelError): Recovery = Recovery.Pass

    /** Observes attempts and finished calls (telemetry). Must not block. */
    public fun observe(event: CallEvent) {}
}

/** One logical call as the pipeline sees it; mutations persist across its attempts. */
public interface RequestContext {
    /** The operation being called. */
    public val operation: OperationSpec<*, *, *>

    /** Id of the logical call, sent as `X-Request-ID` on every attempt. */
    public val callId: String

    /** 1-based attempt number. */
    public val attempt: Int

    /** Absolute URL. */
    public val url: String

    /** Current value of header [name], or null. */
    public fun header(name: String): String?

    /** Sets header [name] for this and later attempts. */
    public fun setHeader(name: String, value: String)

    /** Removes header [name]. */
    public fun removeHeader(name: String)

    /**
     * Sets a top-level body field (`captcha_token`): a JSON object body gets the property, a form or
     * multipart body gets a text field. Returns false when the body cannot carry a field (no body,
     * non-object JSON, raw bytes) — then nothing changed.
     */
    public fun setBodyField(name: String, value: String): Boolean
}

/** Decision of a recovery step. */
public sealed interface Recovery {
    /** Not mine; the next recovery decides. */
    public data object Pass : Recovery

    /** Send once more after [mutate] adjusted the request. */
    public class Retry(
        /** Adjusts the request before the retry. */
        public val mutate: suspend (RequestContext) -> Unit = {},
    ) : Recovery

    /** Stop and return [failure]. */
    public class Fail(
        /** The error the call returns. */
        public val failure: TransportFailure,
    ) : Recovery
}

/** Telemetry events of the pipeline. */
public sealed interface CallEvent {
    /** One attempt went out and came back. */
    public data class Attempt(
        /** Operation id. */
        val operationId: String,
        /** Logical call id. */
        val callId: String,
        /** 1-based attempt number. */
        val attempt: Int,
        /** HTTP status, or null when no response arrived. */
        val status: Int?,
        /** Error code of a failed attempt. */
        val errorCode: String?,
        /** Time spent on the wire. */
        val duration: Duration,
    ) : CallEvent

    /** The logical call returned. */
    public data class Finished(
        /** Operation id. */
        val operationId: String,
        /** Logical call id. */
        val callId: String,
        /** Number of attempts sent. */
        val attempts: Int,
        /** Error code when the call failed. */
        val errorCode: String?,
        /** Wall time of the whole call. */
        val duration: Duration,
    ) : CallEvent
}
