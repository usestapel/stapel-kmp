package stapel.result

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Decides whether a failed response belongs to the shared [TransportFailure] family or is a domain
 * refusal to be decoded through the operation's error table. One rule for every transport, so
 * hand-written and generated callers agree:
 *
 * 1. 401 → [TransportFailure.Unauthenticated] (the session layer already had its one refresh).
 * 2. 403 carrying a step-up [challenge] → [TransportFailure.VerificationRequired].
 * 3. A server envelope whose code [isDomainCode] accepts → domain (returns null).
 * 4. 429 → [TransportFailure.RateLimited]; 502/503/504 → [TransportFailure.Unavailable];
 *    other 5xx → [TransportFailure.ServerFault].
 * 5. Any other status with a server envelope → domain (returns null; an unknown code decodes to
 *    the table's `Unknown` or to [ServerRefusal]).
 * 6. No envelope → [TransportFailure.HttpStatus] with the synthesized `stapel.http.<status>`.
 *
 * @param envelope the parsed envelope, or [ErrorEnvelope.synthesized] when the body had none.
 * @param challenge the step-up challenge from the body's `verification` object, if any.
 * @param retryAfterHeader the `Retry-After` header, already parsed; used only when the params
 *   carry no wait (the backend copies the wait into `params.retry_after` because header access is
 *   lossy on some stacks).
 * @param isDomainCode whether the operation's table knows the code.
 * @return the transport failure, or null when the response is a domain refusal.
 */
public fun classifyFailure(
    envelope: ErrorEnvelope,
    challenge: VerificationChallenge? = null,
    retryAfterHeader: Duration? = null,
    isDomainCode: (ErrorCode) -> Boolean = { false },
): TransportFailure? {
    val status = envelope.status
    val fromServer = !envelope.isSynthesized
    return when {
        status == 401 -> TransportFailure.Unauthenticated(envelope)
        status == 403 && challenge != null -> TransportFailure.VerificationRequired(challenge, envelope)
        fromServer && isDomainCode(envelope.code) -> null
        status == 429 -> TransportFailure.RateLimited(retryAfterFromParams(envelope.params) ?: retryAfterHeader, envelope)
        status == 502 || status == 503 || status == 504 -> TransportFailure.Unavailable(envelope)
        status in 500..599 -> TransportFailure.ServerFault(envelope)
        fromServer -> null
        else -> TransportFailure.HttpStatus(envelope)
    }
}

/** `params.retry_after` (seconds) first, then `params.retry_after_minutes`; null when neither. */
internal fun retryAfterFromParams(params: ErrorParams): Duration? {
    params.long("retry_after")?.takeIf { it >= 0 }?.let { return it.seconds }
    params.long("retry_after_minutes")?.takeIf { it >= 0 }?.let { return it.minutes }
    return null
}
