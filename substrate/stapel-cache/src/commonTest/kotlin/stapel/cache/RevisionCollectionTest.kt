@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import stapel.wire.KeyPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** A revision-paginated table: rows `id:value` with a revision each; deletions are soft. */
private class FakeRevisionServer {
    private class Row(val id: String, var value: String, var revision: Long, var deleted: Boolean = false)

    private val rows = linkedMapOf<String, Row>()
    private var revision = 0L
    val requests = mutableListOf<RevisionRequest>()
    var failOnPage: Int? = null
    var gateOnPage: Pair<Int, CompletableDeferred<Unit>>? = null

    fun put(id: String, value: String) {
        revision++
        val row = rows[id]
        if (row == null) rows[id] = Row(id, value, revision) else { row.value = value; row.revision = revision; row.deleted = false }
    }

    fun delete(id: String) {
        revision++
        rows.getValue(id).apply { deleted = true; this.revision = this@FakeRevisionServer.revision }
    }

    suspend fun fetch(r: RevisionRequest): StapelResult<RevisionPage<String>, StapelError> {
        requests += r
        gateOnPage?.let { (page, gate) -> if (page == r.page) gate.await() }
        if (failOnPage == r.page) return StapelResult.Err(TransportFailure.Offline())
        val inRange = rows.values
            .filter { r.minRevision == null || it.revision > r.minRevision }
            .filter { r.maxRevision == null || it.revision <= r.maxRevision }
            .sortedBy { it.revision }
        val live = inRange.filter { !it.deleted }
        val page = live.drop((r.page - 1) * r.pageSize).take(r.pageSize)
        val deleted = if (r.minRevision == null) emptyList() else inRange.filter { it.deleted }.map { it.id }
        return StapelResult.Ok(RevisionPage(page.map { "${it.id}:${it.value}" }, deleted, r.page * r.pageSize < live.size, revision))
    }
}

class RevisionCollectionTest {
    private val clock = TestClock()
    private val server = FakeRevisionServer()

    private fun spec(
        scope: CacheScope = CacheScope.App,
        hash: String = "h1",
        pageSize: Int = 2,
        maxPages: Int = 100,
    ) = RevisionSpec(
        key = CacheKey.of("categories", "tree", scope = scope),
        itemSerializer = String.serializer(),
        schemaHash = hash,
        itemId = { it.substringBefore(':') },
        pageSize = pageSize,
        maxPages = maxPages,
        policy = ReadPolicy.StaleWhileRevalidate(5.minutes, kotlin.time.Duration.INFINITE),
    )

    private fun rowsOf(r: Resource<List<String>>): List<String>? = (r as? Resource.Ready)?.data

    private suspend fun RevisionCollection<String>.syncOk(): SyncReport = (sync() as StapelResult.Ok).value

    @Test
    fun aFullSyncThenADeltaUpdatesInPlaceAppendsAndDeletes() = runTest {
        listOf("a" to "Apple", "b" to "Banana", "c" to "Cherry").forEach { server.put(it.first, it.second) }
        val cache = newCache(backgroundScope, clock)
        val tree = cache.revisioned(spec(), server::fetch)

        val first = tree.syncOk()
        assertEquals(SyncReport(fullSync = true, pages = 2, truncated = false, revision = 3), first)
        assertEquals(listOf("a:Apple", "b:Banana", "c:Cherry"), rowsOf(cache.revisioned(spec(), server::fetch).state.firstReady()))
        assertEquals(listOf(null, null), server.requests.map { it.minRevision }, "a full sync has no lower bound")

        server.put("b", "Blueberry")
        server.put("d", "Date")
        server.delete("a")
        server.requests.clear()
        val delta = tree.syncOk()
        assertEquals(SyncReport(fullSync = false, pages = 1, truncated = false, revision = 6), delta)
        assertEquals(3L, server.requests.single().minRevision, "the delta asks for changes after the stored revision")
        assertEquals(listOf("b:Blueberry", "c:Cherry", "d:Date"), rowsOf(tree.state.firstReady()))
    }

    @Test
    fun laterPagesArePinnedToTheFirstPagesGlobalMaximum() = runTest {
        (1..5).forEach { server.put("r$it", "v$it") }
        val cache = newCache(backgroundScope, clock)
        cache.revisioned(spec(), server::fetch).syncOk()
        assertEquals(listOf(null, 5L, 5L), server.requests.map { it.maxRevision })
        assertEquals(listOf(1, 2, 3), server.requests.map { it.page })
    }

