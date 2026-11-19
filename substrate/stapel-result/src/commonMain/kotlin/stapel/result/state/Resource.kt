package stapel.result.state

import stapel.result.StapelError
import stapel.result.StapelResult

/**
 * The state of something being loaded, with the data only behind the discriminant (ruling R4):
 * there is no "loading with stale data" or "failed with data" — held data is always [Ready], and
 * [Freshness] says how fresh it is and whether a revalidation failed.
 */
public sealed interface Resource<out T> {
    /** No answer yet — including "not allowed to start" (session still initializing). */
    public data object Loading : Resource<Nothing>

    /**
     * The data exists only here.
     *
     * @property data the value.
     * @property freshness where it came from and how old it is.
     */
    public data class Ready<out T>(public val data: T, public val freshness: Freshness) : Resource<T>

    /**
     * Nothing to show and the load failed.
     *
     * @property error why it failed.
     */
    public data class Failed(public val error: StapelError) : Resource<Nothing>
}

/** Where a [Resource.Ready] value came from. */
public enum class Origin {
    /** A response received in this process. */
    Network,

    /** The in-memory cache. */
    Memory,

    /** Persistent storage. */
    Disk,
}

/**
 * How fresh a [Resource.Ready] value is.
 *
 * @property origin where the value came from.
 * @property fetchedAtMillis epoch milliseconds when the value was fetched from the network.
 * @property stale older than the read policy's freshness window.
 * @property refreshing a revalidation is in flight.
 * @property refreshError the last revalidation failed with this; the data is the older value.
 * @property pendingWrites optimistic patches currently applied on top of the server value.
 */
public data class Freshness(
    public val origin: Origin,
    public val fetchedAtMillis: Long,
    public val stale: Boolean = false,
    public val refreshing: Boolean = false,
    public val refreshError: StapelError? = null,
    public val pendingWrites: Int = 0,
) {
    /** Holder of [network]. */
    public companion object {
        /** A value just received from the network: fresh, settled, unpatched. */
        public fun network(fetchedAtMillis: Long): Freshness = Freshness(Origin.Network, fetchedAtMillis)
    }
}

/** True when this is [Resource.Loading]. */
public val Resource<*>.isLoading: Boolean get() = this is Resource.Loading

/** The data when [Resource.Ready], else null. */
public fun <T> Resource<T>.dataOrNull(): T? = (this as? Resource.Ready)?.data

/** The error when [Resource.Failed], else null (a failed revalidation is in [Freshness.refreshError]). */
public fun Resource<*>.errorOrNull(): StapelError? = (this as? Resource.Failed)?.error

/** Transforms the data of a [Resource.Ready], keeping its freshness. */
public inline fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Loading -> Resource.Loading
    is Resource.Ready -> Resource.Ready(transform(data), freshness)
    is Resource.Failed -> this
}

/**
 * A result as a resource: [StapelResult.Ok] becomes [Resource.Ready] with [freshness],
 * [StapelResult.Err] becomes [Resource.Failed].
 */
public fun <T> StapelResult<T, *>.toResource(freshness: Freshness): Resource<T> = when (this) {
    is StapelResult.Ok -> Resource.Ready(value, freshness)
    is StapelResult.Err -> Resource.Failed(error)
}

/**
 * Both resources together. A failure of either wins (the first one's); both ready gives a pair
 * whose freshness is the older/worse of the two; anything else is loading.
 */
public fun <A, B> bothReady(a: Resource<A>, b: Resource<B>): Resource<Pair<A, B>> = when {
    a is Resource.Failed -> a
    b is Resource.Failed -> b
    a is Resource.Ready && b is Resource.Ready -> Resource.Ready(a.data to b.data, a.freshness.combine(b.freshness))
    else -> Resource.Loading
}

private fun Freshness.combine(other: Freshness): Freshness {
    val older = if (fetchedAtMillis <= other.fetchedAtMillis) this else other
    return Freshness(
        origin = older.origin,
        fetchedAtMillis = older.fetchedAtMillis,
        stale = stale || other.stale,
        refreshing = refreshing || other.refreshing,
        refreshError = refreshError ?: other.refreshError,
        pendingWrites = pendingWrites + other.pendingWrites,
    )
}

/**
 * Keeps [previous] on the glass while a parameter change reloads: a [Resource.Loading] with a
 * previous ready value becomes that value marked `refreshing`. A failure is never hidden — it is
 * the caller's to render.
 */
public fun <T> Resource<T>.keepPrevious(previous: Resource.Ready<T>?): Resource<T> = when (this) {
    is Resource.Loading ->
        if (previous == null) this else previous.copy(freshness = previous.freshness.copy(refreshing = true))
    else -> this
}

/**
 * Four required arms for a list: "empty" and "failed" can never share a branch, which is the
 * mistake this function exists to prevent.
 */
public inline fun <T, R> Resource<List<T>>.matchList(
    loading: () -> R,
    failed: (StapelError) -> R,
    empty: (Freshness) -> R,
    rows: (List<T>, Freshness) -> R,
): R = when (this) {
    is Resource.Loading -> loading()
    is Resource.Failed -> failed(error)
    is Resource.Ready -> if (data.isEmpty()) empty(freshness) else rows(data, freshness)
}
