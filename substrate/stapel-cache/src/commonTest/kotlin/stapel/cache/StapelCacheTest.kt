@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import stapel.result.StapelResult
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.CacheScope
import stapel.wire.KeyPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StapelCacheTest {
    private val clock = TestClock()

    @Test
    fun staleWhileRevalidateTurnsStaleExactlyAtFreshFor() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("acc")
        val spec = spec("auth", "me", policy = ReadPolicy.StaleWhileRevalidate(30.seconds, 1.hours))
        cache.write(spec, "old")
        val fetch = FakeFetch()

        clock.advance(30.seconds - 1.milliseconds)
        assertEquals(listOf("R:old"), collect(cache, spec, fetch))
        assertEquals(0, fetch.calls, "fresh entry must not be refetched")

        clock.advance(1.milliseconds)
        assertEquals(listOf("R:old:sr", "R:new"), collect(cache, spec, fetch))
        assertEquals(1, fetch.calls)

        // The refetched value is fresh again for another freshFor window.
        clock.advance(29.seconds)
        assertEquals(listOf("R:new"), collect(cache, spec, fetch))
        assertEquals(1, fetch.calls)
        // ... and gone past retainFor.
        clock.advance(2.hours)
        fetch.fail()
        assertEquals(listOf("L", "F"), collect(cache, spec, fetch))
    }

    @Test
    fun concurrentCollectorsOfOneKeyShareOneFetch() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("acc")
        val spec = spec("auth", "me")
        val fetch = FakeFetch().apply { gate = CompletableDeferred() }
        val seen = List(5) { mutableListOf<String>() }
        val jobs = seen.map { out -> backgroundScope.launch { cache.observe(spec) { fetch.invoke() }.collect { out += it.short() } } }
        runCurrent()
        assertEquals(1, fetch.calls, "five collectors started ${fetch.calls} fetches")
        fetch.gate!!.complete(Unit)
        runCurrent()
        seen.forEach { assertEquals(listOf("L", "R:new"), it) }
        assertEquals(1, fetch.calls)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun distinctKeysFetchIndependently() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("acc")
        val fetch = FakeFetch()
        collect(cache, spec("auth", "me"), fetch)
        collect(cache, spec("auth", "sessions"), fetch)
        assertEquals(2, fetch.calls)
    }

    @Test
    fun invalidationReachesPrefixOnlyAndRefetchesObservedKeys() = runTest {
        val observer = RecordingObserver()
        val cache = newCache(backgroundScope, clock, observer = observer)
        cache.setAccount("acc")
        val conv = spec("chat", "conv")
        val convPage = pageSpec(2)
        val conversations = spec("chat", "conversations")
        val other = spec("notifications", "feed")
        listOf(conv, convPage, conversations, other).forEach { cache.write(it, "old") }

        val fetch = FakeFetch()
        val emissions = mutableListOf<String>()
        val job = backgroundScope.launch { cache.observe(conv) { fetch.invoke() }.collect { emissions += it.short() } }
        runCurrent()
        assertEquals(listOf("R:old"), emissions)

        assertEquals(2, cache.invalidate(KeyPattern.of("chat", "conv")))
        runCurrent()
        assertEquals(listOf("R:old", "R:old:sr", "R:new"), emissions, "observed key must refetch")
        assertEquals(1, fetch.calls)
        assertTrue(cache.peek(convPage)!!.freshness.stale, "argument variant not invalidated")
        assertFalse(cache.peek(conversations)!!.freshness.stale, "sibling with a longer segment invalidated")
        assertFalse(cache.peek(other)!!.freshness.stale, "other namespace invalidated")
        assertTrue(observer.events.any { it is CacheEvent.Invalidated && it.matched == 2 })
        job.cancel()
    }

    @Test
    fun invalidationWithoutRefetchOnlyMarks() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("acc")
        val spec = spec("auth", "me")
        cache.write(spec, "old")
        val fetch = FakeFetch()
        val job = backgroundScope.launch { cache.observe(spec) { fetch.invoke() }.collect { } }
        runCurrent()
        cache.invalidate(KeyPattern.of("auth"), Refetch.None)
        runCurrent()
        assertEquals(0, fetch.calls)
        assertTrue(cache.peek(spec)!!.freshness.stale)
        job.cancel()
    }

    @Test
    fun removeDeletesFromMemoryAndStore() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        cache.setAccount("acc")
        cache.write(spec("chat", "conv"), "a")
        cache.write(spec("chat", "conversations"), "b")
        assertEquals(1, cache.remove(KeyPattern.of("chat", "conv")))
        assertNull(cache.peek(spec("chat", "conv")))
        assertNotNull(cache.peek(spec("chat", "conversations")))
        val stores = factory.open(PartitionId.account("acc"))
        assertNull(stores.entries.get(storageKeyOf(spec("chat", "conv").key)))
    }

    @Test
    fun exactPatternsReachOnlyTheirVariantEvenWhenNotInMemory() = runTest {
        val factory = DurableMemoryFactory()
        val page = ::pageSpec
        val first = newCache(backgroundScope, clock, factory)
        first.setAccount("acc")
        first.write(page(1), "p1")
        first.write(page(2), "p2")
        first.write(spec("chat", "conv"), "plain")

        // A new instance: nothing is in memory, so the store must resolve the exact key itself.
        val second = newCache(backgroundScope, clock, factory)
        second.setAccount("acc")
        assertEquals(1, second.invalidate(KeyPattern.exact(page(2).key), Refetch.None))
        assertEquals(1, second.remove(KeyPattern.exact(page(1).key)))
        assertNull(second.peek(page(1)))
        assertTrue(second.peek(page(2))!!.freshness.stale)
        assertFalse(second.peek(spec("chat", "conv"))!!.freshness.stale, "exact pattern reached a sibling")
        assertEquals("plain", second.peek(spec("chat", "conv"))?.data)
    }

    @Test
    fun schemaMismatchIsAMissAndDeletesTheEntry() = runTest {
        val (served, entryLeft, reason) = readSeeded(storedHash = "old-hash", payload = "\"stored\"", fetchFails = true)
        assertEquals(listOf("L", "F"), served)
        assertFalse(entryLeft)
        assertEquals(MissReason.SchemaMismatch, reason)
    }

    @Test
    fun undecodablePayloadIsAMissAndDeletesTheEntry() = runTest {
        val (served, entryLeft, reason) = readSeeded(storedHash = "h1", payload = "{not json", fetchFails = true)
        assertEquals(listOf("L", "F"), served)
        assertFalse(entryLeft)
        assertEquals(MissReason.Undecodable, reason)
    }

    @Test
    fun matchingHashAndPayloadAreServedAndKept() = runTest {
        // Inversion of the two tests above: the same path with a valid entry serves it from disk.
        val (served, entryLeft, reason) = readSeeded(storedHash = "h1", payload = "\"stored\"")
        assertEquals(listOf("R:stored"), served)
        assertTrue(entryLeft)
        assertNull(reason)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.readSeeded(
        storedHash: String,
        payload: String,
        fetchFails: Boolean = false,
    ): Triple<List<String>, Boolean, MissReason?> {
        val factory = DurableMemoryFactory()
        val spec = spec("auth", "me")
        val storageKey = storageKeyOf(spec.key)
        factory.open(PartitionId.account("acc")).entries.put(
            StoredEntry(storageKey, "auth", payload.encodeToByteArray(), storedHash, clock.millis, false),
        )
        val observer = RecordingObserver()
        val cache = newCache(backgroundScope, clock, factory, observer)
        cache.setAccount("acc")
        // With a failing fetch nothing is rewritten, so a missing entry is the miss's own delete.
        val served = collect(cache, spec, FakeFetch().apply { if (fetchFails) fail() })
        val left = factory.open(PartitionId.account("acc")).entries.get(storageKey)
        val reason = observer.events.filterIsInstance<CacheEvent.Miss>().firstOrNull()?.reason
        return Triple(served, left != null, reason)
    }

    @Test
    fun originSaysWhereTheValueCameFrom() = runTest {
        val factory = DurableMemoryFactory()
        val first = newCache(backgroundScope, clock, factory)
        first.setAccount("acc")
        val spec = spec("auth", "me", policy = ReadPolicy.StaleWhileRevalidate(Duration.ZERO, Duration.INFINITE))
        val fromNetwork = first.observe(spec) { StapelResult.Ok("me") }.firstSettled()
        assertEquals(StapelResult.Ok("me"), fromNetwork)
        assertEquals(Origin.Network, first.peek(spec)!!.freshness.origin)

        val second = newCache(backgroundScope, clock, factory)
        second.setAccount("acc")
        assertEquals(Origin.Disk, second.peek(spec)!!.freshness.origin)
    }

    @Test
    fun memoryStoreIsTheDefaultAndAnnouncesDegrade() = runTest {
        val observer = RecordingObserver()
        val cache = StapelCache(StapelCacheConfig(json = testJson, scope = backgroundScope, clock = clock, observer = observer))
        assertFalse(cache.capabilities.durable)
        assertFalse(cache.capabilities.offlineQueue)
        assertEquals(1, observer.events.count { it is CacheEvent.Degraded })
    }

    @Test
    fun durableFactoryIsUsedOnlyWhenPassed() = runTest {
        val observer = RecordingObserver()
        val cache = newCache(backgroundScope, clock, DurableMemoryFactory(), observer)
        assertTrue(cache.capabilities.durable)
        assertEquals(0, observer.events.count { it is CacheEvent.Degraded })
    }

    @Test
    fun userScopedReadsWaitForTheAccount() = runTest {
        val cache = newCache(backgroundScope, clock)
        val fetch = FakeFetch()
        val emissions = mutableListOf<String>()
        val job = backgroundScope.launch { cache.observe(spec("auth", "me")) { fetch.invoke() }.collect { emissions += it.short() } }
        runCurrent()
        assertEquals(listOf("L"), emissions)
        assertEquals(0, fetch.calls, "a user-scoped read ran before the account was known")
        cache.setAccount("acc")
        runCurrent()
        assertEquals(listOf("L", "R:new"), emissions)
        job.cancel()
    }

    @Test
    fun appScopedReadsDoNotWaitForTheAccount() = runTest {
        val cache = newCache(backgroundScope, clock)
        val fetch = FakeFetch()
        assertEquals(listOf("L", "R:new"), collect(cache, spec("site", "config", scope = CacheScope.App), fetch))
    }

    @Test
    fun anonymousUserReadsGoToTheNetworkAndAreNotKept() = runTest {
        val observer = RecordingObserver()
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory, observer)
        cache.setAccount(null)
        val fetch = FakeFetch()
        assertEquals(listOf("L", "R:new"), collect(cache, spec("auth", "me"), fetch))
        assertEquals(listOf("L", "R:new"), collect(cache, spec("auth", "me"), fetch))
        assertEquals(2, fetch.calls)
        assertTrue(observer.events.any { it is CacheEvent.Miss && it.reason == MissReason.NoPartition })
        assertEquals(emptyList(), factory.listPartitions())
    }

    @Test
    fun accountSwitchIsolatesAndKeepsEachPartition() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        val spec = spec("auth", "me", policy = ReadPolicy.CacheFirst(1.hours))
        cache.setAccount("a")
        cache.write(spec, "me-a")
        cache.setAccount("b")
        assertNull(cache.peek(spec), "account b sees account a's entry")
        cache.write(spec, "me-b")
        cache.setAccount("a")
        assertEquals("me-a", cache.peek(spec)?.data)
    }

    @Test
    fun observersFollowTheAccountSwitch() = runTest {
        val cache = newCache(backgroundScope, clock, DurableMemoryFactory())
        val spec = spec("auth", "me", policy = ReadPolicy.CacheFirst(1.hours))
        cache.setAccount("a")
        cache.write(spec, "me-a")
        val emissions = mutableListOf<String>()
        val job = backgroundScope.launch { cache.observe(spec) { StapelResult.Ok("fetched-b") }.collect { emissions += it.short() } }
        runCurrent()
        cache.setAccount("b")
        runCurrent()
        assertEquals(listOf("R:me-a", "L", "R:fetched-b"), emissions)
        job.cancel()
    }

    @Test
    fun wipeDeletesThePartitionAndRunsHooks() = runTest {
        val factory = DurableMemoryFactory()
        val observer = RecordingObserver()
        val cache = newCache(backgroundScope, clock, factory, observer)
        val hooked = mutableListOf<WipeTarget>()
        cache.addWipeHook { hooked += it }
        runCurrent()
        cache.setAccount("a")
        cache.write(spec("auth", "me"), "me-a")
        cache.write(spec("site", "config", scope = CacheScope.App), "cfg")

        val report = cache.wipe(WipeTarget.Account("a"))

        assertEquals(1, report.partitions)
        assertEquals(listOf(PartitionId.App), factory.listPartitions())
        assertNull(cache.peek(spec("auth", "me")))
        assertEquals("cfg", cache.peek(spec("site", "config", scope = CacheScope.App))?.data, "app partition lost")
        assertEquals(listOf<WipeTarget>(WipeTarget.Account("a")), hooked)
        assertTrue(observer.events.contains(CacheEvent.Wiped(WipeTarget.Account("a"), 1)))
    }

    @Test
    fun wipeEverythingIncludesTheAppPartition() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        cache.setAccount("a")
        cache.write(spec("auth", "me"), "me-a")
        cache.write(spec("site", "config", scope = CacheScope.App), "cfg")
        cache.setAccount("b")
        cache.write(spec("auth", "me"), "me-b")
        assertEquals(2, cache.wipe(WipeTarget.AllAccounts).partitions)
        assertEquals(listOf(PartitionId.App), factory.listPartitions())
        assertEquals(1, cache.wipe(WipeTarget.Everything).partitions)
        assertEquals(emptyList(), factory.listPartitions())
        assertNull(cache.peek(spec("site", "config", scope = CacheScope.App)))
    }

    @Test
    fun wipeAllAccountsExceptKeepsOnlyThatAccountAndTheApp() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        for (account in listOf("a", "b", "c")) {
            cache.setAccount(account)
            cache.write(spec("auth", "me"), "me-$account")
        }
        cache.write(spec("site", "config", scope = CacheScope.App), "cfg")
        assertEquals(2, cache.wipe(WipeTarget.AllAccountsExcept("c")).partitions)
        assertEquals(setOf(PartitionId.App, PartitionId.account("c")), factory.listPartitions().toSet())
        assertEquals("me-c", cache.peek(spec("auth", "me"))?.data)
    }

    @Test
    fun aLateResponseAfterWipeIsNotWritten() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        cache.setAccount("a")
        val gate = CompletableDeferred<Unit>()
        val job = backgroundScope.launch {
            cache.observe(spec("auth", "me")) {
                // A transport that ignores cancellation and answers after the wipe.
                withContext(NonCancellable) { gate.await() }
                StapelResult.Ok("late")
            }.collect { }
        }
        runCurrent()
        cache.wipe(WipeTarget.Account("a"))
        gate.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), factory.listPartitions(), "late response recreated the partition")
        assertEquals(0, factory.lateWrites, "late response was written to the wiped partition's store")
        assertEquals(0, factory.open(PartitionId.account("a")).entries.count())
        job.cancel()
    }

    @Test
    fun neverPersistedValuesStayOutOfTheStoreAndDieWithTheCollector() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        cache.setAccount("a")
        val spec = spec("auth", "totp-secret", persistence = Persistence.Never)
        val job = backgroundScope.launch { cache.observe(spec) { StapelResult.Ok("SECRET") }.collect { } }
        runCurrent()
        assertEquals("SECRET", cache.peek(spec)?.data)
        assertEquals(0, factory.open(PartitionId.account("a")).entries.count(), "sensitive value reached the store")
        job.cancel()
        runCurrent()
        assertNull(cache.peek(spec), "sensitive value outlived its collector")
    }

    @Test
    fun networkOnlyIsNeverStored() = runTest {
        val factory = DurableMemoryFactory()
        val cache = newCache(backgroundScope, clock, factory)
        cache.setAccount("a")
        val spec = spec("auth", "login", policy = ReadPolicy.NetworkOnly)
        collect(cache, spec, FakeFetch())
        assertEquals(0, factory.open(PartitionId.account("a")).entries.count())
    }

    @Test
    fun oversizedValueIsServedButNotPersisted() = runTest {
        val factory = DurableMemoryFactory()
        val observer = RecordingObserver()
        val cache = newCache(backgroundScope, clock, factory, observer, maxEntryBytes = 8)
        cache.setAccount("a")
        val spec = spec("auth", "me")
        assertEquals(listOf("L", "R:a much longer value"), collect(cache, spec, FakeFetch(StapelResult.Ok("a much longer value"))))
        assertEquals(0, factory.open(PartitionId.account("a")).entries.count())
        assertTrue(observer.events.any { it is CacheEvent.NotPersisted && it.reason == NotPersistedReason.TooLarge })
    }

    @Test
    fun updateNeverSeedsAnEntry() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("a")
        val spec = spec("auth", "me")
        assertFalse(cache.update(spec) { "$it!" })
        assertNull(cache.peek(spec))
        cache.write(spec, "me")
        assertTrue(cache.update(spec) { "$it!" })
        assertEquals("me!", cache.peek(spec)?.data)
    }

    @Test
    fun getReturnsTheSettledAnswer() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("a")
        val spec = spec("auth", "me")
        cache.write(spec, "old")
        clock.advance(1.minutes)
        // Stale: get waits for the revalidation instead of returning the refreshing value.
        assertEquals(StapelResult.Ok("new"), cache.get(spec) { StapelResult.Ok("new") })
        val failing = FakeFetch().apply { fail() }
        assertIs<StapelResult.Err<*>>(cache.get(spec("auth", "other")) { failing.invoke() })
    }

    @Test
    fun pollingRefetchesWhileCollected() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("a")
        val fetch = FakeFetch()
        val job = backgroundScope.launch {
            cache.observe(spec("chat", "unread"), ReadOptions(pollEvery = 10.seconds)) { fetch.invoke() }.collect { }
        }
        runCurrent()
        assertEquals(1, fetch.calls)
        advanceTimeBy(10.seconds + 1.milliseconds)
        assertEquals(2, fetch.calls)
        job.cancel()
        advanceTimeBy(1.minutes)
        assertEquals(2, fetch.calls, "polling continued without a collector")
    }

    @Test
    fun foregroundRefetchesStaleObservedKeys() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("a")
        val spec = spec("auth", "me")
        cache.write(spec, "old")
        val fetch = FakeFetch()
        val job = backgroundScope.launch { cache.observe(spec) { fetch.invoke() }.collect { } }
        runCurrent()
        cache.onForeground()
        runCurrent()
        assertEquals(0, fetch.calls, "fresh key refetched on foreground")
        clock.advance(1.minutes)
        cache.onForeground()
        runCurrent()
        assertEquals(1, fetch.calls)
        job.cancel()
    }

    @Test
    fun aThrowingFetchBecomesAFailureNotACrash() = runTest {
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("a")
        val out = mutableListOf<Resource<String>>()
        val job = backgroundScope.launch {
            cache.observe<String, stapel.result.StapelError>(spec("auth", "me")) { error("boom") }.collect { out += it }
        }
        runCurrent()
        assertIs<Resource.Failed>(out.last())
        job.cancel()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.collect(
        cache: StapelCache,
        spec: QuerySpec<String>,
        fetch: FakeFetch,
    ): List<String> {
        val emissions = mutableListOf<String>()
        val job = backgroundScope.launch { cache.observe(spec) { fetch.invoke() }.collect { emissions += it.short() } }
        runCurrent()
        job.cancel()
        return emissions
    }
}
