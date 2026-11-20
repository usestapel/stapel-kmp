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
    private val open = mutableMapOf<PartitionId, MemoryPartition>()

    override val durable: Boolean get() = false

    override suspend fun open(partition: PartitionId): PartitionStores = lock.withLock {
        val stores = open.getOrPut(partition) { MemoryPartition(MemoryCacheStore(), MemoryListStore()) }
        PartitionStores(entries = stores.entries, lists = stores.lists, close = { forget(partition, stores) })
    }

    override suspend fun destroy(partition: PartitionId) {
        lock.withLock { open.remove(partition) }?.clear()
    }

    override suspend fun listPartitions(): List<PartitionId> = lock.withLock {
        open.filterValues { it.holdsData() }.keys.sortedBy { it.name }
    }

    private suspend fun forget(partition: PartitionId, stores: MemoryPartition) {
        lock.withLock { if (open[partition] === stores) open.remove(partition) }
        stores.clear()
    }
}

private class MemoryPartition(val entries: MemoryCacheStore, val lists: MemoryListStore) {
    suspend fun holdsData(): Boolean = entries.count() > 0 || lists.holdsData()

    suspend fun clear() {
        entries.clear()
        lists.clear()
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

/** In-memory [ListStore]: ordered rows per list key and a cursor map, behind one mutex. */
internal class MemoryListStore : ListStore {
    private class MemoryList(val head: StoredListHead, val items: LinkedHashMap<String, StoredListItem>)

    private val lock = Mutex()
    private val lists = mutableMapOf<String, MemoryList>()
    private val cursors = mutableMapOf<String, Long>()

    suspend fun holdsData(): Boolean = lock.withLock { lists.isNotEmpty() || cursors.isNotEmpty() }

    override suspend fun head(storageKey: String): StoredListHead? = lock.withLock { lists[storageKey]?.head }

    override suspend fun items(storageKey: String): List<StoredListItem> =
        lock.withLock { lists[storageKey]?.items?.values?.toList().orEmpty() }

    override suspend fun replace(head: StoredListHead, items: List<StoredListItem>) {
        val rows = LinkedHashMap<String, StoredListItem>()
        items.forEach { rows[it.itemId] = it }
        lock.withLock { lists[head.storageKey] = MemoryList(head, rows) }
    }

    override suspend fun merge(head: StoredListHead, upserts: List<StoredListItem>, removedIds: Collection<String>) {
        lock.withLock {
            val rows = LinkedHashMap(lists[head.storageKey]?.items.orEmpty())
            upserts.forEach { rows[it.itemId] = it }
            removedIds.forEach { rows.remove(it) }
            lists[head.storageKey] = MemoryList(head, rows)
        }
    }

    override suspend fun delete(storageKey: String) {
        lock.withLock {
            lists.remove(storageKey)
            cursors.remove(storageKey)
        }
    }

    override suspend fun markInvalidated(keyPrefix: String): Int = lock.withLock {
        val matched = lists.filterKeys { it.startsWith(keyPrefix) }
        matched.forEach { (key, list) -> lists[key] = MemoryList(list.head.withInvalidated(true), list.items) }
        matched.size
    }

    override suspend fun deletePrefix(keyPrefix: String): Int = lock.withLock {
        val matched = lists.keys.filter { it.startsWith(keyPrefix) }
        matched.forEach { lists.remove(it) }
        cursors.keys.filter { it.startsWith(keyPrefix) }.forEach { cursors.remove(it) }
        matched.size
    }

    override suspend fun keys(keyPrefix: String): List<String> = lock.withLock { lists.keys.filter { it.startsWith(keyPrefix) } }

    override suspend fun readCursor(storageKey: String): Long? = lock.withLock { cursors[storageKey] }

    override suspend fun writeCursor(storageKey: String, value: Long) {
        lock.withLock { cursors[storageKey] = value }
    }

    override suspend fun clear() {
        lock.withLock {
            lists.clear()
            cursors.clear()
        }
    }
}
