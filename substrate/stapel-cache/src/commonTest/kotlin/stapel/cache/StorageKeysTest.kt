package stapel.cache

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import stapel.wire.KeyPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** The engine stores entries under stapel-wire's `CacheKey.storageKey` and selects with `KeyPattern.storagePrefix`. */
class StorageKeysTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }

    private fun spec(key: CacheKey) = QuerySpec(key, String.serializer(), "s1", ReadPolicy.Default)

    private val conv = CacheKey.of("chat", "conv", scope = CacheScope.App)
    private val conv42 = CacheKey.of("chat", "conv", "42", scope = CacheScope.App)
    private val convPage = CacheKey.of("chat", "conv", args = JsonObject(mapOf("page" to JsonPrimitive(2))), scope = CacheScope.App)
    private val conversations = CacheKey.of("chat", "conversations", scope = CacheScope.App)

    private suspend fun seeded(): Pair<ManagedStapelCache, CacheStore> {
        val factory = MemoryCacheStoreFactory()
        val cache = StapelCache(StapelCacheConfig(json = Json, scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()), clock = clock, storeFactory = factory))
        listOf(conv, conv42, convPage, conversations).forEach { cache.write(spec(it), it.toString()) }
        return cache to factory.open(PartitionId.App).entries
    }

    @Test
    fun entriesLiveUnderTheWireStorageKey() = runTest {
        val (_, store) = seeded()
        listOf(conv, conv42, convPage, conversations).forEach { assertNotNull(store.get(it.storageKey), "$it") }
        assertEquals(4, store.count())
    }

    @Test
    fun prefixInvalidationIsBySegmentNotByCharacter() = runTest {
        val (cache, store) = seeded()
        cache.invalidate(KeyPattern.of("chat", "conv"), Refetch.None)
        val invalidated = store.keys("").filter { store.get(it)!!.invalidated }.toSet()
        assertEquals(setOf(conv, conv42, convPage).map { it.storageKey }.toSet(), invalidated)
    }

    @Test
    fun anExactPatternRemovesOneKeyNotItsChildren() = runTest {
        val (cache, store) = seeded()
        cache.remove(KeyPattern.exact(conv))
        assertNull(store.get(conv.storageKey))
        assertNotNull(store.get(conv42.storageKey))
        assertNotNull(store.get(convPage.storageKey))
        cache.remove(KeyPattern.of("chat", "conv"))
        assertNull(store.get(conv42.storageKey))
        assertNotNull(store.get(conversations.storageKey))
    }
}
