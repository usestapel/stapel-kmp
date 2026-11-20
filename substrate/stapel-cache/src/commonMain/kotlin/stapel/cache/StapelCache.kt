package stapel.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import stapel.runtime.Connectivity
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.wire.CacheKey
import stapel.wire.KeyPattern
import kotlin.time.Clock

/**
 * The cache every repository reads through. One instance per app, built by [StapelCache] in the
 * composition root; bricks receive it as this interface and never see storage.
 */
public interface StapelCache {
    /** What the configured storage can do. */
    public val capabilities: CacheCapabilities

    /**
     * The value of [spec] as a cold flow of [Resource]s, honouring the read policy. Collecting it is
     * what makes the key "active" (refetched on invalidation, polling, [ManagedStapelCache.onForeground]).
     * Concurrent collectors of one key share one in-flight [fetch] (single-flight).
     *
     * User-scoped keys emit `Loading` until the account is known ([ManagedStapelCache.setAccount]).
     */
    public fun <T, E : StapelError> observe(
        spec: QuerySpec<T>,
        options: ReadOptions = ReadOptions.Default,
        fetch: suspend () -> StapelResult<T, E>,
    ): Flow<Resource<T>>

    /** One settled answer honouring the policy: the first non-refreshing value, or the failure. */
    public suspend fun <T, E : StapelError> get(
        spec: QuerySpec<T>,
        options: ReadOptions = ReadOptions.Default,
        fetch: suspend () -> StapelResult<T, E>,
    ): StapelResult<T, StapelError>

    /** What is held now (memory or store) within the policy's retention, with no request. */
    public suspend fun <T> peek(spec: QuerySpec<T>): Resource.Ready<T>?

    /** Store a server value obtained elsewhere (a mutation response, a realtime frame). */
    public suspend fun <T> write(spec: QuerySpec<T>, value: T)

    /** Transform the held value; `false` when nothing is held (a signal never seeds an entry). */
    public suspend fun <T> update(spec: QuerySpec<T>, transform: (T) -> T): Boolean

    /**
     * Mark every entry matching [pattern] stale (both scopes); observed ones refetch per [refetch].
     * Returns the number of entries matched.
     */
    public suspend fun invalidate(pattern: KeyPattern, refetch: Refetch = Refetch.Active): Int

    /** Delete every entry matching [pattern] from memory and store. Returns the number deleted. */
    public suspend fun remove(pattern: KeyPattern): Int

    /**
     * The revision-synced collection of [spec], whose pages come from [fetch] (see
     * [RevisionCollection]). Stored as a list plus a revision cursor in the key's partition;
     * [invalidate] marks it stale (an observed one syncs a delta), [remove] drops it with its
     * revision (the next sync is a full one).
     */
    public fun <T, E : StapelError> revisioned(
        spec: RevisionSpec<T>,
        fetch: suspend (RevisionRequest) -> StapelResult<RevisionPage<T>, E>,
    ): RevisionCollection<T>
}

/**
 * The composition root's handle: partitions, wipe and lifecycle on top of [StapelCache]. The
 * session binding (`stapel-cache-session`) drives [setAccount] and [wipe].
 */
public interface ManagedStapelCache : StapelCache {
    /**
     * Open the partition of [accountId] (closing the previous account's, which keeps its files), or
     * none for `null` (anonymous: user-scoped reads go to the network and are not kept).
     * Until the first call, user-scoped reads wait in `Loading`.
     */
    public suspend fun setAccount(accountId: String?)

    /** Delete the targeted partitions physically, cancel their in-flight fetches, run wipe hooks. */
    public suspend fun wipe(target: WipeTarget): WipeReport

    /** Register [hook] to run after every wipe (image caches, typed stores). Close the handle to remove. */
    public fun addWipeHook(hook: WipeHook): AutoCloseable

    /** The app came to the foreground: observed keys that are stale refetch. */
    public fun onForeground()

    /** Close every open partition and cancel in-flight work. The instance is unusable afterwards. */
    public suspend fun close()
}

/**
 * What the configured storage can do.
 *
 * @property durable entries survive process death (`false` with the memory store).
 * @property offlineQueue writes can be queued for later delivery. Always `false` until the outbox
 *   ships; a write never claims "will be sent later" without it.
 */
public class CacheCapabilities(
    public val durable: Boolean,
    public val offlineQueue: Boolean,
) {
    /** Debug form. */
    override fun toString(): String = "CacheCapabilities(durable=$durable, offlineQueue=$offlineQueue)"
}

/** Which partitions a wipe deletes. */
public sealed interface WipeTarget {
    /**
     * One account's partition (logout, session revoked or expired).
     *
     * @property accountId the account whose data goes.
     */
    public data class Account(val accountId: String) : WipeTarget

    /** Every account partition; the app partition is kept. */
    public data object AllAccounts : WipeTarget

    /**
     * Every account partition except [accountId]'s (orphans of sessions that ended without a
     * completed wipe, e.g. a process killed during logout).
     *
     * @property accountId the account whose partition stays.
     */
    public data class AllAccountsExcept(val accountId: String) : WipeTarget

