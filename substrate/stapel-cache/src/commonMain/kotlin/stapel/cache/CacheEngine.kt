package stapel.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.serialization.KSerializer
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.runtime.NetworkStatus
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import stapel.wire.KeyPattern
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A value held in memory for one key. */
internal class Held(
    val value: Any?,
    val fetchedAt: Long,
    val invalidated: Boolean,
    val origin: Origin,
    val schemaHash: String,
    val persisted: Boolean,
) {
    fun invalidate(): Held = Held(value, fetchedAt, true, origin, schemaHash, persisted)
}

/** Observable state of one key in one partition. */
internal data class SlotState(
    val held: Held? = null,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val refreshError: StapelError? = null,
    val failure: StapelError? = null,
    /** Number of fetches that finished (either way); gates CacheFirst/NetworkFirst observers. */
    val completedFetches: Long = 0,
)

internal class Slot(val key: CacheKey, val storageKey: String) {
    val state = MutableStateFlow(SlotState())
    val loadLock = Mutex()
    var inflight: Job? = null
    var observers = 0
    var fetcher: (suspend () -> StapelResult<Any?, StapelError>)? = null
    var spec: QuerySpec<*>? = null
}

/** One open partition (or the store-less "no account" partition when [stores] is null). */
internal class Runtime(val partition: PartitionId?, val stores: PartitionStores?, parent: CoroutineScope) {
    val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    val lock = Mutex()
    val slots = mutableMapOf<String, Slot>()
    var alive = true

    /** Scope of the keys this partition holds. */
    val cacheScope: CacheScope get() = if (partition == PartitionId.App) CacheScope.App else CacheScope.User

    suspend fun slot(key: CacheKey): Slot {
        val storageKey = key.storageKey
        return lock.withLock { slots.getOrPut(storageKey) { Slot(key, storageKey) } }
    }
}

/** Where a scope's reads go right now. */
internal sealed interface Target {
    /** Account not known yet: user-scoped reads wait. */
    data object Waiting : Target

    data class Live(val runtime: Runtime) : Target
}

internal class CacheEngine(private val config: StapelCacheConfig) : ManagedStapelCache {
    private val json = config.json
    private val factory = config.storeFactory
    private val adminLock = Mutex()
    private val user = MutableStateFlow<Target>(Target.Waiting)
    private val app = MutableStateFlow<Target>(Target.Waiting)
    private var accountId: String? = null
    private val hooks = mutableListOf<WipeHook>()
    private val hooksLock = Mutex()

    override val capabilities: CacheCapabilities = CacheCapabilities(durable = factory.durable, offlineQueue = false)

    init {
        if (!factory.durable) {
            config.observer.onEvent(
                CacheEvent.Degraded("storage is not durable: entries live for the process only, no offline queue"),
            )
        }
    }

    /** Refetches observed keys when the network returns after `Offline`. */
    private val reconnect: Job? = config.connectivity?.let { connectivity ->
        config.scope.launch {
            var offline = false
            connectivity.status.collect { status ->
                when (status) {
                    NetworkStatus.Offline -> offline = true
                    is NetworkStatus.Online -> if (offline) {
                        offline = false
                        refetchObserved(includeFailed = true)
                    }
                    NetworkStatus.Unknown -> Unit
                }
            }
        }
    }

    private fun now(): Long = config.clock.now().toEpochMilliseconds()

    // ---------------------------------------------------------------- partitions

    private fun targetFlow(scope: CacheScope): Flow<Target> = when (scope) {
        CacheScope.User -> user
        CacheScope.App -> flow {
            ensureApp()
            emitAll(app)
        }
    }

    private suspend fun ensureApp(): Runtime = adminLock.withLock {
        (app.value as? Target.Live)?.runtime ?: openRuntime(PartitionId.App).also { app.value = Target.Live(it) }
    }

    private suspend fun openRuntime(partition: PartitionId?): Runtime =
        Runtime(partition, partition?.let { factory.open(it) }, config.scope)

    private suspend fun runtimeFor(scope: CacheScope): Runtime? = when (scope) {
        CacheScope.App -> ensureApp()
        CacheScope.User -> (user.value as? Target.Live)?.runtime
    }

