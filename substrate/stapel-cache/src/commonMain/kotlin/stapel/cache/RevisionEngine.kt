package stapel.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.CacheKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** What one partition holds for one revision collection. */
internal data class RevisionState(
    /** The rows by id in collection order, or null before anything is held. */
    val rows: Map<String, Any?>? = null,
    val revision: Long? = null,
    val fetchedAt: Long = 0,
    val origin: Origin = Origin.Network,
    val invalidated: Boolean = false,
    val loaded: Boolean = false,
    val syncing: Boolean = false,
    val refreshError: StapelError? = null,
    val failure: StapelError? = null,
)

/** The per-partition state and sync machinery of one revision collection key. */
internal class RevisionHolder(val key: CacheKey, private val engine: CacheEngine) {
    val state = MutableStateFlow(RevisionState())
    val syncLock = Mutex()
    private val loadLock = Mutex()
    var observers = 0
    var sync: (suspend (Runtime) -> StapelResult<SyncReport, StapelError>)? = null

    fun launchSync(runtime: Runtime) {
        val run = sync ?: return
        if (!runtime.alive) return
        runtime.scope.launch { run(runtime) }
    }

    /** Reads the stored list once: schema hash and decoding are checked; a bad list is deleted with its cursor. */
    suspend fun <T> load(runtime: Runtime, spec: RevisionSpec<T>) {
        loadLock.withLock {
            if (state.value.loaded) return
            val lists = runtime.stores?.lists
            var loaded = RevisionState(loaded = true)
            if (lists != null) {
                val head = engine.storeCall { lists.head(key.storageKey) }
                when {
                    head == null -> engine.config.observer.onEvent(CacheEvent.Miss(key, MissReason.Absent))
                    head.schemaHash != spec.schemaHash -> {
                        engine.storeCall { lists.delete(key.storageKey) }
                        engine.config.observer.onEvent(CacheEvent.Miss(key, MissReason.SchemaMismatch))
                    }
                    else -> {
                        val items = engine.storeCall { lists.items(key.storageKey) }.orEmpty()
                        val rows = LinkedHashMap<String, Any?>()
                        var bad = false
                        for (item in items) {
                            val value = decode(spec, item.payload)
                            if (value == null) {
                                bad = true
                                break
                            }
                            rows[item.itemId] = value.value
                        }
                        if (bad) {
                            engine.storeCall { lists.delete(key.storageKey) }
                            engine.config.observer.onEvent(CacheEvent.Miss(key, MissReason.Undecodable))
                        } else {
                            loaded = RevisionState(
                                rows = rows,
                                revision = engine.storeCall { lists.readCursor(key.storageKey) },
                                fetchedAt = head.fetchedAtMillis,
                                origin = Origin.Disk,
                                invalidated = head.invalidated,
                                loaded = true,
                            )
                        }
                    }
                }
            }
            state.update { if (it.rows == null) loaded.copy(syncing = it.syncing) else it.copy(loaded = true) }
        }
    }

    private class Decoded<T>(val value: T)

