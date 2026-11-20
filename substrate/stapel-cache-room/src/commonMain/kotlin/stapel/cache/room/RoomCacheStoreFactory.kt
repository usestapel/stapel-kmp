package stapel.cache.room

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import stapel.cache.CacheStore
import stapel.cache.CacheStoreFactory
import stapel.cache.ListStore
import stapel.cache.PartitionId
import stapel.cache.PartitionStores
import stapel.cache.StoredEntry
import stapel.cache.StoredListHead
import stapel.cache.StoredListItem
import kotlin.coroutines.CoroutineContext

/**
 * Options of [RoomCacheStoreFactory].
 *
 * @property ioContext where database and file work runs: the app's IO dispatcher
 *   (`StapelDispatchers.io`; the Koin module passes it). No default: a library never picks a
 *   dispatcher itself (gate G-R1).
 * @property driver creates the SQLite driver of each database; replace it to plug an encrypting or
 *   a system driver. Default: the bundled SQLite build, the same on every platform.
 * @property onDiscarded called when a partition's `cache.db` could not be used as found (unreadable
 *   file, a schema or store format from another version) and was deleted and recreated empty. The
 *   cache is disposable, so this is a metric, not an error.
 */
public class RoomCacheOptions(
    public val ioContext: CoroutineContext,
    public val driver: () -> SQLiteDriver = { BundledSQLiteDriver() },
    public val onDiscarded: (partition: PartitionId, cause: Throwable) -> Unit = { _, _ -> },
)

/**
 * Durable cache storage on Room 3: one `cache.db` per partition under `<directory>/stapel-cache/`.
 *
 * ```
 * <directory>/stapel-cache/app/cache.db           public data, kept on logout
 * <directory>/stapel-cache/u-<16 hex>/cache.db    one directory per account
 * ```
 * The app opts in by passing this factory to the cache (the default is the memory store); nothing
 * is discovered from the classpath. [destroy] closes the partition and deletes its directory with
 * every file in it (`-wal`, `-shm` included). `cache.db` is disposable: a file that cannot be
 * opened, that carries another schema, or another store format is deleted and recreated empty; a
 * version with a migration (1 → 2, the list tables) is migrated and keeps its entries.
 *
 * Platform entry points pick the directory: `RoomCacheStoreFactory(context, options)` on Android
 * (`noBackupFilesDir`), `RoomCacheStoreFactory(options)` on iOS (Application Support, excluded from
 * backup); on the JVM the app passes a directory to this constructor, by default
 * `desktopDataDirectory(appId)`. On the JVM `stapel-cache/` and each partition directory are made
 * owner-only (`rwx------`, also when found wider) on POSIX file systems; the mobile directories are
 * inside the app's sandbox already.
 *
 * @param directory absolute path of the directory that will hold `stapel-cache/`.
 */
