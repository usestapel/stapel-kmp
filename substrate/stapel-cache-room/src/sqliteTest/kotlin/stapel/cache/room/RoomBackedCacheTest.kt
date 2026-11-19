@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
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
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.cache.WipeTarget
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.CacheKey
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

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteTree(it) }
        }
        SystemFileSystem.delete(path)
    }
}
