package stapel.cache

/**
 * The head row of a stored list (a paged window or a revision-synced collection): the envelope and
 * cursor facts of the list, without its rows.
 *
 * @property storageKey the encoded cache key of the list.
 * @property namespace the first key segment (the brick).
 * @property envelope serialized envelope-level facts (counts, facets); may be empty.
 * @property nextAnchor where the next page starts (a paged window's tail cursor), or `null`.
 * @property prevAnchor where the previous page starts, or `null`.
 * @property hasNext more rows exist after the window.
 * @property hasPrev more rows exist before the window.
 * @property schemaHash hash of the item/envelope schema the rows were written with; a reader whose
 *   hash differs treats the list as a miss and deletes it.
 * @property fetchedAtMillis epoch milliseconds at which the list was last completed from the network.
 * @property invalidated `true` once invalidated; the list is then stale regardless of age.
 */
public class StoredListHead(
    public val storageKey: String,
    public val namespace: String,
    public val envelope: ByteArray,
    public val nextAnchor: String?,
    public val prevAnchor: String?,
    public val hasNext: Boolean,
    public val hasPrev: Boolean,
    public val schemaHash: String,
    public val fetchedAtMillis: Long,
    public val invalidated: Boolean,
) {
    /** A copy with [invalidated] replaced. */
    public fun withInvalidated(invalidated: Boolean): StoredListHead =
        StoredListHead(storageKey, namespace, envelope, nextAnchor, prevAnchor, hasNext, hasPrev, schemaHash, fetchedAtMillis, invalidated)

    /** Field-wise equality, comparing [envelope] by content. */
    override fun equals(other: Any?): Boolean =
        other is StoredListHead &&
            storageKey == other.storageKey &&
            namespace == other.namespace &&
            envelope.contentEquals(other.envelope) &&
            nextAnchor == other.nextAnchor &&
            prevAnchor == other.prevAnchor &&
            hasNext == other.hasNext &&
            hasPrev == other.hasPrev &&
            schemaHash == other.schemaHash &&
            fetchedAtMillis == other.fetchedAtMillis &&
            invalidated == other.invalidated

    /** Consistent with [equals]. */
    override fun hashCode(): Int {
        var h = storageKey.hashCode()
        h = 31 * h + envelope.contentHashCode()
        h = 31 * h + (nextAnchor?.hashCode() ?: 0)
        h = 31 * h + (prevAnchor?.hashCode() ?: 0)
        h = 31 * h + hasNext.hashCode()
        h = 31 * h + hasPrev.hashCode()
        h = 31 * h + schemaHash.hashCode()
        h = 31 * h + fetchedAtMillis.hashCode()
        h = 31 * h + invalidated.hashCode()
        return h
    }

    /** Debug form; shows the envelope size, not its bytes. */
    override fun toString(): String =
        "StoredListHead(key=${storageKey.replace(KEY_SEPARATOR, '/')}, next=$nextAnchor, prev=$prevAnchor, " +
            "hasNext=$hasNext, hasPrev=$hasPrev, envelopeBytes=${envelope.size}, schemaHash=$schemaHash, " +
            "fetchedAt=$fetchedAtMillis, invalidated=$invalidated)"
}

/**
 * One row of a stored list.
 *
 * @property itemId the row's identity inside its list (unique per list).
 * @property payload serialized row bytes; the store never decodes them.
 */
public class StoredListItem(
    public val itemId: String,
    public val payload: ByteArray,
) {
    /** Equality by id and payload content. */
    override fun equals(other: Any?): Boolean =
        other is StoredListItem && itemId == other.itemId && payload.contentEquals(other.payload)

    /** Consistent with [equals]. */
    override fun hashCode(): Int = 31 * itemId.hashCode() + payload.contentHashCode()

    /** Debug form. */
    override fun toString(): String = "StoredListItem($itemId, bytes=${payload.size})"
}

/**
 * Storage of the lists and durable cursors of one partition: a head row per list key, its rows in
 * window order, and small `Long` cursors (a revision collection's last synced revision, a realtime
 * resume sequence). Lives next to [CacheStore] in [PartitionStores], so a list is wiped with its
 * partition.
 *
 * Every write of one call is atomic: a reader sees the list before or after it, never half of it.
 * Prefix operations match by plain string prefix of the storage key; the empty prefix matches every
 * list. The memory store and every durable store pass one contract suite
 * (`stapel-cache-testing`'s `ListStoreContract`).
 */
public interface ListStore {
    /** The head of the list under [storageKey], or `null`. */
    public suspend fun head(storageKey: String): StoredListHead?

    /** The rows of the list under [storageKey] in window order; empty when there is no list. */
    public suspend fun items(storageKey: String): List<StoredListItem>

    /**
     * Replaces the whole list under [head]'s key: the head and exactly [items], in that order. An id
     * that occurs twice in [items] keeps its first position with the later payload.
     */
    public suspend fun replace(head: StoredListHead, items: List<StoredListItem>)

    /**
     * Applies a change to the list under [head]'s key and replaces its head: each of [upserts] whose
     * id is already present gets the new payload in place; new ids are appended after the last row,
     * in order; every id in [removedIds] is deleted. On a key with no list this creates it.
     */
    public suspend fun merge(head: StoredListHead, upserts: List<StoredListItem>, removedIds: Collection<String>)

    /** Removes the list and the cursor stored exactly under [storageKey]; no-op when absent. */
    public suspend fun delete(storageKey: String)

    /** Marks every list whose key starts with [keyPrefix] invalidated; returns how many matched. */
    public suspend fun markInvalidated(keyPrefix: String): Int

    /**
     * Deletes every list and every cursor whose key starts with [keyPrefix]; returns how many lists
     * were deleted. A cursor goes with its data so a later sync starts from scratch.
     */
    public suspend fun deletePrefix(keyPrefix: String): Int

    /** Keys of every list whose key starts with [keyPrefix], in no particular order. */
    public suspend fun keys(keyPrefix: String): List<String>

    /** The cursor stored under [storageKey], or `null`. */
    public suspend fun readCursor(storageKey: String): Long?

    /** Stores [value] as the cursor under [storageKey], replacing any earlier one. */
    public suspend fun writeCursor(storageKey: String, value: Long)

    /** Deletes every list and cursor. */
    public suspend fun clear()
}