public class RoomCacheStoreFactory(
    directory: String,
    private val options: RoomCacheOptions,
) : CacheStoreFactory {
    private val root = Path(directory, ROOT_NAME)
    private val lock = Mutex()
    private val open = mutableMapOf<PartitionId, Handle>()

    /** Always `true`: entries are files on disk. */
    override val durable: Boolean get() = true

    /**
     * Opens `cache.db` of [partition] (creating the directory), recreating it empty when it cannot be
     * used as found. Handles are counted: the database closes with the last [PartitionStores.close].
     */
    override suspend fun open(partition: PartitionId): PartitionStores = lock.withLock {
        val handle = open[partition] ?: openHandle(partition).also { open[partition] = it }
        handle.refs++
        PartitionStores(
            entries = RoomCacheStore(handle.db.entries()),
            lists = RoomListStore(handle.db),
            close = { release(partition, handle) },
        )
    }

    /** Closes the partition's database if open and deletes its directory with every file in it. */
    override suspend fun destroy(partition: PartitionId): Unit = lock.withLock {
        open.remove(partition)?.db?.close()
        withContext(options.ioContext) { deleteRecursively(partitionDir(partition)) }
    }

    /** Partitions whose directory holds a `cache.db`; foreign directories are ignored. */
    override suspend fun listPartitions(): List<PartitionId> = lock.withLock {
        withContext(options.ioContext) {
            if (!SystemFileSystem.exists(root)) return@withContext emptyList()
            SystemFileSystem.list(root)
                .filter { SystemFileSystem.exists(Path(it, DB_NAME)) }
                .mapNotNull { PartitionId.parse(it.name) }
                .sortedBy { it.name }
        }
    }

    /** Directory of [partition]; tests use it to assert on the file system. */
    internal fun partitionDir(partition: PartitionId): Path = Path(root, partition.name)

    private suspend fun release(partition: PartitionId, handle: Handle) = lock.withLock {
        handle.refs--
        if (handle.refs == 0 && open[partition] === handle) {
            open.remove(partition)
            handle.db.close()
        }
    }

    private suspend fun openHandle(partition: PartitionId): Handle = withContext(options.ioContext) {
        val dir = partitionDir(partition)
        createOwnerOnlyDirectories(root)
        createOwnerOnlyDirectories(dir)
        val first = build(dir)
        try {
            verify(first)
            Handle(first)
        } catch (e: CancellationException) {
            first.close()
            throw e
        } catch (e: Exception) {
            first.close()
            deleteDatabaseFiles(dir)
            options.onDiscarded(partition, e)
            val second = build(dir)
            try {
                verify(second)
            } catch (again: Exception) {
                second.close()
                throw again
            }
            Handle(second)
        }
    }

    private fun build(dir: Path): CacheDatabase =
        cacheDatabaseBuilder(Path(dir, DB_NAME).toString())
            .setDriver(options.driver())
            .setQueryCoroutineContext(options.ioContext)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    /** Opens the connection (Room is lazy) and checks the store format. */
    private suspend fun verify(db: CacheDatabase) {
        val dao = db.entries()
        when (val format = dao.meta(FORMAT_KEY)) {
            null -> dao.putMeta(MetaRow(FORMAT_KEY, STORE_FORMAT.toString()))
            STORE_FORMAT.toString() -> Unit
            else -> throw IllegalStateException("cache.db store format $format, expected $STORE_FORMAT")
        }
    }

    private fun deleteDatabaseFiles(dir: Path) {
        DB_FILES.forEach { name ->
            val file = Path(dir, name)
            if (SystemFileSystem.exists(file)) SystemFileSystem.delete(file)
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteRecursively(it) }
        }
        SystemFileSystem.delete(path)
    }

    private class Handle(val db: CacheDatabase) {
        var refs: Int = 0
    }

    internal companion object {
        const val ROOT_NAME: String = "stapel-cache"
        const val DB_NAME: String = "cache.db"
        val DB_FILES: List<String> = listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm", "$DB_NAME-journal")
        const val FORMAT_KEY: String = "format"

        /**
         * Store format number (payload encoding, key encoding); a different number recreates the file.
         * 2: keys are `stapel-wire`'s `CacheKey.storageKey`.
         */
        const val STORE_FORMAT: Int = 2
    }
}

/** [CacheStore] over the `entry` table of one partition's `cache.db`. */
internal class RoomCacheStore(private val dao: EntryDao) : CacheStore {
    override suspend fun get(storageKey: String): StoredEntry? = dao.get(storageKey)?.let {
        StoredEntry(it.key, it.namespace, it.payload, it.schemaHash, it.fetchedAt, it.invalidated)
    }

    override suspend fun put(entry: StoredEntry) {
        dao.put(
            EntryRow(
                key = entry.storageKey,
                namespace = entry.namespace,
                payload = entry.payload,
                schemaHash = entry.schemaHash,
                fetchedAt = entry.fetchedAtMillis,
                size = entry.payload.size.toLong(),
                invalidated = entry.invalidated,
            ),
        )
    }

    override suspend fun delete(storageKey: String): Unit = dao.delete(storageKey)

