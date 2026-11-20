package stapel.cache.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import stapel.cache.PartitionId
import stapel.cache.StoredEntry
import stapel.cache.StoredListHead
import stapel.cache.StoredListItem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `cache.db` version 1 (written by the previous release: `entry` + `meta`) opens as version 2 with
 * its entries kept and the list tables added, through the real factory path. Room validates the
 * migrated schema against the compiled entities; a migration that drifts from `schemas/…/2.json` is
 * rejected, which the factory reports as a discard — so "no discard" proves the statements match.
 */
class RoomMigrationTest {
    private val dirs = mutableListOf<Path>()
    private val account = PartitionId.account("migrating-account")
    private val key = "auth\u001Fme\u001F"

    @AfterTest
    fun cleanUp() {
        dirs.forEach { deleteTree(it) }
    }

    /** A partition directory holding a version-1 `cache.db` exactly as `schemas/…/1.json` describes it. */
    private fun versionOneFile(): Path {
        val dir = Path(SystemTemporaryDirectory, "stapel-cache-migrate-" + Random.nextLong().toULong().toString(16))
        dirs += dir
        val path = Path(dir, "stapel-cache", account.name, "cache.db")
        SystemFileSystem.createDirectories(path.parent!!)
        BundledSQLiteDriver().open(path.toString()).also { c ->
            V1_SQL.forEach { c.execSQL(it) }
            c.execSQL("INSERT INTO entry VALUES ('$key', 'auth', x'6f6c64', 'h', 42, 3, 0)")
            c.execSQL("INSERT INTO meta VALUES ('format', '${RoomCacheStoreFactory.STORE_FORMAT}')")
            c.execSQL("PRAGMA user_version = 1")
        }.close()
        return dir
    }

    @Test
    fun versionOneIsMigratedKeepingItsEntries() = runTest {
        val dir = versionOneFile()
        val discarded = mutableListOf<Throwable>()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default, onDiscarded = { _, e -> discarded += e }))

        val stores = factory.open(account)
        val kept = stores.entries.get(key)
        assertEquals(StoredEntry(key, "auth", "old".encodeToByteArray(), "h", 42, false), kept, "the v1 entry did not survive")
        assertEquals(emptyList(), discarded.map { it.message }, "the migrated file was discarded (schema mismatch after migration?)")

        val list = "categories\u001Ftree\u001F"
        stores.lists.replace(
            StoredListHead(list, "categories", ByteArray(0), null, null, false, false, "c1", 7, false),
            listOf(StoredListItem("a", "A".encodeToByteArray())),
        )
        stores.lists.writeCursor(list, 9)
        assertEquals(listOf("a"), stores.lists.items(list).map { it.itemId })
        stores.close()

        val path = Path(factory.partitionDir(account), "cache.db").toString()
        BundledSQLiteDriver().open(path).also { c ->
            assertEquals(CACHE_DB_VERSION.toLong(), c.long("PRAGMA user_version"))
            assertEquals(1L, c.long("SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_list_item_list_key_position'"))
            assertEquals(9L, c.long("SELECT value FROM cursor"))
        }.close()
    }

    private fun SQLiteConnection.long(sql: String): Long = prepare(sql).use { st ->
        assertTrue(st.step(), "no row for $sql")
        st.getLong(0)
    }

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteTree(it) }
        }
        SystemFileSystem.delete(path)
    }

    private companion object {
        /** `schemas/…/1.json` with `${TABLE_NAME}` filled in, plus Room's setup queries. */
        val V1_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `entry` (`key` TEXT NOT NULL, `namespace` TEXT NOT NULL, `payload` BLOB NOT NULL, " +
                "`schema_hash` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, `size` INTEGER NOT NULL, `invalidated` INTEGER NOT NULL, " +
                "PRIMARY KEY(`key`))",
            "CREATE INDEX IF NOT EXISTS `index_entry_fetched_at` ON `entry` (`fetched_at`)",
            "CREATE TABLE IF NOT EXISTS `meta` (`name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`name`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd5affe6b4c1ecae994125c4916773d40')",
        )
    }
}
