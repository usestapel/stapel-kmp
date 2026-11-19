package stapel.cache

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.wire.CacheKey
import stapel.wire.KeyPattern
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StorageKeysTest {
    private fun covers(pattern: KeyPattern, key: CacheKey) = storageKeyOf(key).startsWith(storagePrefixOf(pattern))

    @Test
    fun prefixIsBySegmentNotByCharacter() {
        assertTrue(covers(KeyPattern.of("chat", "conv"), CacheKey.of("chat", "conv")))
        assertTrue(covers(KeyPattern.of("chat", "conv"), CacheKey.of("chat", "conv", "42")))
        assertFalse(covers(KeyPattern.of("chat", "conv"), CacheKey.of("chat", "conversations")))
        assertFalse(covers(KeyPattern.of("chat"), CacheKey.of("chatter", "x")))
    }

    @Test
    fun argumentsAreASegmentOfTheirOwn() {
        val withArgs = CacheKey.of("chat", "conv", args = JsonObject(mapOf("page" to JsonPrimitive(2))))
        assertTrue(covers(KeyPattern.of("chat", "conv"), withArgs))
        assertNotEquals(storageKeyOf(withArgs), storageKeyOf(CacheKey.of("chat", "conv", "{\"page\":2}")))
    }

    @Test
    fun storageKeyAgreesWithPatternMatching() {
        val keys = listOf(
            CacheKey.of("a"), CacheKey.of("a", "b"), CacheKey.of("a", "bc"), CacheKey.of("ab"),
            CacheKey.of("a", "b", "c"), CacheKey.of("a", "\u001Eb"), CacheKey.of("каталог", "🚀"),
        )
        val patterns = listOf(
            KeyPattern.of("a"), KeyPattern.of("a", "b"), KeyPattern.of("ab"), KeyPattern.of("a", "\u001Eb"),
            KeyPattern.of("каталог"),
        )
        for (p in patterns) for (k in keys) {
            assertTrue(p.matches(k) == covers(p, k), "pattern $p vs key $k disagree")
        }
    }

    @Test
    fun storageKeyDecodesBackToTheSameKey() {
        val keys = listOf(
            CacheKey.of("a"),
            CacheKey.of("a", "b", "c"),
            CacheKey.of("a", "\u001Eb", "x\u001Ey"),
            CacheKey.of("chat", "conv", args = JsonObject(mapOf("page" to JsonPrimitive(2), "q" to JsonPrimitive("x")))),
            CacheKey.of("каталог", "🚀"),
        )
        for (k in keys) kotlin.test.assertEquals(k, cacheKeyOf(storageKeyOf(k), k.scope), "round trip of $k")
        kotlin.test.assertNull(cacheKeyOf("no-terminator", stapel.wire.CacheScope.User))
    }

    @Test
    fun exactPatternsAreTellableFromPrefixPatterns() {
        val k = CacheKey.of("chat", "conv", args = JsonObject(mapOf("page" to JsonPrimitive(2))))
        assertTrue(isExact(KeyPattern.exact(k)))
        assertTrue(isExact(KeyPattern.exact(CacheKey.of("chat", "conv"))))
        assertFalse(isExact(KeyPattern.of("chat", "conv")))
        assertFalse(isExact(KeyPattern.of("chat")))
    }
}
