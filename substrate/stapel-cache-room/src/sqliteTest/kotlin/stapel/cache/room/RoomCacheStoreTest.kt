package stapel.cache.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import stapel.cache.PartitionId
import stapel.cache.StoredEntry
import stapel.cache.testing.CacheStoreContract
import stapel.cache.testing.ListStoreContract
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCacheStoreTest {
    private val dirs = mutableListOf<Path>()
    private val account = PartitionId.account("account-7f3a")
    private val key = "auth\u001Fme\u001F"

    private fun tempDir(): Path {
        val dir = Path(SystemTemporaryDirectory, "stapel-cache-room-" + Random.nextLong().toULong().toString(16))
        SystemFileSystem.createDirectories(dir)
        dirs += dir
        return dir
    }

    @AfterTest
    fun cleanUp() {
        dirs.forEach { deleteTree(it) }
    }

    @Test
    fun roomStorePassesTheContract() = runTest {
        val ran = CacheStoreContract.verify { RoomCacheStoreFactory(tempDir().toString(), RoomCacheOptions(Dispatchers.Default)) }
        assertEquals(CacheStoreContract.caseNames.size, ran)
    }

    @Test
    fun roomListStorePassesTheListContract() = runTest {
        val ran = ListStoreContract.verify { RoomCacheStoreFactory(tempDir().toString(), RoomCacheOptions(Dispatchers.Default)) }
        assertEquals(ListStoreContract.caseNames.size, ran)
    }

    @Test
    fun partitionsAreSeparateFilesNamedByHash() = runTest {
        val dir = tempDir()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))
        val app = factory.open(PartitionId.App)
        val user = factory.open(account)
        app.entries.put(entry(key, "app"))
        user.entries.put(entry(key, "user"))

        assertTrue(SystemFileSystem.exists(Path(dir, "stapel-cache", "app", "cache.db")))
        assertTrue(SystemFileSystem.exists(Path(dir, "stapel-cache", account.name, "cache.db")))
        assertTrue(Regex("u-[0-9a-f]{16}").matches(account.name))
        val names = SystemFileSystem.list(Path(dir, "stapel-cache")).map { it.name }.toSet()
        assertEquals(setOf("app", account.name), names)
        assertFalse(names.any { "account" in it }, "an account id appears in a path: $names")
        assertEquals(listOf(PartitionId.App, account).sortedBy { it.name }, factory.listPartitions())
        app.close()
        user.close()
    }

    @Test
    fun wipeClosesAndDeletesEveryFileOfThePartition() = runTest {
        val dir = tempDir()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))
        val app = factory.open(PartitionId.App)
        val user = factory.open(account)
        app.entries.put(entry(key, "app"))
        repeat(50) { user.entries.put(entry("auth\u001F$it\u001F", "v$it")) }
        val partitionDir = factory.partitionDir(account)
        // Inversion guard: the files exist before the wipe, so "absent after" is a real observation.
        val before = SystemFileSystem.list(partitionDir).map { it.name }
        assertTrue("cache.db" in before, "files before wipe: $before")

        factory.destroy(account)

        assertFalse(SystemFileSystem.exists(partitionDir), "partition directory survived the wipe")
        val residue = SystemFileSystem.list(Path(dir, "stapel-cache")).map { it.name }
        assertEquals(listOf("app"), residue, "residue after wipe")
        assertEquals("app", app.entries.get(key)?.payload?.decodeToString(), "app partition lost data")
        assertEquals(listOf(PartitionId.App), factory.listPartitions())
        app.close()
    }

    @Test
    fun reopenAfterAbandonedHandleReadsFromDisk() = runTest {
        // Process death: the first instance is never closed; a new instance over the same directory
        // (the next process) must read what was written.
        val dir = tempDir()
        val first = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))
        val stores = first.open(account)
        stores.entries.put(entry(key, "last known me", fetchedAt = 42, invalidated = true))
        stores.entries.put(entry("auth\u001Fcapabilities\u001F", "caps"))

        val second = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))
        assertEquals(listOf(account), second.listPartitions())
        val reopened = second.open(account)
        val read = assertNotNull(reopened.entries.get(key))
        assertEquals("last known me", read.payload.decodeToString())
        assertEquals(42, read.fetchedAtMillis)
        assertTrue(read.invalidated)
        assertEquals(2, reopened.entries.count())
        reopened.close()
        stores.close()
    }

    @Test
    fun unreadableFileIsDiscardedAndRecreated() = runTest {
        val dir = tempDir()
        val partitionDir = Path(dir, "stapel-cache", account.name)
        SystemFileSystem.createDirectories(partitionDir)
        SystemFileSystem.sink(Path(partitionDir, "cache.db")).buffered().use {
            it.writeString("this is not a database ".repeat(200))
        }
        val discarded = mutableListOf<PartitionId>()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default, onDiscarded = { p, _ -> discarded += p }))

        val stores = factory.open(account)
        assertEquals(0, stores.entries.count())
        stores.entries.put(entry(key, "fresh"))
        assertEquals("fresh", stores.entries.get(key)?.payload?.decodeToString())
        assertEquals(listOf(account), discarded)
        stores.close()
    }

    @Test
    fun foreignSchemaAtTheSameVersionIsDiscarded() = runTest {
        val dir = tempDir()
        val path = Path(dir, "stapel-cache", account.name, "cache.db")
        SystemFileSystem.createDirectories(path.parent!!)
        BundledSQLiteDriver().open(path.toString()).also { c ->
            c.execSQL("CREATE TABLE entry (`key` TEXT PRIMARY KEY, other INTEGER)")
            c.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            c.execSQL("INSERT INTO room_master_table VALUES (42, 'not-our-schema')")
            c.execSQL("PRAGMA user_version = $CACHE_DB_VERSION")
        }.close()
        val discarded = mutableListOf<Throwable>()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default, onDiscarded = { _, e -> discarded += e }))

        val stores = factory.open(account)
        stores.entries.put(entry(key, "fresh"))
        assertEquals("fresh", stores.entries.get(key)?.payload?.decodeToString())
        assertEquals(1, discarded.size, "foreign schema was not discarded")
        stores.close()
    }

    @Test
    fun newerSchemaVersionIsDroppedNotMigrated() = runTest {
        val dir = tempDir()
        val path = Path(dir, "stapel-cache", account.name, "cache.db")
        SystemFileSystem.createDirectories(path.parent!!)
        BundledSQLiteDriver().open(path.toString()).also { c ->
            c.execSQL("CREATE TABLE future (x INTEGER)")
            c.execSQL("PRAGMA user_version = ${CACHE_DB_VERSION + 6}")
        }.close()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))

        val stores = factory.open(account)
        stores.entries.put(entry(key, "fresh"))
        assertEquals("fresh", stores.entries.get(key)?.payload?.decodeToString())
        stores.close()
    }

    @Test
    fun otherStoreFormatIsDiscardedAndSameFormatIsKept() = runTest {
        // Same format: the data survives a reopen (inversion of the discard below).
        val dir = tempDir()
        val factory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default))
        factory.open(account).also { it.entries.put(entry(key, "kept")) }.close()
        factory.open(account).also {
            assertEquals("kept", it.entries.get(key)?.payload?.decodeToString())
        }.close()

        // Another format number written by "another version" of the library.
        val path = Path(factory.partitionDir(account), "cache.db").toString()
        cacheDatabaseBuilder(path).setDriver(BundledSQLiteDriver()).build().also {
            it.entries().putMeta(MetaRow("format", "${RoomCacheStoreFactory.STORE_FORMAT + 1}"))
        }.close()
        val discarded = mutableListOf<PartitionId>()
        val next = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default, onDiscarded = { p, _ -> discarded += p }))
        next.open(account).also {
            assertNull(it.entries.get(key), "entry of another store format was served")
        }.close()
        assertEquals(listOf(account), discarded)
    }

    @Test
    fun handlesAreCountedPerPartition() = runTest {
        val factory = RoomCacheStoreFactory(tempDir().toString(), RoomCacheOptions(Dispatchers.Default))
        val a = factory.open(account)
        val b = factory.open(account)
        a.entries.put(entry(key, "v"))
        a.close()
        // `b` still works after `a` closed: the database is closed with the last handle only.
        assertContentEquals("v".encodeToByteArray(), b.entries.get(key)?.payload)
        b.close()
    }

    private fun entry(storageKey: String, value: String, fetchedAt: Long = 1, invalidated: Boolean = false) =
        StoredEntry(storageKey, storageKey.substringBefore('\u001F'), value.encodeToByteArray(), "h", fetchedAt, invalidated)

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteTree(it) }
        }
        SystemFileSystem.delete(path)
    }
}
