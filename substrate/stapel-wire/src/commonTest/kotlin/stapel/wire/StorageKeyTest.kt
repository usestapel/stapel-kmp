package stapel.wire

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageKeyTest {
    private val keys = listOf(
        CacheKey.of("chat", "conv"),
        CacheKey.of("chat", "conversations"),
        CacheKey.of("chat", "conversations", "7"),
        CacheKey.of("chat", "conv", args = buildJsonObject { put("q", "x") }),
        CacheKey.of("chat", "a\u001Fb", "c\u001Ed", "\u001De"),
        CacheKey.of("chat", ""),
        CacheKey.of("chatroom"),
        CacheKey.of("chat"),
    )

    private val patterns = listOf(
        KeyPattern.of("chat"),
        KeyPattern.of("chat", "conv"),
        KeyPattern.of("chat", "conversations"),
        KeyPattern.of("chat", "a\u001Fb"),
        KeyPattern.of("chat", ""),
        KeyPattern.exact(CacheKey.of("chat", "conv")),
    )

    /** The string-prefix test a store runs must agree with [KeyPattern.matches] for every pair. */
    @Test
    fun storagePrefixesAgreeWithMatchesAtSegmentBoundaries() {
        for (p in patterns) for (k in keys) {
            val byPrefix = if (p.isExact) k.storageKey == p.storagePrefix else k.storageKey.startsWith(p.storagePrefix)
            assertEquals(p.matches(k), byPrefix, "$p vs $k")
        }
        // The inversion: a plain separator join (no terminator) makes `chat/conv` swallow `chat/conversations`.
        val naive = { segs: List<String> -> segs.joinToString("\u001F") }
        assertTrue(naive(listOf("chat", "conversations")).startsWith(naive(listOf("chat", "conv"))))
        assertFalse(CacheKey.of("chat", "conversations").storageKey.startsWith(KeyPattern.of("chat", "conv").storagePrefix))
    }

    @Test
    fun storageKeysAreReversibleAndInjective() {
        for (k in keys) assertEquals(k, CacheKey.fromStorageKey(k.storageKey))
        assertEquals(keys.size, keys.map { it.storageKey }.toSet().size)
        val app = CacheKey.of("geo", "countries", scope = CacheScope.App)
        assertEquals(app, CacheKey.fromStorageKey(app.storageKey, CacheScope.App))
        assertNull(CacheKey.fromStorageKey("chat"))
        assertNull(CacheKey.fromStorageKey(""))
        assertNull(CacheKey.fromStorageKey("chat\u001F\u001E9\u001F"))
    }

    @Test
    fun exactnessIsPublic() {
        val key = CacheKey.of("auth", "me")
        assertTrue(KeyPattern.exact(key).isExact)
        assertEquals(key, KeyPattern.exact(key).exactKey)
        assertFalse(KeyPattern.of("auth", "me").isExact)
        assertNull(KeyPattern.of("auth").exactKey)
    }
}
