@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import stapel.cache.ManagedStapelCache
import stapel.cache.PartitionId
import stapel.cache.QuerySpec
import stapel.cache.ReadPolicy
import stapel.cache.RevisionPage
import stapel.cache.RevisionRequest
import stapel.cache.RevisionSpec
import stapel.cache.SyncReport
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.cache.WipeTarget
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The cache engine over the Room store: the end-to-end durability and wipe proofs. */
class RoomBackedCacheTest {
    private val dirs = mutableListOf<Path>()
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
    private val me = QuerySpec(
        key = CacheKey.of("auth", "me"),
        serializer = String.serializer(),
        schemaHash = "me-v1",
        policy = ReadPolicy.StaleWhileRevalidate(Duration.ZERO, Duration.INFINITE),
    )

    private fun tempDir(): Path {
        val dir = Path(SystemTemporaryDirectory, "stapel-cache-e2e-" + Random.nextLong().toULong().toString(16))
        SystemFileSystem.createDirectories(dir)
        dirs += dir
        return dir
    }

    @AfterTest
    fun cleanUp() {
        dirs.forEach { deleteTree(it) }
    }

    private fun kotlinx.coroutines.test.TestScope.cache(dir: Path): ManagedStapelCache = StapelCache(
        StapelCacheConfig(
            json = Json,
            scope = backgroundScope,
            clock = clock,
            storeFactory = RoomCacheStoreFactory(dir.toString(), RoomCacheOptions(Dispatchers.Default)),
        ),
    )

    @Test
    fun coldStartShowsTheLastKnownValueFromDiskThenRevalidates() = runTest {
        val dir = tempDir()
        val first = cache(dir)
        first.setAccount("account-1")
        assertEquals(StapelResult.Ok("me v1"), first.get(me) { StapelResult.Ok("me v1") })
        // The first process dies here: no close, no flush.

        val second = cache(dir)
        second.setAccount("account-1")
        val emissions = second.observe(me) { StapelResult.Ok("me v2") }.take(2).toList()
        val firstPaint = assertIs<Resource.Ready<String>>(emissions[0])
        assertEquals("me v1", firstPaint.data)
        assertEquals(Origin.Disk, firstPaint.freshness.origin)
        assertEquals("me v2", assertIs<Resource.Ready<String>>(emissions[1]).data)
    }

    @Test
    fun coldStartOfflineKeepsTheLastKnownValueWithTheError() = runTest {
        val dir = tempDir()
        val first = cache(dir)
        first.setAccount("account-1")
        first.get(me) { StapelResult.Ok("me v1") }

        val second = cache(dir)
        second.setAccount("account-1")
        val settled = second.observe(me) { StapelResult.Err(TransportFailure.Offline()) }.take(2).toList().last()
        val ready = assertIs<Resource.Ready<String>>(settled)
        assertEquals("me v1", ready.data)
        assertIs<TransportFailure.Offline>(ready.freshness.refreshError)
    }

    @Test
    fun logoutWipeLeavesNoPartitionDirectoryEvenWithALateResponse() = runTest {
        val dir = tempDir()
        val cache = cache(dir)
        cache.setAccount("account-1")
        cache.get(me) { StapelResult.Ok("me v1") }
        val partitionDir = Path(dir, "stapel-cache", PartitionId.account("account-1").name)
        assertEquals(true, SystemFileSystem.exists(partitionDir), "inversion guard: the partition exists before the wipe")

        val gate = CompletableDeferred<Unit>()
        val late = QuerySpec(CacheKey.of("auth", "sessions"), String.serializer(), "s-v1")
        val job = backgroundScope.launch {
            cache.observe(late) {
                withContext(NonCancellable) { gate.await() }
                StapelResult.Ok("late")
            }.collect { }
        }
        runCurrent()
        cache.wipe(WipeTarget.Account("account-1"))
        gate.complete(Unit)
        runCurrent()
        job.cancel()

        assertFalse(SystemFileSystem.exists(partitionDir), "partition directory exists after logout")
    }

    @Test
    fun aRevisionCollectionSurvivesTheProcessAndSyncsOnlyTheDelta() = runTest {
        val dir = tempDir()
        val spec = RevisionSpec(
            key = CacheKey.of("categories", "tree", scope = CacheScope.App),
            itemSerializer = String.serializer(),
            schemaHash = "c1",
            itemId = { it.substringBefore(':') },
            pageSize = 2,
        )
        val asked = mutableListOf<RevisionRequest>()
        fun server(rows: List<String>, deleted: List<String>, max: Long): suspend (RevisionRequest) -> StapelResult<RevisionPage<String>, TransportFailure> = { r ->
            asked += r
            val page = rows.drop((r.page - 1) * r.pageSize).take(r.pageSize)
            StapelResult.Ok(RevisionPage(page, deleted, r.page * r.pageSize < rows.size, max))
        }
        val first = cache(dir).revisioned(spec, server(listOf("a:Apple", "b:Banana", "c:Cherry"), emptyList(), 3))
        assertEquals(SyncReport(fullSync = true, pages = 2, truncated = false, revision = 3), (first.sync() as StapelResult.Ok).value)
        // The first process dies here: no close, no flush.

        asked.clear()
        val second = cache(dir).revisioned(spec, server(listOf("b:Blueberry", "d:Date"), listOf("a"), 5))
        val cold = assertIs<Resource.Ready<List<String>>>(second.state.first { it !is Resource.Loading })
        assertEquals(listOf("a:Apple", "b:Banana", "c:Cherry"), cold.data)
        assertEquals(Origin.Disk, cold.freshness.origin)
        assertEquals(SyncReport(fullSync = false, pages = 1, truncated = false, revision = 5), (second.sync() as StapelResult.Ok).value)
        assertEquals(3L, asked.single().minRevision)

        val third = cache(dir).revisioned(spec, server(emptyList(), emptyList(), 5))
        assertEquals(listOf("b:Blueberry", "c:Cherry", "d:Date"), (third.state.first { it !is Resource.Loading } as Resource.Ready).data)
    }

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteTree(it) }
        }
        SystemFileSystem.delete(path)
    }
}
