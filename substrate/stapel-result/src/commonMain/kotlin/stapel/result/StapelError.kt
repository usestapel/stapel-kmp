package stapel.result

import kotlin.time.Duration

/**
 * The one shape every failure has, whichever family it belongs to.
 *
 * Two families implement it:
 * - [DomainError] — a refusal the backend expressed with a code from a brick's error table
 *   (generated per brick, one class per code, plus [ServerRefusal] for code-only handling);
 * - [TransportFailure] — the shared sealed family every operation can fail with: no response,
 *   session gone, step-up unresolved, throttled, server down, contract violated.
 *
 * Generated `<Op>Error` types include the transport family through a [TransportCarrier] variant,
 * so one exhaustive `when` covers both.
 */
public interface StapelError {
    /** The error code; drives text ([stapel.result.text.ErrorTextResolver]) and branching. */
    public val code: ErrorCode

    /** HTTP status of the response, or null when no response was received. */
    public val status: Int?

    /** Envelope params (empty when none). */
    public val params: ErrorParams

    /** What can be done about it. */
    public val remediation: Remediation

    /** The server's answer, or null when no response was received. */
    public val envelope: ErrorEnvelope?
}

/**
 * A refusal the backend expressed with an error code. Generated per-brick error classes implement
 * this; [code], [status] and [params] come from [envelope] unless a class overrides them.
 */
public interface DomainError : StapelError {
    override val envelope: ErrorEnvelope
    override val code: ErrorCode get() = envelope.code
    override val status: Int get() = envelope.status
    override val params: ErrorParams get() = envelope.params
}

/**
 * A domain refusal handled by code only, for callers that have no generated table (hand-written
 * substrate calls) or for a code the table does not know. Its remediation is the backend's
 * heuristic ([Remediation.default]).
 */
public data class ServerRefusal(override val envelope: ErrorEnvelope) : DomainError {
    override val remediation: Remediation
        get() = Remediation.default(envelope.code.value, envelope.status, envelope.params.names)
}

/** Which timeout fired. */
public enum class TimeoutPhase {
    /** Establishing the connection. */
    Connect,

    /** Waiting for bytes on an open connection. */
    Socket,

    /** The whole request exceeded its budget. */
    Request,
}

/**
 * A step-up challenge: a 403 whose body carries a `verification` object.
 *
 * @property challengeId the challenge to resolve.
 * @property scope the verification scope, when stated.
 * @property factors the factors the server accepts.
 * @property raw every field of the `verification` object, flattened to text.
 */
public data class VerificationChallenge(
    public val challengeId: String,
    public val scope: String? = null,
    public val factors: List<String> = emptyList(),
    public val raw: ErrorParams = ErrorParams.Empty,
)

/**
 * The transport-level failure family shared by every operation (ruling R3).
 *
 * The network layer produces these; a generated `<Op>Error` includes them through its
 * [TransportCarrier] variant. Which responses land here rather than in the domain table is fixed
 * by [classifyFailure].
 */
public sealed interface TransportFailure : StapelError {

    /** No HTTP response was received. [code] is always `stapel.transport.failed`. */
    public sealed interface NoResponse : TransportFailure {
        /** A diagnostic for logs; never shown to a person. */
        public val detail: String?
        override val code: ErrorCode get() = CoreErrorCodes.TransportFailed
        override val status: Int? get() = null
        override val params: ErrorParams get() = ErrorParams.Empty
        override val envelope: ErrorEnvelope? get() = null
        override val remediation: Remediation get() = Remediation.Retry
    }

    /** The device is offline; the request was not sent. */
    public data class Offline(override val detail: String? = null) : NoResponse

    /**
     * A timeout fired.
     *
     * @property phase which timeout fired.
     */
    public data class Timeout(public val phase: TimeoutPhase, override val detail: String? = null) : NoResponse

    /** DNS, connect or connection-drop failure. */
    public data class ConnectionFailed(override val detail: String? = null) : NoResponse

