package stapel.cache.room

import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/** One row of the generic document cache; mirrors `stapel.cache.StoredEntry`. */
@Entity(tableName = "entry", indices = [Index("fetched_at")])
internal class EntryRow(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "namespace") val namespace: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
    @ColumnInfo(name = "schema_hash") val schemaHash: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "invalidated") val invalidated: Boolean,
)

/** Store-level facts (the store format number). */
@Entity(tableName = "meta")
internal class MetaRow(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "value") val value: String,
)

/** The head of a stored list; mirrors `stapel.cache.StoredListHead`. */
@Entity(tableName = "list_head")
internal class ListHeadRow(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "namespace") val namespace: String,
    @ColumnInfo(name = "envelope") val envelope: ByteArray,
    @ColumnInfo(name = "next_anchor") val nextAnchor: String?,
    @ColumnInfo(name = "prev_anchor") val prevAnchor: String?,
    @ColumnInfo(name = "has_next") val hasNext: Boolean,
    @ColumnInfo(name = "has_prev") val hasPrev: Boolean,
    @ColumnInfo(name = "schema_hash") val schemaHash: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "invalidated") val invalidated: Boolean,
)

/** One row of a stored list, ordered by [position] inside its list. */
@Entity(
    tableName = "list_item",
    primaryKeys = ["list_key", "item_id"],
    indices = [Index("list_key", "position")],
    foreignKeys = [
        ForeignKey(entity = ListHeadRow::class, parentColumns = ["key"], childColumns = ["list_key"], onDelete = ForeignKey.CASCADE),
    ],
)
internal class ListItemRow(
    @ColumnInfo(name = "list_key") val listKey: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "position") val position: Long,
    @ColumnInfo(name = "payload") val payload: ByteArray,
)

/** A durable `Long` cursor (a revision collection's last synced revision, a resume sequence). */
@Entity(tableName = "cursor")
internal class CursorRow(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: Long,
)

/**
 * Prefix predicate used by the prefix operations. `substr`/`length` count characters on TEXT, so
 * this is an exact string-prefix test for any key; the `>=` bound lets SQLite start at the prefix
 * in the primary-key index.
 */
private const val PREFIX = "`key` >= :prefix AND substr(`key`, 1, length(:prefix)) = :prefix"

@Dao
internal interface EntryDao {
    @Query("SELECT * FROM entry WHERE `key` = :key")
    suspend fun get(key: String): EntryRow?

    @Upsert
    suspend fun put(row: EntryRow)

    @Query("DELETE FROM entry WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("UPDATE entry SET invalidated = 1 WHERE $PREFIX")
    suspend fun markInvalidated(prefix: String): Int

    @Query("DELETE FROM entry WHERE $PREFIX")
    suspend fun deletePrefix(prefix: String): Int

    @Query("SELECT `key` FROM entry WHERE $PREFIX")
    suspend fun keys(prefix: String): List<String>

    @Query("SELECT COUNT(*) FROM entry")
    suspend fun count(): Int

    @Query("DELETE FROM entry")
    suspend fun clear()

    @Query("SELECT value FROM meta WHERE name = :name")
    suspend fun meta(name: String): String?

    @Upsert
    suspend fun putMeta(row: MetaRow)
}

private const val HEAD_PREFIX = "`key` >= :prefix AND substr(`key`, 1, length(:prefix)) = :prefix"
private const val ITEM_PREFIX = "list_key >= :prefix AND substr(list_key, 1, length(:prefix)) = :prefix"

@Dao
internal interface ListDao {
    @Query("SELECT * FROM list_head WHERE `key` = :key")
    suspend fun head(key: String): ListHeadRow?

    @Query("SELECT * FROM list_item WHERE list_key = :key ORDER BY position")
    suspend fun items(key: String): List<ListItemRow>

    @Upsert
    suspend fun putHead(row: ListHeadRow)

    @Upsert
    suspend fun putItems(rows: List<ListItemRow>)

    @Query("UPDATE list_item SET payload = :payload WHERE list_key = :key AND item_id = :itemId")
    suspend fun updatePayload(key: String, itemId: String, payload: ByteArray): Int