    private fun liveRuntimes(): List<Runtime> =
        listOf(user.value, app.value).mapNotNull { (it as? Target.Live)?.runtime }

    private suspend fun shutDown(runtime: Runtime) {
        runtime.lock.withLock { runtime.alive = false }
        runtime.scope.cancel()
        runtime.stores?.close?.invoke()
    }

    override suspend fun setAccount(accountId: String?): Unit = adminLock.withLock {
        val current = user.value
        if (current is Target.Live && this.accountId == accountId) return@withLock
        if (current is Target.Live) shutDown(current.runtime)
        this.accountId = accountId
        user.value = Target.Live(openRuntime(accountId?.let { PartitionId.account(it) }))
    }

    override suspend fun wipe(target: WipeTarget): WipeReport {
        val count = adminLock.withLock {
            val partitions = when (target) {
                is WipeTarget.Account -> listOf(PartitionId.account(target.accountId))
                WipeTarget.AllAccounts -> (factory.listPartitions() + currentAccountPartition()).filter { it.isAccount }
                is WipeTarget.AllAccountsExcept -> (factory.listPartitions() + currentAccountPartition())
                    .filter { it.isAccount && it != PartitionId.account(target.accountId) }
                WipeTarget.Everything -> factory.listPartitions() + currentAccountPartition() + PartitionId.App
            }.distinct()
            for (partition in partitions) {
                val userRuntime = (user.value as? Target.Live)?.runtime
                if (userRuntime?.partition == partition) {
                    shutDown(userRuntime)
                    user.value = Target.Live(openRuntime(null))
                    accountId = null
                }
                val appRuntime = (app.value as? Target.Live)?.runtime
                if (appRuntime?.partition == partition) {
                    shutDown(appRuntime)
                    app.value = Target.Waiting
                }
            }
            val existing = factory.listPartitions().toSet()
            partitions.forEach { factory.destroy(it) }
            partitions.count { it in existing }
        }
        hooksLock.withLock { hooks.toList() }.forEach { it.onWipe(target) }
        config.observer.onEvent(CacheEvent.Wiped(target, count))
        return WipeReport(count)
    }

    private fun currentAccountPartition(): List<PartitionId> =
        listOfNotNull((user.value as? Target.Live)?.runtime?.partition)

    override fun addWipeHook(hook: WipeHook): AutoCloseable {
        config.scope.launch { hooksLock.withLock { hooks += hook } }
        return AutoCloseable { config.scope.launch { hooksLock.withLock { hooks -= hook } } }
    }

    override fun onForeground() {
        config.scope.launch { refetchObserved(includeFailed = false) }
    }

    private suspend fun refetchObserved(includeFailed: Boolean) {
        for (runtime in liveRuntimes()) {
            val active = runtime.lock.withLock { runtime.slots.values.filter { it.observers > 0 } }
            for (slot in active) {
                val spec = slot.spec ?: continue
                val state = slot.state.value
                val held = state.held
                val due = held == null || isStale(held, policyOf(spec)) ||
                    (includeFailed && (state.refreshError != null || state.failure != null))
                if (due) startFetch(runtime, slot)
            }
        }
    }

    override suspend fun close(): Unit = adminLock.withLock {
        reconnect?.cancel()
        liveRuntimes().forEach { shutDown(it) }
        user.value = Target.Waiting
        app.value = Target.Waiting
    }

    // ---------------------------------------------------------------- reads

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun <T, E : StapelError> observe(
        spec: QuerySpec<T>,
        options: ReadOptions,
        fetch: suspend () -> StapelResult<T, E>,
    ): Flow<Resource<T>> {
        val policy = options.policy ?: spec.policy
        if (policy == ReadPolicy.NetworkOnly) return networkOnly(fetch)
        return targetFlow(spec.key.scope).flatMapLatest { target ->
            when (target) {
                Target.Waiting -> flowOf(Resource.Loading)
                is Target.Live ->
                    if (target.runtime.stores == null && spec.key.scope == CacheScope.User) {
                        config.observer.onEvent(CacheEvent.Miss(spec.key, MissReason.NoPartition))
                        networkOnly(fetch)
                    } else {
                        observeIn(target.runtime, spec, policy, options, fetch)
                    }
            }
        }.distinctUntilChanged()
    }