    /**
     * TLS failed.
     *
     * @property pinMismatch true when a configured certificate pin rejected the server.
     */
    public data class TlsRejected(
        public val pinMismatch: Boolean,
        override val detail: String? = null,
    ) : NoResponse {
        override val remediation: Remediation
            get() = if (pinMismatch) Remediation.ContactSupport else Remediation.Retry
    }

    /**
     * A 401 that survived the refresh seam, or a call that needs a session made without one
     * ([envelope] null: nothing was sent).
     */
    public data class Unauthenticated(override val envelope: ErrorEnvelope? = null) : TransportFailure {
        override val code: ErrorCode get() = envelope?.code ?: CoreErrorCodes.http(401)
        override val status: Int? get() = envelope?.status
        override val params: ErrorParams get() = envelope?.params ?: ErrorParams.Empty
        override val remediation: Remediation get() = Remediation.Reauthenticate
    }

    /**
     * A step-up challenge nobody resolved.
     *
     * @property challenge the unresolved challenge.
     */
    public data class VerificationRequired(
        public val challenge: VerificationChallenge,
        override val envelope: ErrorEnvelope,
    ) : Answered {
        override val remediation: Remediation get() = Remediation.Verify
    }

    /**
     * 429 (or a throttling code the domain table does not know).
     *
     * @property retryAfter how long the server asked to wait, when it said.
     */
    public data class RateLimited(
        public val retryAfter: Duration?,
        override val envelope: ErrorEnvelope,
    ) : Answered {
        override val remediation: Remediation get() = Remediation.WaitAndRetry
    }

    /** 502/503/504: no verdict from the application. */
    public data class Unavailable(override val envelope: ErrorEnvelope) : Answered {
        override val remediation: Remediation get() = Remediation.Retry
    }

    /** Any other 5xx. */
    public data class ServerFault(override val envelope: ErrorEnvelope) : Answered {
        override val remediation: Remediation get() = Remediation.Retry
    }

    /**
     * Any other status without a domain verdict: a body that was not an error envelope (a proxy's
     * HTML page, an empty body) or a redirect the client refuses to follow.
     */
    public data class HttpStatus(override val envelope: ErrorEnvelope) : Answered {
        override val remediation: Remediation
            get() = Remediation.default(envelope.code.value, envelope.status, envelope.params.names)
    }

    /**
     * A success body that does not match the generated model. `code` = `stapel.contract.decode`.
     *
     * @property operationId the operation whose response failed to decode.
     * @property detail the decoder's message, for logs.
     * @property status the (successful) status of the response.
     */
    public data class ContractViolation(
        public val operationId: String,
        public val detail: String,
        override val status: Int? = null,
    ) : TransportFailure {
        override val code: ErrorCode get() = CoreErrorCodes.ContractDecode
        override val params: ErrorParams get() = ErrorParams.Empty
        override val envelope: ErrorEnvelope? get() = null
        override val remediation: Remediation get() = Remediation.Bug
    }

    /**
     * Anything else (a bug in the client). `code` = `stapel.error.unknown`.
     *
     * @property detail a diagnostic for logs; never shown to a person.
     */
    public data class Unexpected(public val detail: String) : TransportFailure {
        override val code: ErrorCode get() = CoreErrorCodes.Unknown
        override val status: Int? get() = null
        override val params: ErrorParams get() = ErrorParams.Empty
        override val envelope: ErrorEnvelope? get() = null
        override val remediation: Remediation get() = Remediation.Retry
    }

    /** A failure for which the server answered: [envelope] is always present. */
    public sealed interface Answered : TransportFailure {
        override val envelope: ErrorEnvelope
        override val code: ErrorCode get() = envelope.code
        override val status: Int get() = envelope.status
        override val params: ErrorParams get() = envelope.params
    }
}

