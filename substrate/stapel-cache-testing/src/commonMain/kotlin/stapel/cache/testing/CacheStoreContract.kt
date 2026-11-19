package stapel.cache.testing

import stapel.cache.CacheStore
import stapel.cache.CacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.StoredEntry

/**
 * The behaviour every [CacheStoreFactory] and its [CacheStore]s must show. The memory store and
 * every durable store run the same suite, which is what makes them interchangeable (degrade parity).
 *
 * Usage, from a test of the store under test:
 * ```
 * @Test fun contract() = runTest { CacheStoreContract.verify { MyStoreFactory(tempDir()) } }
 * ```
 * The suite does not depend on a test framework: a broken rule is reported as one [AssertionError]
 * naming every failed case.
 */
public object CacheStoreContract {
    /** Names of the cases, in run order. */
    public val caseNames: List<String> get() = cases.map { it.first }

    /**
     * Runs every case against a fresh factory from [newFactory] (one per case) and throws an
     * [AssertionError] listing the failed cases. Returns the number of cases that ran.
     */
    public suspend fun verify(newFactory: suspend () -> CacheStoreFactory): Int {
        val failures = mutableListOf<String>()
        for ((name, case) in cases) {
            val factory = newFactory()
            try {
                case(factory)
            } catch (e: AssertionError) {
                failures += "$name: ${e.message}"
            } catch (e: Exception) {
                failures += "$name: unexpected ${e::class.simpleName}: ${e.message}"
            } finally {
                runCatching { factory.listPartitions().forEach { factory.destroy(it) } }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "CacheStoreContract: ${failures.size} of ${cases.size} cases failed:\n" +
                    failures.joinToString("\n") { "  - $it" },
            )
        }
        return cases.size
    }

    private val one = PartitionId.account("contract-one")
    private val two = PartitionId.account("contract-two")