    override suspend fun markInvalidated(keyPrefix: String): Int = dao.markInvalidated(keyPrefix)

    override suspend fun deletePrefix(keyPrefix: String): Int = dao.deletePrefix(keyPrefix)

    override suspend fun keys(keyPrefix: String): List<String> = dao.keys(keyPrefix)

    override suspend fun count(): Int = dao.count()

    override suspend fun clear(): Unit = dao.clear()
}

/**
 * [ListStore] over the `list_head`, `list_item` and `cursor` tables of one partition's `cache.db`;
 * every call that writes runs in one write transaction.
 */
internal class RoomListStore(private val db: CacheDatabase) : ListStore {
    private val dao = db.lists()

    override suspend fun head(storageKey: String): StoredListHead? = dao.head(storageKey)?.let {
        StoredListHead(
            it.key, it.namespace, it.envelope, it.nextAnchor, it.prevAnchor, it.hasNext, it.hasPrev,
            it.schemaHash, it.fetchedAt, it.invalidated,
        )
    }

    override suspend fun items(storageKey: String): List<StoredListItem> =
        dao.items(storageKey).map { StoredListItem(it.itemId, it.payload) }

    override suspend fun replace(head: StoredListHead, items: List<StoredListItem>) {
        val rows = LinkedHashMap<String, StoredListItem>()
        items.forEach { rows[it.itemId] = it }
        db.withWriteTransaction {
            dao.putHead(head.toRow(rows.values.sumOf { it.payload.size.toLong() }))
            dao.deleteItems(head.storageKey)
            dao.putItems(rows.values.mapIndexed { i, item -> ListItemRow(head.storageKey, item.itemId, i.toLong(), item.payload) })
        }
    }

    override suspend fun merge(head: StoredListHead, upserts: List<StoredListItem>, removedIds: Collection<String>) {
        db.withWriteTransaction {
            dao.putHead(head.toRow(upserts.sumOf { it.payload.size.toLong() }))
            var next = (dao.maxPosition(head.storageKey) ?: -1L) + 1
            for (item in upserts) {
                if (dao.updatePayload(head.storageKey, item.itemId, item.payload) == 0) {
                    dao.putItems(listOf(ListItemRow(head.storageKey, item.itemId, next++, item.payload)))
                }
            }
            removedIds.distinct().chunked(ID_CHUNK).forEach { dao.deleteItemIds(head.storageKey, it) }
        }
    }

    override suspend fun delete(storageKey: String) {
        db.withWriteTransaction {
            dao.deleteItems(storageKey)
            dao.deleteHead(storageKey)
            dao.deleteCursor(storageKey)
        }
    }

    override suspend fun markInvalidated(keyPrefix: String): Int = dao.markInvalidated(keyPrefix)

    override suspend fun deletePrefix(keyPrefix: String): Int = db.withWriteTransaction {
        dao.deleteItemsPrefix(keyPrefix)
        dao.deleteCursorsPrefix(keyPrefix)
        dao.deleteHeadsPrefix(keyPrefix)
    }

    override suspend fun keys(keyPrefix: String): List<String> = dao.keys(keyPrefix)

    override suspend fun readCursor(storageKey: String): Long? = dao.cursor(storageKey)

    override suspend fun writeCursor(storageKey: String, value: Long): Unit = dao.putCursor(CursorRow(storageKey, value))

    override suspend fun clear() {
        db.withWriteTransaction {
            dao.clearItems()
            dao.clearHeads()
            dao.clearCursors()
        }
    }

    private fun StoredListHead.toRow(size: Long) = ListHeadRow(
        key = storageKey, namespace = namespace, envelope = envelope, nextAnchor = nextAnchor, prevAnchor = prevAnchor,
        hasNext = hasNext, hasPrev = hasPrev, schemaHash = schemaHash, fetchedAt = fetchedAtMillis, size = size,
        invalidated = invalidated,
    )

    private companion object {
        /** Bound variables per `IN (…)` statement, well under SQLite's limit. */
        const val ID_CHUNK = 500
    }
}