/**
 * The variant through which a generated `<Op>Error` (or any sealed error type) includes the
 * shared [TransportFailure] family. Every [StapelError] member delegates to [failure].
 *
 * Generated shape: `data class Transport(override val failure: TransportFailure) : AuthApiError,
 * <every op error of the brick>, TransportCarrier`.
 */
public interface TransportCarrier : StapelError {
    /** The carried transport failure. */
    public val failure: TransportFailure
    override val code: ErrorCode get() = failure.code
    override val status: Int? get() = failure.status
    override val params: ErrorParams get() = failure.params
    override val remediation: Remediation get() = failure.remediation
    override val envelope: ErrorEnvelope? get() = failure.envelope
}

/** The transport failure this error is or carries, or null for a domain error. */
public fun StapelError.transportFailureOrNull(): TransportFailure? = when (this) {
    is TransportFailure -> this
    is TransportCarrier -> failure
    else -> null
}

/** True when the error's code is one of [codes]. */
public fun StapelError.hasCode(vararg codes: String): Boolean = codes.any { it == code.value }

/** True when the error's code is [code]. */
public fun StapelError.hasCode(code: ErrorCode): Boolean = this.code == code

/**
 * True when the same request may succeed later without anything changing on the client:
 * no response (except a certificate-pin rejection), throttling, or a server-side fault.
 * Domain refusals, authentication and step-up are permanent until something changes.
 */
public val StapelError.isTransient: Boolean
    get() = when (val failure = transportFailureOrNull()) {
        null -> false
        is TransportFailure.TlsRejected -> !failure.pinMismatch
        is TransportFailure.NoResponse -> true
        is TransportFailure.RateLimited, is TransportFailure.Unavailable, is TransportFailure.ServerFault -> true
        is TransportFailure.HttpStatus -> failure.envelope.status == 408
        is TransportFailure.Unauthenticated, is TransportFailure.VerificationRequired,
        is TransportFailure.ContractViolation, is TransportFailure.Unexpected,
        -> false
    }

/** How long the server asked to wait before retrying, when it said. */
public val StapelError.retryAfter: Duration?
    get() = (transportFailureOrNull() as? TransportFailure.RateLimited)?.retryAfter
        ?: retryAfterFromParams(params)

/**
 * A plain-data copy of an error, for storage (an offline queue keeps the last failure) and logs.
 * It loses the concrete class; [code], [status] and [params] are what text and branching need.
 *
 * @property code the error code text.
 * @property status the HTTP status, null when no response was received.
 * @property params the envelope params.
 * @property remediation the error's remediation.
 * @property transient the error's [isTransient] verdict.
 * @property serverText the envelope's `error` text.
 * @property serverLanguage the language of [serverText].
 * @property requestId the request id.
 */
public data class ErrorSnapshot(
    public val code: String,
    public val status: Int?,
    public val params: Map<String, String>,
    public val remediation: Remediation,
    public val transient: Boolean,
    public val serverText: String? = null,
    public val serverLanguage: String? = null,
    public val requestId: String? = null,
) {
    /** Rebuilds an error with the same code, status, params and remediation. */
    public fun toError(): StapelError = SnapshotError(this)
}

/** A plain-data copy of this error. */
public fun StapelError.snapshot(): ErrorSnapshot = ErrorSnapshot(
    code = code.value,
    status = status,
    params = params.asMap(),
    remediation = remediation,
    transient = isTransient,
    serverText = envelope?.serverText,
    serverLanguage = envelope?.serverLanguage,
    requestId = envelope?.requestId,
)

private data class SnapshotError(val snapshot: ErrorSnapshot) : StapelError {
    override val code: ErrorCode get() = ErrorCode(snapshot.code)
    override val status: Int? get() = snapshot.status
    override val params: ErrorParams get() = ErrorParams(snapshot.params)
    override val remediation: Remediation get() = snapshot.remediation
    override val envelope: ErrorEnvelope?
        get() = snapshot.status?.let {
            ErrorEnvelope(code, it, params, snapshot.serverText, snapshot.serverLanguage, snapshot.requestId)
        }
}
