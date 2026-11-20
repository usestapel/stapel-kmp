package stapel.cache.testing

import kotlin.coroutines.cancellation.CancellationException
import stapel.cache.CacheStoreFactory
import stapel.cache.ListStore
import stapel.cache.PartitionId
import stapel.cache.StoredListHead
import stapel.cache.StoredListItem

/**
 * The behaviour every [CacheStoreFactory]'s [ListStore]s must show (lists and cursors). The memory
 * store and every durable store run the same suite, so revision collections and paged windows behave
 * the same on both (degrade parity).
 *
 * ```
 * @Test fun lists() = runTest { ListStoreContract.verify { MyStoreFactory(tempDir()) } }
 * ```
 * A broken rule is reported as one [AssertionError] naming every failed case.
 */
public object ListStoreContract {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "$name: unexpected ${e::class.simpleName}: ${e.message}"
            } finally {
                try {
                    factory.listPartitions().forEach { factory.destroy(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Cleanup of a failed case; the case's own failure is already recorded.
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "ListStoreContract: ${failures.size} of ${cases.size} cases failed:\n" +
                    failures.joinToString("\n") { "  - $it" },
            )
        }
        return cases.size
    }

    private val one = PartitionId.account("list-contract-one")
    private val two = PartitionId.account("list-contract-two")

    private val cases: List<Pair<String, suspend (CacheStoreFactory) -> Unit>> = listOf(
        "absent list reads nothing" to { f ->
            withLists(f) { s ->
                expectEqual(null, s.head(key("a", "b")), "head")
                expectEqual(emptyList<StoredListItem>(), s.items(key("a", "b")), "items")
                expectEqual(null, s.readCursor(key("a", "b")), "cursor")
            }
        },
        "replace round-trips the head and the rows in order" to { f ->
            withLists(f) { s ->
                val k = key("feed", "inbox")
                val h = head(k, envelope = byteArrayOf(1, -2, 3), next = "a-9", prev = "a-1", hasNext = true, hasPrev = true, fetchedAt = 77, invalidated = true)
                val rows = listOf(item("c", "3"), item("a", "1"), item("b", "2"), item("empty", ""))
                s.replace(h, rows)
                expectEqual(h, s.head(k), "head")
                expectEqual(rows, s.items(k), "rows in window order")
            }
        },
        "replace drops the rows it does not name" to { f ->
            withLists(f) { s ->
                val k = key("feed", "inbox")
                s.replace(head(k), listOf(item("a", "1"), item("b", "2"), item("c", "3")))
                s.replace(head(k, fetchedAt = 2), listOf(item("d", "4"), item("b", "2'")))
                expectEqual(listOf(item("d", "4"), item("b", "2'")), s.items(k), "rows after the second replace")
                expectEqual(2L, s.head(k)?.fetchedAtMillis, "head replaced")
            }
        },
        "a duplicate id in replace keeps its first position with the later payload" to { f ->
            withLists(f) { s ->
                val k = key("feed", "dup")
                s.replace(head(k), listOf(item("a", "1"), item("b", "2"), item("a", "1'")))
                expectEqual(listOf(item("a", "1'"), item("b", "2")), s.items(k), "rows")
            }
        },
        "merge updates in place, appends new ids in order and removes ids" to { f ->
            withLists(f) { s ->
                val k = key("cat", "tree")
                s.replace(head(k), listOf(item("a", "1"), item("b", "2"), item("c", "3")))
                s.merge(head(k, fetchedAt = 9), listOf(item("x", "new1"), item("b", "2'"), item("y", "new2")), listOf("a", "absent"))
                expectEqual(listOf(item("b", "2'"), item("c", "3"), item("x", "new1"), item("y", "new2")), s.items(k), "rows")
                expectEqual(9L, s.head(k)?.fetchedAtMillis, "head replaced")
                s.merge(head(k, fetchedAt = 10), listOf(item("z", "z")), emptyList())
                expectEqual(listOf("b", "c", "x", "y", "z"), s.items(k).map { it.itemId }, "append after an earlier merge")
            }
        },
        "merge on an absent key creates the list" to { f ->
            withLists(f) { s ->
                val k = key("cat", "new")
                s.merge(head(k), listOf(item("a", "1")), listOf("gone"))
                expectEqual(listOf(item("a", "1")), s.items(k), "rows")
                expect(s.head(k) != null) { "no head after merge" }
            }
        },
        "many appends keep their order" to { f ->
            withLists(f) { s ->
                val k = key("feed", "long")
                s.replace(head(k), (0 until 50).map { item("r$it", "$it") })
                s.merge(head(k), (50 until 120).map { item("r$it", "$it") }, emptyList())
                s.merge(head(k), (120 until 200).map { item("r$it", "$it") }, listOf("r0", "r199"))
                expectEqual((1 until 199).map { "r$it" }, s.items(k).map { it.itemId }, "ids in order")
            }
        },
        "lists with the same item ids do not mix" to { f ->
            withLists(f) { s ->
                s.replace(head(key("a", "1")), listOf(item("x", "in-one")))
                s.replace(head(key("a", "2")), listOf(item("x", "in-two")))
                s.merge(head(key("a", "2")), emptyList(), listOf("x"))
                expectEqual(listOf(item("x", "in-one")), s.items(key("a", "1")), "first list")
                expectEqual(emptyList<StoredListItem>(), s.items(key("a", "2")), "second list")
            }
        },
        "delete removes one list with its cursor and leaves neighbours" to { f ->
            withLists(f) { s ->
                s.replace(head(key("a", "1")), listOf(item("x", "1")))
                s.replace(head(key("a", "1", "child")), listOf(item("y", "2")))
                s.writeCursor(key("a", "1"), 5)
                s.writeCursor(key("a", "1", "child"), 6)
                s.delete(key("a", "1"))
                s.delete(key("a", "absent"))
                expectEqual(null, s.head(key("a", "1")), "deleted head")
                expectEqual(emptyList<StoredListItem>(), s.items(key("a", "1")), "deleted rows")
                expectEqual(null, s.readCursor(key("a", "1")), "deleted cursor")
                expectEqual(listOf(item("y", "2")), s.items(key("a", "1", "child")), "child list")
                expectEqual(6L, s.readCursor(key("a", "1", "child")), "child cursor")
            }
        },
        "markInvalidated matches lists by segment prefix only" to { f ->
            withLists(f) { s ->
                seed(s)
                expectEqual(2, s.markInvalidated(key("chat", "conv")), "matched count")
                expect(s.head(key("chat", "conv"))!!.invalidated) { "exact list not invalidated" }
                expect(s.head(key("chat", "conv", "p2"))!!.invalidated) { "child list not invalidated" }
                expect(!s.head(key("chat", "conversations"))!!.invalidated) { "sibling with a longer segment invalidated" }
                expectEqual(listOf(item("1", "c")), s.items(key("chat", "conv")), "rows kept")
            }
        },
        "deletePrefix removes lists and cursors by segment prefix only" to { f ->
            withLists(f) { s ->
                seed(s)
                s.writeCursor(key("chat", "conv"), 1)
                s.writeCursor(key("chat", "conversations"), 2)
                expectEqual(2, s.deletePrefix(key("chat", "conv")), "deleted count")
                expectEqual(null, s.head(key("chat", "conv", "p2")), "child list survived")
                expectEqual(null, s.readCursor(key("chat", "conv")), "cursor survived its list")
                expectEqual(2L, s.readCursor(key("chat", "conversations")), "sibling cursor")
                expect(s.head(key("chat", "conversations")) != null) { "sibling list deleted" }
            }
        },
        "keys lists by segment prefix only" to { f ->
            withLists(f) { s ->
                seed(s)
                expectEqual(setOf(key("chat", "conv"), key("chat", "conv", "p2")), s.keys(key("chat", "conv")).toSet(), "keys")
                expectEqual(4, s.keys("").size, "every key")
            }
        },
        "cursors round-trip and are replaced" to { f ->
            withLists(f) { s ->
                s.writeCursor(key("cat", "tree"), 41)
                s.writeCursor(key("cat", "tree"), Long.MAX_VALUE)
                s.writeCursor(key("rt", "seq"), -3)
                expectEqual(Long.MAX_VALUE, s.readCursor(key("cat", "tree")), "replaced cursor")
                expectEqual(-3L, s.readCursor(key("rt", "seq")), "negative cursor")
            }
        },
        "non-ASCII keys round-trip and prefix-match" to { f ->
            withLists(f) { s ->
                val cyr = key("каталог", "дерево")
                val emoji = key("каталог", "🚀")
                s.replace(head(cyr), listOf(item("ид-1", "один")))
                s.replace(head(emoji), listOf(item("🚀", "x")))
                expectEqual(listOf(item("ид-1", "один")), s.items(cyr), "cyrillic rows")
                expectEqual(2, s.markInvalidated(key("каталог")), "namespace prefix")
                expectEqual(1, s.deletePrefix(key("каталог", "🚀")), "supplementary prefix")
            }
        },
        "clear removes every list and cursor" to { f ->
            withLists(f) { s ->
                seed(s)
                s.writeCursor(key("cat", "tree"), 3)
                s.clear()
                expectEqual(0, s.keys("").size, "keys after clear")
                expectEqual(null, s.readCursor(key("cat", "tree")), "cursor after clear")
            }
        },
        "partitions are isolated" to { f ->
            val a = f.open(one)
            val b = f.open(two)
            try {
                a.lists.replace(head(key("cat", "tree")), listOf(item("x", "1")))
                a.lists.writeCursor(key("cat", "tree"), 9)
                expectEqual(null, b.lists.head(key("cat", "tree")), "list leaked")
                expectEqual(null, b.lists.readCursor(key("cat", "tree")), "cursor leaked")
                expectEqual(listOf(item("x", "1")), a.lists.items(key("cat", "tree")), "own rows")
            } finally {
                a.close()
                b.close()
            }
        },
        "a partition holding only a list is listed and destroyed with it" to { f ->
            val a = f.open(one)
            a.lists.replace(head(key("cat", "tree")), listOf(item("x", "1")))
            a.lists.writeCursor(key("cat", "tree"), 9)
            expect(one in f.listPartitions()) { "partition with a list not listed" }
            f.destroy(one)
            val reopened = f.open(one)
            try {
                expectEqual(null, reopened.lists.head(key("cat", "tree")), "list survived destroy")
                expectEqual(null, reopened.lists.readCursor(key("cat", "tree")), "cursor survived destroy")
            } finally {
                reopened.close()
            }
        },
        "close then reopen keeps lists and cursors exactly when the factory is durable" to { f ->
            val first = f.open(one)
            first.lists.replace(head(key("cat", "tree")), listOf(item("b", "2"), item("a", "1")))
            first.lists.writeCursor(key("cat", "tree"), 12)
            first.close()
            val second = f.open(one)
            try {
                val rows = second.lists.items(key("cat", "tree"))
                if (f.durable) {
                    expectEqual(listOf(item("b", "2"), item("a", "1")), rows, "rows after reopen")
                    expectEqual(12L, second.lists.readCursor(key("cat", "tree")), "cursor after reopen")
                } else {
                    expectEqual(emptyList<StoredListItem>(), rows, "a non-durable store kept rows")
                    expectEqual(null, second.lists.readCursor(key("cat", "tree")), "a non-durable store kept the cursor")
                }
            } finally {
                second.close()
            }
        },
    )

    private suspend fun seed(s: ListStore) {
        s.replace(head(key("chat", "conv")), listOf(item("1", "c")))
        s.replace(head(key("chat", "conv", "p2")), listOf(item("2", "c2")))
        s.replace(head(key("chat", "conversations")), listOf(item("3", "cs")))
        s.replace(head(key("notifications", "feed")), listOf(item("4", "n")))
    }

    private suspend fun withLists(f: CacheStoreFactory, block: suspend (ListStore) -> Unit) {
        val stores = f.open(one)
        try {
            block(stores.lists)
        } finally {
            stores.close()
        }
    }

    /** Encodes segments the way the cache does: each segment followed by U+001F. */
    private fun key(vararg segments: String): String = segments.joinToString("") { "$it\u001F" }

    private fun head(
        storageKey: String,
        envelope: ByteArray = ByteArray(0),
        next: String? = null,
        prev: String? = null,
        hasNext: Boolean = false,
        hasPrev: Boolean = false,
        fetchedAt: Long = 1_000,
        invalidated: Boolean = false,
    ): StoredListHead = StoredListHead(
        storageKey = storageKey,
        namespace = storageKey.substringBefore('\u001F'),
        envelope = envelope,
        nextAnchor = next,
        prevAnchor = prev,
        hasNext = hasNext,
        hasPrev = hasPrev,
        schemaHash = "h1",
        fetchedAtMillis = fetchedAt,
        invalidated = invalidated,
    )

    private fun item(id: String, payload: String): StoredListItem = StoredListItem(id, payload.encodeToByteArray())

    private inline fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    private fun expectEqual(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) throw AssertionError("$what: expected <$expected>, got <$actual>")
    }
}
