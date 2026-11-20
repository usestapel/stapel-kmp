package stapel.cache.testing

import kotlinx.coroutines.test.runTest
import stapel.cache.CacheStoreFactory
import stapel.cache.ListStore
import stapel.cache.MemoryCacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.PartitionStores
import stapel.cache.StoredListHead
import stapel.cache.StoredListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListStoreContractTest {
    @Test
    fun memoryStorePassesTheContract() = runTest {
        assertEquals(ListStoreContract.caseNames.size, ListStoreContract.verify { MemoryCacheStoreFactory() })
    }

    // Inversions: each broken store must be caught by the named case.

    @Test
    fun mergeThatMovesUpdatedRowsToTheEndIsCaught() = runTest {
        assertCaught("merge updates in place, appends new ids in order and removes ids") {
            Broken { s -> object : ListStore by s {
                override suspend fun merge(head: StoredListHead, upserts: List<StoredListItem>, removedIds: Collection<String>) {
                    s.merge(head, emptyList(), removedIds + upserts.map { it.itemId })
                    s.merge(head, upserts, emptyList())
                }
            } }
        }
    }

    @Test
    fun replaceThatKeepsOldRowsIsCaught() = runTest {
        assertCaught("replace drops the rows it does not name") {
            Broken { s -> object : ListStore by s {
                override suspend fun replace(head: StoredListHead, items: List<StoredListItem>) = s.merge(head, items, emptyList())
            } }
        }
    }

    @Test
    fun deletePrefixThatKeepsCursorsIsCaught() = runTest {
        assertCaught("deletePrefix removes lists and cursors by segment prefix only") {
            Broken { s -> object : ListStore by s {
                override suspend fun deletePrefix(keyPrefix: String): Int {
                    val cursors = s.keys(keyPrefix).associateWith { s.readCursor(it) }
                    val n = s.deletePrefix(keyPrefix)
                    cursors.forEach { (k, v) -> if (v != null) s.writeCursor(k, v) }
                    return n
                }
            } }
        }
    }

    @Test
    fun listsWithoutSegmentBoundaryIsCaught() = runTest {
        assertCaught("markInvalidated matches lists by segment prefix only") {
            Broken { s -> object : ListStore by s {
                override suspend fun markInvalidated(keyPrefix: String) = s.markInvalidated(keyPrefix.trimEnd('\u001F'))
            } }
        }
    }

    @Test
    fun sharedListsAcrossPartitionsAreCaught() = runTest {
        val shared = MemoryCacheStoreFactory()
        assertCaught("partitions are isolated") {
            object : CacheStoreFactory by shared {
                override suspend fun open(partition: PartitionId) = shared.open(PartitionId.App)
            }
        }
    }

    private suspend fun assertCaught(case: String, factory: () -> CacheStoreFactory) {
        assertTrue(case in ListStoreContract.caseNames, "unknown case $case")
        val error = assertFailsWith<AssertionError> { ListStoreContract.verify { factory() } }
        assertTrue("- $case:" in error.message.orEmpty(), "case <$case> not reported in:\n${error.message}")
    }

    /** A memory factory whose list stores are wrapped by [wrap]. */
    private class Broken(private val wrap: (ListStore) -> ListStore) : CacheStoreFactory {
        private val inner = MemoryCacheStoreFactory()
        override val durable: Boolean get() = inner.durable
        override suspend fun open(partition: PartitionId): PartitionStores {
            val stores = inner.open(partition)
            return PartitionStores(stores.entries, wrap(stores.lists), stores.close)
        }
        override suspend fun destroy(partition: PartitionId) = inner.destroy(partition)
        override suspend fun listPartitions() = inner.listPartitions()
    }
}
