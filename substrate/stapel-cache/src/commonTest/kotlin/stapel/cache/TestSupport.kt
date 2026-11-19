package stapel.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Resource
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock that only moves when told to. */
class TestClock(var millis: Long = 1_700_000_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)

    fun advance(by: Duration) {
        millis += by.inWholeMilliseconds
    }
}

/** Counts calls; answers with [next] (a value or an error), optionally held on [gate]. */
class FakeFetch(var next: StapelResult<String, StapelError> = StapelResult.Ok("new")) {
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null

    suspend fun invoke(): StapelResult<String, StapelError> {
        calls++
        gate?.await()
        return next
    }

    fun fail() {
        next = StapelResult.Err(TransportFailure.Offline())
    }
}

class RecordingObserver : CacheObserver {
    val events = mutableListOf<CacheEvent>()
    override fun onEvent(event: CacheEvent) {
        events += event
    }
}

/** A memory factory that claims durability and keeps partitions across close (simulates disk). */
class DurableMemoryFactory : CacheStoreFactory {
    private val inner = MemoryCacheStoreFactory()
    private val handles = mutableMapOf<PartitionId, PartitionStores>()
    private val destroyed = mutableSetOf<CacheStore>()

    /** Writes that reached a store after its partition was destroyed (a late response). */
    var lateWrites: Int = 0
        private set

    override val durable: Boolean get() = true

    override suspend fun open(partition: PartitionId): PartitionStores {
        val stores = handles.getOrPut(partition) {
            val raw = inner.open(partition).entries
            PartitionStores(
                object : CacheStore by raw {
                    override suspend fun put(entry: StoredEntry) {
                        if (raw in destroyed) lateWrites++
                        raw.put(entry)
                    }
                },
                close = {},
            ).also { wrapped -> rawOf[wrapped.entries] = raw }
        }
        return PartitionStores(stores.entries, close = {})
    }

    private val rawOf = mutableMapOf<CacheStore, CacheStore>()

    override suspend fun destroy(partition: PartitionId) {
        handles.remove(partition)?.let { destroyed += rawOf.getValue(it.entries) }
        inner.destroy(partition)
    }

    override suspend fun listPartitions(): List<PartitionId> = inner.listPartitions()
}

val testJson: Json = Json { ignoreUnknownKeys = true }

fun spec(
    vararg path: String,
    policy: ReadPolicy = ReadPolicy.Default,
    scope: CacheScope = CacheScope.User,
    hash: String = "h1",
    persistence: Persistence = Persistence.Durable,
): QuerySpec<String> = QuerySpec(
    key = CacheKey.of(path.first(), *path.drop(1).toTypedArray(), scope = scope),
    serializer = String.serializer(),
    schemaHash = hash,
    policy = policy,
    persistence = persistence,
)

/** `chat/conv` with a `{"page": n}` argument. */
fun pageSpec(n: Int): QuerySpec<String> = QuerySpec(
    key = CacheKey.of("chat", "conv", args = kotlinx.serialization.json.JsonObject(mapOf("page" to kotlinx.serialization.json.JsonPrimitive(n)))),
    serializer = String.serializer(),
    schemaHash = "h1",
)

fun newCache(
    scope: CoroutineScope,
    clock: TestClock,
    factory: CacheStoreFactory = MemoryCacheStoreFactory(),
    observer: CacheObserver = CacheObserver.None,
    maxEntryBytes: Long = 1024 * 1024,
): ManagedStapelCache = StapelCache(
    StapelCacheConfig(
        json = testJson,
        scope = scope,
        clock = clock,
        storeFactory = factory,
        observer = observer,
        maxEntryBytes = maxEntryBytes,
    ),
)

/** Compact form of an emission: L, F, or R:<data>[:flags] with s=stale r=refreshing e=refreshError. */
fun Resource<String>.short(): String = when (this) {
    Resource.Loading -> "L"
    is Resource.Failed -> "F"
    is Resource.Ready -> {
        val flags = buildString {
            if (freshness.stale) append('s')
            if (freshness.refreshing) append('r')
            if (freshness.refreshError != null) append('e')
        }
        if (flags.isEmpty()) "R:$data" else "R:$data:$flags"
    }
}