    private val cases: List<Pair<String, suspend (CacheStoreFactory) -> Unit>> = listOf(
        "absent key reads null" to { f ->
            withStore(f) { s -> expect(s.get(key("a", "b")) == null) { "get of an absent key returned an entry" } }
        },
        "put then get returns the same entry" to { f ->
            withStore(f) { s ->
                val e = entry(key("auth", "me"), byteArrayOf(1, 2, 3, -1), fetchedAt = 1234, invalidated = true)
                s.put(e)
                expectEqual(e, s.get(e.storageKey), "round trip")
            }
        },
        "empty and large payloads round-trip" to { f ->
            withStore(f) { s ->
                val empty = entry(key("a", "empty"), ByteArray(0))
                val large = entry(key("a", "large"), ByteArray(256 * 1024) { (it % 251).toByte() })
                s.put(empty)
                s.put(large)
                expectEqual(empty, s.get(empty.storageKey), "empty payload")
                expectEqual(large, s.get(large.storageKey), "large payload")
            }
        },
        "put replaces the entry under the same key" to { f ->
            withStore(f) { s ->
                val k = key("a", "x")
                s.put(entry(k, byteArrayOf(1), fetchedAt = 1))
                val second = entry(k, byteArrayOf(2), fetchedAt = 2, schemaHash = "h2")
                s.put(second)
                expectEqual(second, s.get(k), "replaced entry")
                expectEqual(1, s.count(), "count after replace")
            }
        },
        "delete removes one key and ignores an absent one" to { f ->
            withStore(f) { s ->
                s.put(entry(key("a", "1"), byteArrayOf(1)))
                s.put(entry(key("a", "2"), byteArrayOf(2)))
                s.delete(key("a", "1"))
                s.delete(key("a", "absent"))
                expect(s.get(key("a", "1")) == null) { "deleted entry still readable" }
                expect(s.get(key("a", "2")) != null) { "delete removed a neighbour" }
            }
        },
        "markInvalidated matches by segment prefix only" to { f ->
            withStore(f) { s ->
                seedPrefixFixture(s)
                expectEqual(2, s.markInvalidated(key("chat", "conv")), "matched count")
                expect(s.get(key("chat", "conv"))!!.invalidated) { "exact key not invalidated" }
                expect(s.get(key("chat", "conv", "{\"page\":2}"))!!.invalidated) { "child key not invalidated" }
                expect(!s.get(key("chat", "conversations"))!!.invalidated) { "sibling with a longer segment invalidated" }
                expect(!s.get(key("notifications", "feed"))!!.invalidated) { "other namespace invalidated" }
                expectEqual(byteArrayOf(7).toList(), s.get(key("chat", "conv"))!!.payload.toList(), "payload kept")
            }
        },
        "deletePrefix matches by segment prefix only" to { f ->
            withStore(f) { s ->
                seedPrefixFixture(s)
                expectEqual(2, s.deletePrefix(key("chat", "conv")), "deleted count")
                expect(s.get(key("chat", "conv")) == null) { "exact key kept" }
                expect(s.get(key("chat", "conversations")) != null) { "sibling with a longer segment deleted" }
                expectEqual(2, s.count(), "remaining count")
            }
        },
        "empty prefix matches every entry" to { f ->
            withStore(f) { s ->
                seedPrefixFixture(s)
                expectEqual(4, s.markInvalidated(""), "invalidated count")
                expectEqual(4, s.deletePrefix(""), "deleted count")
                expectEqual(0, s.count(), "count")
            }
        },
        "non-ASCII keys round-trip and prefix-match" to { f ->
            withStore(f) { s ->
                val cyr = key("каталог", "категории")
                val emoji = key("каталог", "🚀", "x")
                val high = key("каталог", "�")
                listOf(cyr, emoji, high).forEach { s.put(entry(it, it.encodeToByteArray())) }
                expectEqual(cyr.encodeToByteArray().toList(), s.get(cyr)?.payload?.toList(), "cyrillic key")
                expectEqual(3, s.markInvalidated(key("каталог")), "namespace prefix")
                expectEqual(1, s.deletePrefix(key("каталог", "🚀")), "supplementary-char prefix")
                expectEqual(2, s.count(), "count")
            }
        },
        "count and clear" to { f ->
            withStore(f) { s ->
                repeat(5) { s.put(entry(key("n", "$it"), byteArrayOf(it.toByte()))) }
                expectEqual(5, s.count(), "count")
                s.clear()
                expectEqual(0, s.count(), "count after clear")
                expect(s.get(key("n", "1")) == null) { "entry readable after clear" }
            }
        },
        "partitions are isolated" to { f ->
            val a = f.open(one)
            val b = f.open(two)
            try {
                a.entries.put(entry(key("auth", "me"), byteArrayOf(1)))
                expect(b.entries.get(key("auth", "me")) == null) { "entry leaked into another partition" }
                expectEqual(0, b.entries.deletePrefix(""), "other partition count")
                expect(a.entries.get(key("auth", "me")) != null) { "entry lost in its own partition" }
            } finally {
                a.close()
                b.close()
            }
        },
        "listPartitions names partitions that hold data" to { f ->
            val app = f.open(PartitionId.App)
            val a = f.open(one)
            try {
                app.entries.put(entry(key("site", "config"), byteArrayOf(1)))
                a.entries.put(entry(key("auth", "me"), byteArrayOf(1)))
                val listed = f.listPartitions().toSet()
                expect(PartitionId.App in listed && one in listed) { "listed $listed" }
                expect(two !in listed) { "never-opened partition listed" }
            } finally {
                app.close()
                a.close()
            }
        },
        "destroy deletes an open partition and leaves others" to { f ->
            val a = f.open(one)
            val b = f.open(two)
            a.entries.put(entry(key("auth", "me"), byteArrayOf(1)))
            b.entries.put(entry(key("auth", "me"), byteArrayOf(2)))
            f.destroy(one)
            expect(one !in f.listPartitions()) { "destroyed partition still listed" }
            val reopened = f.open(one)
            try {
                expect(reopened.entries.get(key("auth", "me")) == null) { "data survived destroy" }
                expectEqual(byteArrayOf(2).toList(), b.entries.get(key("auth", "me"))?.payload?.toList(), "neighbour")
            } finally {
                reopened.close()
                b.close()
            }
        },
        "destroy of an absent partition is a no-op" to { f ->
            f.destroy(two)
            expect(two !in f.listPartitions()) { "absent partition listed after destroy" }
        },
        "close then reopen keeps data exactly when the factory is durable" to { f ->
            val first = f.open(one)
            first.entries.put(entry(key("auth", "me"), byteArrayOf(9)))
            first.close()
            val second = f.open(one)
            try {
                val kept = second.entries.get(key("auth", "me")) != null
                expectEqual(f.durable, kept, "data kept after close (durable=${f.durable})")
            } finally {
                second.close()
            }
        },
    )

    private suspend fun seedPrefixFixture(s: CacheStore) {
        s.put(entry(key("chat", "conv"), byteArrayOf(7)))
        s.put(entry(key("chat", "conv", "{\"page\":2}"), byteArrayOf(8)))
        s.put(entry(key("chat", "conversations"), byteArrayOf(9)))
        s.put(entry(key("notifications", "feed"), byteArrayOf(10)))
    }

    private suspend fun withStore(f: CacheStoreFactory, block: suspend (CacheStore) -> Unit) {
        val stores = f.open(one)
        try {
            block(stores.entries)
        } finally {
            stores.close()
        }
    }

    /** Encodes segments the way the cache does: each segment followed by U+001F. */
    private fun key(vararg segments: String): String = segments.joinToString("") { "$it\u001F" }

    private fun entry(
        storageKey: String,
        payload: ByteArray,
        fetchedAt: Long = 1_000,
        invalidated: Boolean = false,
        schemaHash: String = "h1",
    ): StoredEntry = StoredEntry(
        storageKey = storageKey,
        namespace = storageKey.substringBefore('\u001F'),
        payload = payload,
        schemaHash = schemaHash,
        fetchedAtMillis = fetchedAt,
        invalidated = invalidated,
    )

    private inline fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    private fun expectEqual(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) throw AssertionError("$what: expected <$expected>, got <$actual>")
    }
}
