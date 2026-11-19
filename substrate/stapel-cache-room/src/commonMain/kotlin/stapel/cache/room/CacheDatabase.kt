package stapel.cache.room

import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Upsert

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

/**
 * `cache.db` of one partition. Disposable by design: any schema change is a destructive
 * recreate (no migrations), see [RoomCacheStoreFactory].
 */
@Database(entities = [EntryRow::class, MetaRow::class], version = CACHE_DB_VERSION, exportSchema = true)
@ConstructedBy(CacheDatabaseConstructor::class)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

/** Room schema version of `cache.db`. Bump on any entity change; old files are dropped, never migrated. */
internal const val CACHE_DB_VERSION: Int = 1

/** Platform builder for a database file at [path] (Room's builder entry points are platform API). */
internal expect fun cacheDatabaseBuilder(path: String): RoomDatabase.Builder<CacheDatabase>