    private fun <T, E : StapelError> networkOnly(fetch: suspend () -> StapelResult<T, E>): Flow<Resource<T>> = flow {
        emit(Resource.Loading)
        when (val result = fetch()) {
            is StapelResult.Ok -> emit(Resource.Ready(result.value, Freshness.network(now())))
            is StapelResult.Err -> emit(Resource.Failed(result.error))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, E : StapelError> observeIn(
        runtime: Runtime,
        spec: QuerySpec<T>,
        policy: ReadPolicy,
        options: ReadOptions,
        fetch: suspend () -> StapelResult<T, E>,
    ): Flow<Resource<T>> = flow {
        val slot = runtime.slot(spec.key)
        load(runtime, slot, spec)
        val gate: Long?
        runtime.lock.withLock {
            slot.observers++
            slot.spec = spec
            slot.fetcher = fetch as suspend () -> StapelResult<Any?, StapelError>
        }
        try {
            val state = slot.state.value
            val held = state.held?.takeIf { it.schemaHash == spec.schemaHash }
            val usable = held != null && age(held) < retainOf(policy)
            val fresh = usable && !isStale(held, policy)
            val needFetch = policy is ReadPolicy.NetworkFirst || !fresh
            gate = if (needFetch && policy !is ReadPolicy.StaleWhileRevalidate) state.completedFetches + 1 else null
            if (usable) config.observer.onEvent(CacheEvent.Hit(spec.key, stale = !fresh))
            if (needFetch) startFetch(runtime, slot)
            coroutineScope {
                options.pollEvery?.let { every ->
                    launch {
                        while (true) {
                            delay(every)
                            startFetch(runtime, slot)
                        }
                    }
                }
                emitAll(slot.state.map { render(it, spec, policy, gate) }.distinctUntilChanged())
            }
        } finally {
            runtime.lock.withLock {
                slot.observers--
                if (slot.observers == 0 && spec.persistence == Persistence.Never) {
                    slot.state.update { it.copy(held = null) }
                }
            }
        }
    }

    private fun <T> render(state: SlotState, spec: QuerySpec<T>, policy: ReadPolicy, gate: Long?): Resource<T> {
        if (gate != null && state.completedFetches < gate) return Resource.Loading
        val held = state.held?.takeIf { it.schemaHash == spec.schemaHash }
        if (held != null && age(held) < retainOf(policy)) {
            @Suppress("UNCHECKED_CAST")
            return Resource.Ready(
                held.value as T,
                Freshness(
                    origin = held.origin,
                    fetchedAtMillis = held.fetchedAt,
                    stale = isStale(held, policy) || (policy is ReadPolicy.NetworkFirst && state.refreshError != null),
                    refreshing = state.refreshing,
                    refreshError = state.refreshError,
                ),
            )
        }
        return when {
            state.refreshing -> Resource.Loading
            state.failure != null -> Resource.Failed(state.failure)
            state.refreshError != null -> Resource.Failed(state.refreshError)
            else -> Resource.Loading
        }
    }

    private fun age(held: Held): Duration = (now() - held.fetchedAt).coerceAtLeast(0).milliseconds

    private fun retainOf(policy: ReadPolicy): Duration = when (policy) {
        ReadPolicy.NetworkOnly -> Duration.ZERO
        is ReadPolicy.NetworkFirst -> policy.retainFor
        is ReadPolicy.CacheFirst -> policy.retainFor
        is ReadPolicy.StaleWhileRevalidate -> policy.retainFor
    }

    private fun isStale(held: Held, policy: ReadPolicy): Boolean = held.invalidated || when (policy) {
        ReadPolicy.NetworkOnly -> true
        is ReadPolicy.NetworkFirst -> false
        is ReadPolicy.CacheFirst -> age(held) >= policy.freshFor
        is ReadPolicy.StaleWhileRevalidate -> age(held) >= policy.freshFor
    }

    private fun policyOf(spec: QuerySpec<*>): ReadPolicy = spec.policy

    /** Reads the store once per slot: schema hash and decoding are checked; a bad entry is deleted. */
    private suspend fun <T> load(runtime: Runtime, slot: Slot, spec: QuerySpec<T>) {
        slot.loadLock.withLock {
            if (slot.state.value.loaded) return
            val store = runtime.stores?.entries
            var held: Held? = null
            if (store != null && spec.persistence == Persistence.Durable) {
                val entry = runCatchingStore { store.get(slot.storageKey) }
                when {
                    entry == null -> config.observer.onEvent(CacheEvent.Miss(spec.key, MissReason.Absent))
                    entry.schemaHash != spec.schemaHash -> {
                        runCatchingStore { store.delete(slot.storageKey) }
                        config.observer.onEvent(CacheEvent.Miss(spec.key, MissReason.SchemaMismatch))
                    }
                    else -> {
                        val value = decode(spec.serializer, entry.payload)
                        if (value == null) {
                            runCatchingStore { store.delete(slot.storageKey) }
                            config.observer.onEvent(CacheEvent.Miss(spec.key, MissReason.Undecodable))
                        } else {
                            held = Held(value.value, entry.fetchedAtMillis, entry.invalidated, Origin.Disk, spec.schemaHash, true)
                        }
                    }
                }
            }
            slot.state.update { if (it.held == null) it.copy(held = held, loaded = true) else it.copy(loaded = true) }
        }
    }

    private class Decoded<T>(val value: T)

    private fun <T> decode(serializer: KSerializer<T>, payload: ByteArray): Decoded<T>? = try {
        Decoded(json.decodeFromString(serializer, payload.decodeToString()))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun <R> runCatchingStore(block: suspend () -> R): R? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Starts the slot's fetch unless one is in flight (single-flight). */
    private suspend fun startFetch(runtime: Runtime, slot: Slot) {
        runtime.lock.withLock {
            if (!runtime.alive || slot.inflight?.isActive == true) return
            val fetcher = slot.fetcher ?: return
            val spec = slot.spec ?: return
            slot.state.update { it.copy(refreshing = true) }
            slot.inflight = runtime.scope.launch { runFetch(runtime, slot, spec, fetcher) }
        }
    }

    private suspend fun runFetch(
        runtime: Runtime,
        slot: Slot,
        spec: QuerySpec<*>,
        fetcher: suspend () -> StapelResult<Any?, StapelError>,
    ) {
        val started = now()
        val result = try {
            fetcher()
        } catch (e: CancellationException) {
            slot.state.update { it.copy(refreshing = false) }
            throw e
        } catch (e: Exception) {
            StapelResult.Err(TransportFailure.Unexpected(e.message ?: e::class.simpleName ?: "fetch threw"))
        }
        config.observer.onEvent(CacheEvent.Fetch(spec.key, now() - started, (result as? StapelResult.Err)?.error))
        if (!runtime.alive) return
        when (result) {
            is StapelResult.Ok -> {
                @Suppress("UNCHECKED_CAST")
                val persisted = persist(runtime, slot, spec as QuerySpec<Any?>, result.value, now())
                slot.state.update {
                    it.copy(
                        held = Held(result.value, now(), false, Origin.Network, spec.schemaHash, persisted),
                        refreshing = false,
                        refreshError = null,
                        failure = null,
                        completedFetches = it.completedFetches + 1,
                    )
                }
            }
            is StapelResult.Err -> slot.state.update {
                if (it.held != null) {
                    it.copy(refreshing = false, refreshError = result.error, completedFetches = it.completedFetches + 1)
                } else {
                    it.copy(refreshing = false, failure = result.error, completedFetches = it.completedFetches + 1)
                }
            }
        }
    }

    /** Write-through to the partition's store; returns whether the value is stored. */
    private suspend fun <T> persist(runtime: Runtime, slot: Slot, spec: QuerySpec<T>, value: T, at: Long): Boolean {
        val store = runtime.stores?.entries ?: return false
        if (spec.persistence != Persistence.Durable) return false
        val bytes = json.encodeToString(spec.serializer, value).encodeToByteArray()
        if (bytes.size > config.maxEntryBytes) {
            runCatchingStore { store.delete(slot.storageKey) }
            config.observer.onEvent(CacheEvent.NotPersisted(spec.key, NotPersistedReason.TooLarge))
            return false
        }
        val entry = StoredEntry(slot.storageKey, spec.key.namespace, bytes, spec.schemaHash, at, invalidated = false)
        val ok = runtime.lock.withLock { runtime.alive && runCatchingStore { store.put(entry) } != null }
        if (!ok && runtime.alive) config.observer.onEvent(CacheEvent.NotPersisted(spec.key, NotPersistedReason.StoreError))
        return ok
    }

    override suspend fun <T, E : StapelError> get(
        spec: QuerySpec<T>,
        options: ReadOptions,
        fetch: suspend () -> StapelResult<T, E>,
    ): StapelResult<T, StapelError> = observe(spec, options, fetch).firstSettled()

    override suspend fun <T> peek(spec: QuerySpec<T>): Resource.Ready<T>? {
        val runtime = runtimeFor(spec.key.scope) ?: return null
        val slot = runtime.slot(spec.key)
        load(runtime, slot, spec)
        @Suppress("UNCHECKED_CAST")
        return render(slot.state.value.copy(refreshing = false), spec, spec.policy, null) as? Resource.Ready<T>
    }

    override suspend fun <T> write(spec: QuerySpec<T>, value: T) {
        val runtime = runtimeFor(spec.key.scope) ?: return
        val slot = runtime.slot(spec.key)
        val at = now()
        val persisted = persist(runtime, slot, spec, value, at)
        slot.state.update {
            it.copy(
                held = Held(value, at, false, Origin.Network, spec.schemaHash, persisted),
                loaded = true,
                refreshError = null,
                failure = null,
            )
        }
    }

    override suspend fun <T> update(spec: QuerySpec<T>, transform: (T) -> T): Boolean {
        val runtime = runtimeFor(spec.key.scope) ?: return false
        val slot = runtime.slot(spec.key)
        load(runtime, slot, spec)
        val held = slot.state.value.held?.takeIf { it.schemaHash == spec.schemaHash } ?: return false
        @Suppress("UNCHECKED_CAST")
        write(spec, transform(held.value as T))
        return true
    }

    override suspend fun invalidate(pattern: KeyPattern, refetch: Refetch): Int {
        var matched = 0
        for (runtime in liveRuntimes()) {
            val slots = runtime.lock.withLock { runtime.slots.values.filter { pattern.matches(it.key) } }
            matched += runtime.stores?.entries?.let { store ->
                runCatchingStore { storeInvalidate(store, pattern) }
            } ?: 0
            for (slot in slots) {
                var memoryOnly = false
                slot.state.update { state ->
                    val held = state.held ?: return@update state
                    memoryOnly = !held.persisted
                    state.copy(held = held.invalidate())
                }
                if (memoryOnly) matched++
                if (refetch == Refetch.Active && slot.observers > 0) startFetch(runtime, slot)
            }
        }
        config.observer.onEvent(CacheEvent.Invalidated(pattern, matched))
        return matched
    }

    override suspend fun remove(pattern: KeyPattern): Int {
        var removed = 0
        for (runtime in liveRuntimes()) {
            val slots = runtime.lock.withLock { runtime.slots.values.filter { pattern.matches(it.key) } }
            removed += runtime.stores?.entries?.let { store ->
                runCatchingStore { storeDelete(store, pattern) }
            } ?: 0
            for (slot in slots) {
                var memoryOnly = false
                slot.state.update { state ->
                    memoryOnly = state.held?.persisted == false
                    state.copy(held = null)
                }
                if (memoryOnly) removed++
                if (slot.observers > 0) startFetch(runtime, slot)
            }
        }
        return removed
    }

    private suspend fun storeInvalidate(store: CacheStore, pattern: KeyPattern): Int {
        val exact = pattern.exactKey ?: return store.markInvalidated(pattern.storagePrefix)
        val entry = store.get(exact.storageKey) ?: return 0
        store.put(entry.withInvalidated(true))
        return 1
    }

    private suspend fun storeDelete(store: CacheStore, pattern: KeyPattern): Int {
        val exact = pattern.exactKey ?: return store.deletePrefix(pattern.storagePrefix)
        if (store.get(exact.storageKey) == null) return 0
        store.delete(exact.storageKey)
        return 1
    }
}
