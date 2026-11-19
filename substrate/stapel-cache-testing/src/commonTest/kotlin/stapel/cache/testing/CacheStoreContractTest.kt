package stapel.cache.testing

import kotlinx.coroutines.test.runTest
import stapel.cache.CacheStore
import stapel.cache.CacheStoreFactory
import stapel.cache.MemoryCacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.PartitionStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CacheStoreContractTest {
    @Test
    fun memoryStorePassesTheContract() = runTest {
        assertEquals(CacheStoreContract.caseNames.size, CacheStoreContract.verify { MemoryCacheStoreFactory() })
    }

    // Inversions: each broken store must be caught by the named case.

    @Test
    fun prefixWithoutSegmentBoundaryIsCaught() = runTest {
        assertCaught("deletePrefix matches by segment prefix only") {
            Broken { s -> object : CacheStore by s {
                override suspend fun deletePrefix(keyPrefix: String) = s.deletePrefix(keyPrefix.trimEnd('\u001F'))
            } }
        }
    }

    @Test
    fun invalidationThatMarksNothingIsCaught() = runTest {
        assertCaught("markInvalidated matches by segment prefix only") {
            Broken { s -> object : CacheStore by s {
                override suspend fun markInvalidated(keyPrefix: String) = 2
            } }
        }
    }

    @Test
    fun keyListingWithoutSegmentBoundaryIsCaught() = runTest {
        assertCaught("keys lists by segment prefix only") {
            Broken { s -> object : CacheStore by s {
                override suspend fun keys(keyPrefix: String) = s.keys(keyPrefix.trimEnd('\u001F'))
            } }
        }
    }

    @Test
    fun lossyPayloadIsCaught() = runTest {
        assertCaught("empty and large payloads round-trip") {
            Broken { s -> object : CacheStore by s {
                override suspend fun get(storageKey: String) = s.get(storageKey)?.let {
                    stapel.cache.StoredEntry(
                        it.storageKey, it.namespace, it.payload.take(65_536).toByteArray(),
                        it.schemaHash, it.fetchedAtMillis, it.invalidated,
                    )
                }
            } }
        }
    }

    @Test
    fun sharedStorageAcrossPartitionsIsCaught() = runTest {
        val shared = MemoryCacheStoreFactory()
        assertCaught("partitions are isolated") {
            object : CacheStoreFactory by shared {
                override suspend fun open(partition: PartitionId) = shared.open(PartitionId.App)
            }
        }
    }

    @Test
    fun memoryStoreClaimingDurabilityIsCaught() = runTest {
        assertCaught("close then reopen keeps data exactly when the factory is durable") {
            val inner = MemoryCacheStoreFactory()
            object : CacheStoreFactory by inner {
                override val durable: Boolean get() = true
            }
        }
    }

    @Test
    fun destroyThatKeepsDataIsCaught() = runTest {
        assertCaught("destroy deletes an open partition and leaves others") {
            val inner = MemoryCacheStoreFactory()
            object : CacheStoreFactory by inner {
                override suspend fun destroy(partition: PartitionId) = Unit
            }
        }
    }

    private suspend fun assertCaught(case: String, factory: () -> CacheStoreFactory) {
        assertTrue(case in CacheStoreContract.caseNames, "unknown case $case")
        val error = assertFailsWith<AssertionError> { CacheStoreContract.verify { factory() } }
        assertTrue("- $case:" in error.message.orEmpty(), "case <$case> not reported in:\n${error.message}")
    }

    /** A memory factory whose stores are wrapped by [wrap]. */
    private class Broken(private val wrap: (CacheStore) -> CacheStore) : CacheStoreFactory {
        private val inner = MemoryCacheStoreFactory()
        override val durable: Boolean get() = inner.durable
        override suspend fun open(partition: PartitionId): PartitionStores {
            val stores = inner.open(partition)
            return PartitionStores(wrap(stores.entries), stores.close)
        }
        override suspend fun destroy(partition: PartitionId) = inner.destroy(partition)
        override suspend fun listPartitions() = inner.listPartitions()
    }
}
