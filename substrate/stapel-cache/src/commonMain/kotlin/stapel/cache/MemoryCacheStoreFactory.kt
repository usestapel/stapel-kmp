package stapel.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The default, non-durable storage: entries live in process memory only.
 *
 * Closing a partition forgets its entries (a switch of account starts empty, as it would after a
 * process restart); nothing survives process death, so `StapelCache` reports itself degraded and
 * never queues writes on top of it.
 */
public class MemoryCacheStoreFactory : CacheStoreFactory {
    private val lock = Mutex()
    private val open = mutableMapOf<PartitionId, MemoryCacheStore>()

    override val durable: Boolean get() = false

    override suspend fun open(partition: PartitionId): PartitionStores = lock.withLock {
        val store = open.getOrPut(partition) { MemoryCacheStore() }
        PartitionStores(entries = store, close = { forget(partition, store) })
    }

    override suspend fun destroy(partition: PartitionId) {
        lock.withLock { open.remove(partition) }?.clear()
    }

    override suspend fun listPartitions(): List<PartitionId> = lock.withLock {
        open.filterValues { it.count() > 0 }.keys.sortedBy { it.name }
    }

    private suspend fun forget(partition: PartitionId, store: MemoryCacheStore) {
        lock.withLock { if (open[partition] === store) open.remove(partition) }
        store.clear()
    }
}

/** In-memory [CacheStore]; a plain map behind a mutex. */
internal class MemoryCacheStore : CacheStore {
    private val lock = Mutex()
    private val entries = mutableMapOf<String, StoredEntry>()

    override suspend fun get(storageKey: String): StoredEntry? = lock.withLock { entries[storageKey] }

    override suspend fun put(entry: StoredEntry) {
        lock.withLock { entries[entry.storageKey] = entry }
    }

    override suspend fun delete(storageKey: String) {
        lock.withLock { entries.remove(storageKey) }
    }

    override suspend fun markInvalidated(keyPrefix: String): Int = lock.withLock {
        val matched = entries.values.filter { it.storageKey.startsWith(keyPrefix) }
        matched.forEach { entries[it.storageKey] = it.withInvalidated(true) }
        matched.size
    }

    override suspend fun deletePrefix(keyPrefix: String): Int = lock.withLock {
        val matched = entries.keys.filter { it.startsWith(keyPrefix) }
        matched.forEach { entries.remove(it) }
        matched.size
    }

    override suspend fun keys(keyPrefix: String): List<String> =
        lock.withLock { entries.keys.filter { it.startsWith(keyPrefix) } }

    override suspend fun count(): Int = lock.withLock { entries.size }

    override suspend fun clear() {
        lock.withLock { entries.clear() }
    }
}