    @Test
    fun subscribersNeverSeeHalfAWalk() = runTest {
        (1..4).forEach { server.put("r$it", "v$it") }
        val cache = newCache(backgroundScope, clock)
        val tree = cache.revisioned(spec(), server::fetch)
        val gate = CompletableDeferred<Unit>()
        server.gateOnPage = 2 to gate
        val seen = mutableListOf<Resource<List<String>>>()
        val job = backgroundScope.launch { tree.state.collect { seen += it } }
        runCurrent()
        assertEquals(1, server.requests.count { it.page == 1 })
        assertTrue(seen.all { it is Resource.Loading }, "page 1 was published before the walk completed: $seen")
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("r1:v1", "r2:v2", "r3:v3", "r4:v4"), rowsOf(seen.last()))
        job.cancel()
    }

    @Test
    fun aFailedWalkKeepsTheRowsAndTheRevision() = runTest {
        (1..4).forEach { server.put("r$it", "v$it") }
        val cache = newCache(backgroundScope, clock)
        val tree = cache.revisioned(spec(), server::fetch)
        tree.syncOk()
        (5..8).forEach { server.put("r$it", "v$it") }
        server.failOnPage = 2
        assertIs<StapelResult.Err<*>>(tree.sync())
        val after = tree.state.firstReady() as Resource.Ready
        assertEquals((1..4).map { "r$it:v$it" }, after.data, "a failed walk published part of a delta")
        assertNotNull(after.freshness.refreshError)

        server.failOnPage = null
        server.requests.clear()
        tree.syncOk()
        assertEquals(4L, server.requests.first().minRevision, "the revision moved on a failed walk")
        assertEquals((1..8).map { "r$it:v$it" }, rowsOf(tree.state.firstReady()))
    }

    @Test
    fun aTruncatedWalkPublishesNothingAndKeepsTheRevision() = runTest {
        (1..4).forEach { server.put("r$it", "v$it") }
        val cache = newCache(backgroundScope, clock)
        val tree = cache.revisioned(spec(maxPages = 2), server::fetch)
        tree.syncOk()
        (5..10).forEach { server.put("r$it", "v$it") }
        val report = tree.syncOk()
        assertEquals(SyncReport(fullSync = false, pages = 2, truncated = true, revision = 4), report)
        assertEquals((1..4).map { "r$it:v$it" }, rowsOf(tree.state.firstReady()))
        server.requests.clear()
        tree.sync()
        assertEquals(4L, server.requests.first().minRevision)
    }

    @Test
    fun aDurableStoreServesTheLastSnapshotAtColdStartAndSyncsADelta() = runTest {
        val disk = DurableMemoryFactory()
        listOf("a" to "Apple", "b" to "Banana").forEach { server.put(it.first, it.second) }
        newCache(backgroundScope, clock, disk).revisioned(spec(), server::fetch).syncOk()

        server.put("c", "Cherry")
        server.requests.clear()
        val restarted = newCache(backgroundScope, clock, disk).revisioned(spec(), server::fetch)
        val cold = restarted.state.firstReady() as Resource.Ready
        assertEquals(listOf("a:Apple", "b:Banana"), cold.data)
        assertEquals(Origin.Disk, cold.freshness.origin)
        assertEquals(SyncReport(false, 1, false, 3), restarted.syncOk())
        assertEquals(2L, server.requests.single().minRevision, "a cold start re-walked the table")
    }

    @Test
    fun theMemoryStoreSyncsInFullOncePerProcess() = runTest {
        server.put("a", "Apple")
        newCache(backgroundScope, clock).revisioned(spec(), server::fetch).syncOk()
        val nextProcess = newCache(backgroundScope, clock).revisioned(spec(), server::fetch)
        assertTrue(nextProcess.syncOk().fullSync)
        assertEquals(false, nextProcess.syncOk().fullSync, "a second sync in the same process is a delta")
    }

    @Test
    fun anotherSchemaHashDropsTheSnapshotAndItsRevision() = runTest {
        val disk = DurableMemoryFactory()
        server.put("a", "Apple")
        newCache(backgroundScope, clock, disk).revisioned(spec(hash = "h1"), server::fetch).syncOk()
        val observer = RecordingObserver()
        val next = newCache(backgroundScope, clock, disk, observer).revisioned(spec(hash = "h2"), server::fetch)
        assertTrue(next.syncOk().fullSync)
        assertTrue(observer.events.any { it is CacheEvent.Miss && it.reason == MissReason.SchemaMismatch })
    }

    @Test
    fun removeDropsTheRevisionAndInvalidateRevalidatesAnObservedCollection() = runTest {
        server.put("a", "Apple")
        val cache = newCache(backgroundScope, clock, DurableMemoryFactory())
        val tree = cache.revisioned(spec(), server::fetch)
        val seen = mutableListOf<Resource<List<String>>>()
        val job = backgroundScope.launch { tree.state.collect { seen += it } }
        runCurrent()
        assertEquals(listOf("a:Apple"), rowsOf(seen.last()))

        server.put("b", "Banana")
        server.requests.clear()
        assertTrue(cache.invalidate(KeyPattern.of("categories")) >= 1)
        runCurrent()
        assertEquals(1L, server.requests.single().minRevision, "invalidation re-walked instead of a delta")
        assertEquals(listOf("a:Apple", "b:Banana"), rowsOf(seen.last()))

        server.requests.clear()
        assertEquals(1, cache.remove(KeyPattern.of("categories")))
        runCurrent()
        assertNull(server.requests.single().minRevision, "after remove the next sync must be a full one")
        job.cancel()
    }

    @Test
    fun userScopedCollectionsWaitForTheAccountAndStayInTheirPartition() = runTest {
        server.put("a", "Apple")
        val cache = newCache(backgroundScope, clock, DurableMemoryFactory())
        val tree = cache.revisioned(spec(scope = CacheScope.User), server::fetch)
        val seen = mutableListOf<Resource<List<String>>>()
        val job = backgroundScope.launch { tree.state.collect { seen += it } }
        runCurrent()
        assertEquals(listOf<Resource<List<String>>>(Resource.Loading), seen)
        assertEquals(0, server.requests.size, "a user-scoped sync ran before the account was known")

        cache.setAccount("one")
        runCurrent()
        assertEquals(listOf("a:Apple"), rowsOf(seen.last()))
        cache.setAccount("two")
        server.requests.clear()
        runCurrent()
        assertNull(server.requests.first().minRevision, "account two reused account one's revision")
        job.cancel()
    }
}

/** The first [Resource.Ready] or [Resource.Failed] the flow emits. */
private suspend fun <T> kotlinx.coroutines.flow.Flow<Resource<T>>.firstReady(): Resource<T> =
    first { it !is Resource.Loading }
