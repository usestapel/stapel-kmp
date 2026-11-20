package stapel.cache

/**
 * One stored cache entry as the storage layer sees it: an opaque payload plus the metadata the
 * cache needs to judge it. The storage layer never decodes [payload].
 *
 * @property storageKey the encoded cache key; a key prefix of one entry is a string prefix.
 * @property namespace the first key segment (the brick), kept for statistics and per-brick removal.
 * @property payload serialized value bytes.
 * @property schemaHash hash of the response schema the payload was written with; a reader whose
 *   hash differs treats the entry as a miss and deletes it.
 * @property fetchedAtMillis epoch milliseconds at which the value was obtained from the network.
 * @property invalidated `true` once the entry was invalidated; it is then stale regardless of age.
 */
public class StoredEntry(
    public val storageKey: String,
    public val namespace: String,
    public val payload: ByteArray,
    public val schemaHash: String,
    public val fetchedAtMillis: Long,
    public val invalidated: Boolean,
) {
    /** A copy with [invalidated] replaced. */
    public fun withInvalidated(invalidated: Boolean): StoredEntry =
        StoredEntry(storageKey, namespace, payload, schemaHash, fetchedAtMillis, invalidated)

    /** Field-wise equality, comparing [payload] by content. */
    override fun equals(other: Any?): Boolean =
        other is StoredEntry &&
            storageKey == other.storageKey &&
            namespace == other.namespace &&
            payload.contentEquals(other.payload) &&
            schemaHash == other.schemaHash &&
            fetchedAtMillis == other.fetchedAtMillis &&
            invalidated == other.invalidated

    /** Consistent with [equals]. */
    override fun hashCode(): Int {
        var h = storageKey.hashCode()
        h = 31 * h + namespace.hashCode()
        h = 31 * h + payload.contentHashCode()
        h = 31 * h + schemaHash.hashCode()
        h = 31 * h + fetchedAtMillis.hashCode()
        h = 31 * h + invalidated.hashCode()
        return h
    }

    /** Debug form; shows the payload size, not its bytes. */
    override fun toString(): String =
        "StoredEntry(key=${storageKey.replace(KEY_SEPARATOR, '/')}, namespace=$namespace, " +
            "bytes=${payload.size}, schemaHash=$schemaHash, fetchedAt=$fetchedAtMillis, invalidated=$invalidated)"
}

/**
 * Storage of one partition: get / put / delete over [StoredEntry], nothing else.
 *
 * Observation, single-flight, policy and decoding live in the cache itself; a store is write-through
 * storage, so the memory store and a database store are behaviourally identical and pass one
 * contract suite (`stapel-cache-testing`). Prefix operations match by plain string prefix of
 * [StoredEntry.storageKey]; the empty prefix matches every entry.
 */
public interface CacheStore {
    /** The entry stored under [storageKey], or `null`. */
    public suspend fun get(storageKey: String): StoredEntry?

    /** Insert or replace the entry under its [StoredEntry.storageKey]. */
    public suspend fun put(entry: StoredEntry)

    /** Remove the entry under [storageKey]; no-op when absent. */
    public suspend fun delete(storageKey: String)

    /** Mark every entry whose key starts with [keyPrefix] invalidated; returns how many matched. */
    public suspend fun markInvalidated(keyPrefix: String): Int

    /** Delete every entry whose key starts with [keyPrefix]; returns how many were deleted. */
    public suspend fun deletePrefix(keyPrefix: String): Int

    /** Keys of every entry whose key starts with [keyPrefix], in no particular order. */
    public suspend fun keys(keyPrefix: String): List<String>

    /** Number of stored entries. */
    public suspend fun count(): Int

    /** Delete every entry. */
    public suspend fun clear()
}

/**
 * The open stores of one partition.
 *
 * @property entries the generic document cache of the partition.
 * @property lists the lists (paged windows, revision-synced collections) and cursors of the partition.
 * @property close releases the partition's resources (database connections). Data of a durable
 *   store survives; a non-durable store forgets it.
 */
public class PartitionStores(
    public val entries: CacheStore,
    public val lists: ListStore,
    public val close: suspend () -> Unit,
)

/**
 * Where partitions are stored. The app passes exactly one factory to the cache: the default
 * [MemoryCacheStoreFactory], or a durable one (`RoomCacheStoreFactory` from `stapel-cache-room`)
 * — explicit wiring, never classpath detection.
 */
public interface CacheStoreFactory {
    /** Entries survive process death. */
    public val durable: Boolean

    /** Open (creating when needed) the stores of [partition]. One open handle per partition at a time. */
    public suspend fun open(partition: PartitionId): PartitionStores

    /** Close the partition if open and delete every trace of it (files included); no-op when absent. */
    public suspend fun destroy(partition: PartitionId)

    /** Partitions that currently hold data, open or not. */
    public suspend fun listPartitions(): List<PartitionId>
}

internal const val KEY_SEPARATOR: Char = '\u001F'