    /** Everything, the app partition included (erasure, "reset app"). */
    public data object Everything : WipeTarget
}

/**
 * Result of a wipe.
 *
 * @property partitions how many partitions were deleted.
 */
public class WipeReport(public val partitions: Int) {
    /** Debug form. */
    override fun toString(): String = "WipeReport(partitions=$partitions)"
}

/** Runs after a wipe (e.g. clears an image cache). */
public fun interface WipeHook {
    /** Called once per wipe with its [target]. */
    public suspend fun onWipe(target: WipeTarget)
}

/**
 * Construction of the cache.
 *
 * @property json the shared JSON instance (unknown keys ignored) used to encode stored values.
 * @property scope application scope; in-flight fetches run in children of it.
 * @property clock time source for ages and staleness (injected, never read from the system here).
 * @property storeFactory storage, chosen explicitly: [MemoryCacheStoreFactory] by default, a durable
 *   factory (e.g. `RoomCacheStoreFactory`) when the app opts in. Never discovered from the classpath.
 * @property observer receives [CacheEvent]s (bridge it to logs or analytics).
 * @property maxEntryBytes an encoded value above this size is kept in memory only.
 * @property connectivity when present, coming back online after `Offline` refetches every observed
 *   key that is stale, missing or whose last refresh failed (the same as [ManagedStapelCache.onForeground]
 *   plus the failed ones).
 */
public class StapelCacheConfig(
    public val json: Json,
    public val scope: CoroutineScope,
    public val clock: Clock,
    public val storeFactory: CacheStoreFactory = MemoryCacheStoreFactory(),
    public val observer: CacheObserver = CacheObserver.None,
    public val maxEntryBytes: Long = 1L * 1024 * 1024,
    public val connectivity: Connectivity? = null,
)

/** Builds the app's cache from [config]. Emits [CacheEvent.Degraded] once when storage is not durable. */
public fun StapelCache(config: StapelCacheConfig): ManagedStapelCache = CacheEngine(config)

/**
 * The first settled answer: the first [Resource.Ready] that is not refreshing, or the first
 * [Resource.Failed].
 */
public suspend fun <T> Flow<Resource<T>>.firstSettled(): StapelResult<T, StapelError> {
    val settled = first { it is Resource.Failed || (it is Resource.Ready && !it.freshness.refreshing) }
    return when (settled) {
        is Resource.Ready -> StapelResult.Ok(settled.data)
        is Resource.Failed -> StapelResult.Err(settled.error)
        Resource.Loading -> error("unreachable")
    }
}

/** Receives cache events; the cache has no logging or analytics dependency. */
public fun interface CacheObserver {
    /** Called synchronously on the cache's coroutine; keep it cheap. */
    public fun onEvent(event: CacheEvent)

    /** The no-op observer. */
    public companion object {
        /** Ignores every event. */
        public val None: CacheObserver = CacheObserver { }
    }
}

/** Something the cache did, for metrics and debugging. */
public sealed interface CacheEvent {
    /**
     * A read was answered from held data.
     *
     * @property key the key read.
     * @property stale the data was older than the policy's freshness window.
     */
    public data class Hit(val key: CacheKey, val stale: Boolean) : CacheEvent

    /**
     * A read found nothing usable.
     *
     * @property key the key read.
     * @property reason why.
     */
    public data class Miss(val key: CacheKey, val reason: MissReason) : CacheEvent

    /**
     * A fetch finished (counted once per request, not per collector).
     *
     * @property key the key fetched.
     * @property durationMillis wall time of the fetch.
     * @property error the failure, or `null` on success.
     */
    public data class Fetch(val key: CacheKey, val durationMillis: Long, val error: StapelError?) : CacheEvent

    /**
     * An invalidation ran.
     *
     * @property pattern what was invalidated.
     * @property matched how many entries matched.
     */
    public data class Invalidated(val pattern: KeyPattern, val matched: Int) : CacheEvent

    /**
     * A value the policy wanted stored was not stored.
     *
     * @property key the key.
     * @property reason why.
     */
    public data class NotPersisted(val key: CacheKey, val reason: NotPersistedReason) : CacheEvent

    /**
     * A wipe ran.
     *
     * @property target what was wiped.
     * @property partitions how many partitions were deleted.
     */
    public data class Wiped(val target: WipeTarget, val partitions: Int) : CacheEvent

    /**
     * Emitted once at construction when the storage is not durable.
     *
     * @property reason human-readable explanation.
     */
    public data class Degraded(val reason: String) : CacheEvent
}

/** Why a read missed. */
public enum class MissReason {
    /** Nothing stored under the key. */
    Absent,

    /** Stored under another response-schema hash; the entry was deleted. */
    SchemaMismatch,

    /** Stored bytes did not decode; the entry was deleted. */
    Undecodable,

    /** A user-scoped key with no account partition: read from the network, not kept. */
    NoPartition,
}

/** Why a value was not stored. */
public enum class NotPersistedReason {
    /** The encoded value exceeds `maxEntryBytes`; it is kept in memory. */
    TooLarge,

    /** The store failed the write; the value is kept in memory. */
    StoreError,
}