    @Query("SELECT MAX(position) FROM list_item WHERE list_key = :key")
    suspend fun maxPosition(key: String): Long?

    @Query("DELETE FROM list_item WHERE list_key = :key")
    suspend fun deleteItems(key: String)

    @Query("DELETE FROM list_item WHERE list_key = :key AND item_id IN (:itemIds)")
    suspend fun deleteItemIds(key: String, itemIds: List<String>)

    @Query("DELETE FROM list_head WHERE `key` = :key")
    suspend fun deleteHead(key: String)

    @Query("UPDATE list_head SET invalidated = 1 WHERE $HEAD_PREFIX")
    suspend fun markInvalidated(prefix: String): Int

    @Query("DELETE FROM list_item WHERE $ITEM_PREFIX")
    suspend fun deleteItemsPrefix(prefix: String)

    @Query("DELETE FROM list_head WHERE $HEAD_PREFIX")
    suspend fun deleteHeadsPrefix(prefix: String): Int

    @Query("SELECT `key` FROM list_head WHERE $HEAD_PREFIX")
    suspend fun keys(prefix: String): List<String>

    @Query("SELECT value FROM cursor WHERE `key` = :key")
    suspend fun cursor(key: String): Long?

    @Upsert
    suspend fun putCursor(row: CursorRow)

    @Query("DELETE FROM cursor WHERE `key` = :key")
    suspend fun deleteCursor(key: String)

    @Query("DELETE FROM cursor WHERE $HEAD_PREFIX")
    suspend fun deleteCursorsPrefix(prefix: String)

    @Query("DELETE FROM list_item")
    suspend fun clearItems()

    @Query("DELETE FROM list_head")
    suspend fun clearHeads()

    @Query("DELETE FROM cursor")
    suspend fun clearCursors()
}

/**
 * `cache.db` of one partition. Disposable by design: a schema change without a migration is a
 * destructive recreate, see [RoomCacheStoreFactory]; version 1 → 2 (the list and cursor tables) is
 * migrated so stored entries survive the upgrade.
 */
@Database(
    entities = [EntryRow::class, MetaRow::class, ListHeadRow::class, ListItemRow::class, CursorRow::class],
    version = CACHE_DB_VERSION,
    exportSchema = true,
)
@ConstructedBy(CacheDatabaseConstructor::class)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao

    abstract fun lists(): ListDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

/**
 * Room schema version of `cache.db`. Bump on any entity change; add a migration when the entries
 * must survive, otherwise the old file is dropped.
 *
 * 1: `entry`, `meta`. 2: + `list_head`, `list_item`, `cursor` ([MIGRATION_1_2]).
 */
internal const val CACHE_DB_VERSION: Int = 2

/** Adds the list and cursor tables; the statements are the ones of `schemas/…/2.json`. */
internal val MIGRATION_1_2: Migration = Migration(1, 2) { connection ->
    MIGRATION_1_2_SQL.forEach { connection.execSQL(it) }
}

internal val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `list_head` (`key` TEXT NOT NULL, `namespace` TEXT NOT NULL, `envelope` BLOB NOT NULL, " +
        "`next_anchor` TEXT, `prev_anchor` TEXT, `has_next` INTEGER NOT NULL, `has_prev` INTEGER NOT NULL, " +
        "`schema_hash` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, `size` INTEGER NOT NULL, `invalidated` INTEGER NOT NULL, " +
        "PRIMARY KEY(`key`))",
    "CREATE TABLE IF NOT EXISTS `list_item` (`list_key` TEXT NOT NULL, `item_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "`payload` BLOB NOT NULL, PRIMARY KEY(`list_key`, `item_id`), FOREIGN KEY(`list_key`) REFERENCES `list_head`(`key`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_list_item_list_key_position` ON `list_item` (`list_key`, `position`)",
    "CREATE TABLE IF NOT EXISTS `cursor` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))",
)

/** Platform builder for a database file at [path] (Room's builder entry points are platform API). */
internal expect fun cacheDatabaseBuilder(path: String): RoomDatabase.Builder<CacheDatabase>
