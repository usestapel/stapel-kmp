package stapel.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * How a read uses the cache. Four modes (ruling R10); the generated default is
 * [StaleWhileRevalidate] of 30 s / 24 h, the web client's query defaults.
 */
public sealed interface ReadPolicy {
    /** Never read, never store: every collection asks the network. */
    public data object NetworkOnly : ReadPolicy

    /**
     * Always ask the network; show `Loading` until it answers. On a failure, an entry younger than
     * [retainFor] is served with the error in `Freshness.refreshError`.
     *
     * @property retainFor how old an entry may be and still stand in for a failed request.
     */
    public data class NetworkFirst(val retainFor: Duration) : ReadPolicy {
        init {
            require(!retainFor.isNegative()) { "retainFor must not be negative" }
        }
    }

    /**
     * An entry younger than [freshFor] is served with no request. Otherwise the network is asked
     * and nothing stale is shown while it loads; on failure an entry younger than [retainFor] is
     * served with the error in `Freshness.refreshError`.
     *
     * @property freshFor age under which no request is made.
     * @property retainFor age under which an entry may stand in for a failed request.
     */
    public data class CacheFirst(val freshFor: Duration, val retainFor: Duration = freshFor) : ReadPolicy {
        init {
            require(!freshFor.isNegative() && retainFor >= freshFor) { "need 0 <= freshFor <= retainFor" }
        }
    }

    /**
     * Any entry younger than [retainFor] is served at once; when it is older than [freshFor] (or
     * invalidated) it is marked stale and revalidated in the background, and the answer re-emitted.
     * `freshFor = ZERO` means "last known, always revalidate".
     *
     * @property freshFor age under which no revalidation is made.
     * @property retainFor age under which an entry is shown at all.
     */
    public data class StaleWhileRevalidate(val freshFor: Duration, val retainFor: Duration) : ReadPolicy {
        init {
            require(!freshFor.isNegative() && retainFor >= freshFor) { "need 0 <= freshFor <= retainFor" }
        }
    }

    /** The defaults. */
    public companion object {
        /** `StaleWhileRevalidate(30.seconds, 24.hours)` — the web client's query defaults. */
        public val Default: ReadPolicy = StaleWhileRevalidate(30.seconds, 24.hours)

        /** `CacheFirst(Duration.INFINITE)`: a stored result that never changes. */
        public val Immutable: ReadPolicy = CacheFirst(Duration.INFINITE)
    }
}

/** Where a value may be kept. */
public enum class Persistence {
    /** Written through to the partition's store (which may itself be memory). */
    Durable,

    /** Kept in process memory only, never written to the store. */
    MemoryOnly,

    /** Never written anywhere and dropped when the last collector leaves (sensitive operations). */
    Never,
}

/**
 * Per-call read options.
 *
 * @property policy overrides the spec's [ReadPolicy] for this call.
 * @property pollEvery refetch on this interval while the flow is collected (a polling fallback
 *   when there is no realtime channel); stops when nothing collects.
 */
public class ReadOptions(
    public val policy: ReadPolicy? = null,
    public val pollEvery: Duration? = null,
) {
    init {
        require(pollEvery == null || pollEvery.isPositive()) { "pollEvery must be positive" }
    }

    /** The defaults. */
    public companion object {
        /** No override, no polling. */
        public val Default: ReadOptions = ReadOptions()
    }
}

/** What an invalidation does to keys that are currently observed. */
public enum class Refetch {
    /** Observed keys refetch at once; others refetch on their next subscription. */
    Active,

    /** Only mark; nothing refetches until the next subscription. */
    None,
}