    private fun <T> decode(spec: RevisionSpec<T>, payload: ByteArray): Decoded<T>? = try {
        Decoded(engine.json.decodeFromString(spec.itemSerializer, payload.decodeToString()))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

internal class RevisionCollectionImpl<T, E : StapelError>(
    private val engine: CacheEngine,
    private val spec: RevisionSpec<T>,
    private val fetch: suspend (RevisionRequest) -> StapelResult<RevisionPage<T>, E>,
) : RevisionCollection<T> {
    private val key = spec.key

    private suspend fun holder(runtime: Runtime): RevisionHolder = runtime.lock.withLock {
        runtime.revisions.getOrPut(key.storageKey) { RevisionHolder(key, engine) }
    }.also { h ->
        if (h.sync == null) h.sync = { rt -> runSync(rt, h) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<Resource<List<T>>> = engine.targetFlow(key.scope).flatMapLatest { target ->
        when (target) {
            Target.Waiting -> flowOf(Resource.Loading)
            is Target.Live -> observeIn(target.runtime)
        }
    }.distinctUntilChanged()

    private fun observeIn(runtime: Runtime): Flow<Resource<List<T>>> = flow {
        val holder = holder(runtime)
        holder.load(runtime, spec)
        runtime.lock.withLock { holder.observers++ }
        try {
            val current = holder.state.value
            if (current.rows == null || current.invalidated || due(current)) holder.launchSync(runtime)
            emitAll(holder.state.map { render(it) }.distinctUntilChanged())
        } finally {
            runtime.lock.withLock { holder.observers-- }
        }
    }

    override suspend fun sync(): StapelResult<SyncReport, StapelError> {
        val runtime = engine.awaitRuntime(key.scope)
        val holder = holder(runtime)
        holder.load(runtime, spec)
        return runSync(runtime, holder)
    }

    private fun age(state: RevisionState): Duration = (engine.now() - state.fetchedAt).coerceAtLeast(0).milliseconds

    private fun freshFor(): Duration = when (val p = spec.policy) {
        ReadPolicy.NetworkOnly, is ReadPolicy.NetworkFirst -> Duration.ZERO
        is ReadPolicy.CacheFirst -> p.freshFor
        is ReadPolicy.StaleWhileRevalidate -> p.freshFor
    }

    private fun retainFor(): Duration = when (val p = spec.policy) {
        ReadPolicy.NetworkOnly -> Duration.ZERO
        is ReadPolicy.NetworkFirst -> p.retainFor
        is ReadPolicy.CacheFirst -> p.retainFor
        is ReadPolicy.StaleWhileRevalidate -> p.retainFor
    }

    private fun due(state: RevisionState): Boolean = age(state) >= freshFor()

    @Suppress("UNCHECKED_CAST")
    private fun render(state: RevisionState): Resource<List<T>> {
        val rows = state.rows
        if (rows != null && age(state) < retainFor()) {
            return Resource.Ready(
                rows.values.toList() as List<T>,
                Freshness(
                    origin = state.origin,
                    fetchedAtMillis = state.fetchedAt,
                    stale = state.invalidated || due(state),
                    refreshing = state.syncing,
                    refreshError = state.refreshError,
                ),
            )
        }
        return when {
            state.syncing -> Resource.Loading
            state.failure != null -> Resource.Failed(state.failure)
            state.refreshError != null -> Resource.Failed(state.refreshError)
            else -> Resource.Loading
        }
    }

    private suspend fun runSync(runtime: Runtime, holder: RevisionHolder): StapelResult<SyncReport, StapelError> =
        holder.syncLock.withLock {
            holder.state.update { it.copy(syncing = true) }
            try {
                walk(runtime, holder)
            } finally {
                holder.state.update { it.copy(syncing = false) }
            }
        }

    private suspend fun walk(runtime: Runtime, holder: RevisionHolder): StapelResult<SyncReport, StapelError> {
        val before = holder.state.value
        val from = if (before.rows == null) null else before.revision
        val full = from == null
        val upserts = LinkedHashMap<String, T>()
        val deleted = LinkedHashSet<String>()
        var pinnedMax: Long? = null
        var page = 1
        var pages = 0
        var truncated = false
        while (true) {
            if (pages >= spec.maxPages) {
                truncated = true
                break
            }
            val started = engine.now()
            val result: StapelResult<RevisionPage<T>, StapelError> = try {
                fetch(RevisionRequest(from, pinnedMax, page, spec.pageSize))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StapelResult.Err(TransportFailure.Unexpected(e.message ?: e::class.simpleName ?: "fetch threw"))
            }
            pages++
            engine.config.observer.onEvent(CacheEvent.Fetch(key, engine.now() - started, (result as? StapelResult.Err)?.error))
            when (result) {
                is StapelResult.Err -> {
                    holder.state.update {
                        if (it.rows != null) it.copy(refreshError = result.error) else it.copy(failure = result.error)
                    }
                    return result
                }
                is StapelResult.Ok -> {
                    val p = result.value
                    if (pinnedMax == null) pinnedMax = p.globalMax
                    p.results.forEach { row ->
                        val id = spec.itemId(row)
                        if (id !in deleted) upserts[id] = row
                    }
                    p.deletedIds.forEach { id ->
                        deleted += id
                        upserts.remove(id)
                    }
                    if (!p.hasNext) break
                    page++
                }
            }
        }
        if (truncated) {
            val error = TransportFailure.Unexpected("revision sync of $key stopped at the page budget (${spec.maxPages})")
            holder.state.update { if (it.rows != null) it.copy(refreshError = error) else it.copy(failure = error) }
            return StapelResult.Ok(SyncReport(full, pages, truncated = true, revision = from))
        }
        val revision = maxOf(pinnedMax ?: 0L, from ?: 0L)
        val at = engine.now()
        val rows = LinkedHashMap<String, Any?>()
        if (!full) before.rows?.let { rows.putAll(it) }
        upserts.forEach { (id, row) -> rows[id] = row }
        deleted.forEach { rows.remove(it) }
        persist(runtime, full, upserts, deleted, revision, at)
        if (!runtime.alive) return StapelResult.Ok(SyncReport(full, pages, false, revision))
        holder.state.update {
            it.copy(
                rows = rows, revision = revision, fetchedAt = at, origin = Origin.Network,
                invalidated = false, refreshError = null, failure = null,
            )
        }
        return StapelResult.Ok(SyncReport(full, pages, false, revision))
    }

    /** Rows first, then the cursor: a crash in between re-applies the same delta next time (idempotent). */
    private suspend fun persist(
        runtime: Runtime,
        full: Boolean,
        upserts: Map<String, T>,
        deleted: Set<String>,
        revision: Long,
        at: Long,
    ) {
        val lists = runtime.stores?.lists ?: return
        val head = StoredListHead(
            storageKey = key.storageKey, namespace = key.namespace, envelope = ByteArray(0),
            nextAnchor = null, prevAnchor = null, hasNext = false, hasPrev = false,
            schemaHash = spec.schemaHash, fetchedAtMillis = at, invalidated = false,
        )
        val items = upserts.map { (id, row) ->
            StoredListItem(id, engine.json.encodeToString(spec.itemSerializer, row).encodeToByteArray())
        }
        val ok = runtime.lock.withLock {
            runtime.alive && engine.storeCall {
                if (full) lists.replace(head, items) else lists.merge(head, items, deleted)
                lists.writeCursor(key.storageKey, revision)
            } != null
        }
        if (!ok && runtime.alive) engine.config.observer.onEvent(CacheEvent.NotPersisted(key, NotPersistedReason.StoreError))
    }
}
