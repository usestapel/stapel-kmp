package stapel.cache.room

import stapel.cache.PartitionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomCacheOptionsTest {
    @Test
    fun databaseFilesCoverTheWriteAheadLogAndJournal() {
        assertEquals(
            listOf("cache.db", "cache.db-wal", "cache.db-shm", "cache.db-journal"),
            RoomCacheStoreFactory.DB_FILES,
        )
    }

    @Test
    fun partitionDirectoryIsUnderTheCacheRoot() {
        val factory = RoomCacheStoreFactory("/data/app")
        val dir = factory.partitionDir(PartitionId.account("x")).toString()
        assertTrue(dir.startsWith("/data/app/stapel-cache/u-"), dir)
        assertTrue(factory.durable)
    }
}
