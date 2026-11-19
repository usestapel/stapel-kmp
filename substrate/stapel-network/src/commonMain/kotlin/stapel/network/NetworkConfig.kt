package stapel.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * When the pipeline re-sends after a transport fault. Engine-level retries are off; this is the
 * only retry layer, and a recovery (refresh, step-up, captcha) never consumes a transport attempt.
 *
 * | Failure | query | mutation |
 * |---|---|---|
 * | request provably not sent (connect refused, DNS) | retry | retry |
 * | timeout / connection drop after send | retry | no |
 * | 502 / 503 / 504 | retry | no |
 * | 429 with a wait ≤ [maxHonouredRetryAfter] | retry after the wait | no |
 * | TLS rejected, any other status | no | no |
 *
 * Mutations are never re-sent once they may have reached the server: the backend has no
 * idempotency key yet.
 */
public data class RetryPolicy(
    /** Total sends for transport faults (1 = never retry). */
    val maxAttempts: Int = 3,
    /** First backoff ceiling; doubles per retry, full jitter. */
    val baseDelay: Duration = 500.milliseconds,
    /** Backoff cap. */
    val maxDelay: Duration = 8.seconds,
    /** A 429 is retried only when the server's wait is at most this. */
    val maxHonouredRetryAfter: Duration = 5.seconds,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
    }

    /** Holder of [None]. */
    public companion object {
        /** Never retry. */
        public val None: RetryPolicy = RetryPolicy(maxAttempts = 1)
    }
}

/** Configuration of the pipeline and its engine. */
public data class NetworkConfig(
    /** Default time limits per exchange. */
    val timeouts: TimeoutPolicy = TimeoutPolicy(),
    /** Transport retry policy. */
    val retry: RetryPolicy = RetryPolicy(),
    /** Certificate pins (none by default). */
    val pins: CertificatePins = CertificatePins.None,
)
