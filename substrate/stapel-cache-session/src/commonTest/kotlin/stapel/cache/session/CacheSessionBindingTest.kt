@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import stapel.cache.ManagedStapelCache
import stapel.cache.MemoryCacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.QuerySpec
import stapel.cache.ReadPolicy
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.cache.StoredEntry
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.session.RefreshOutcome
import stapel.session.SessionDependencies
import stapel.session.SessionEnd
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenPair
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.StoredSession
import stapel.wire.CacheKey
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class CacheSessionBindingTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
    private val me = QuerySpec(
        CacheKey.of("auth", "me"),
        String.serializer(),
        "me-v1",
        ReadPolicy.StaleWhileRevalidate(Duration.ZERO, Duration.INFINITE),
    )

    private fun jwt(claims: String): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return b64.encode("{\"alg\":\"none\"}".encodeToByteArray()) + "." + b64.encode(claims.encodeToByteArray()) + ".sig"
    }

    private fun tokens(userId: Int) = TokenPair(jwt("{\"user_id\":$userId,\"exp\":4102444800}"), "refresh-$userId")

    private fun TestScope.session(stored: TokenPair? = null): SessionManager = SessionManager(
        SessionDependencies(
            vault = InMemoryTokenVault(stored?.let { StoredSession(it, SessionKind.Authenticated) }),
            scope = backgroundScope,
            clock = clock,
        ),
        refresher = { RefreshOutcome.Transient("offline in tests") },
    )

    private fun cache(scope: CoroutineScope, factory: stapel.cache.CacheStoreFactory): ManagedStapelCache =
        StapelCache(StapelCacheConfig(json = Json, scope = scope, clock = clock, storeFactory = factory))

    @Test
    fun userReadsWaitWhileTheSessionInitializes() = runTest {
        val factory = MemoryCacheStoreFactory()
        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope)
        var fetches = 0
        val out = mutableListOf<Resource<String>>()
        backgroundScope.launch { cache.observe(me) { fetches++; StapelResult.Ok("me") }.collect { out += it } }
        runCurrent()
        assertEquals(listOf<Resource<String>>(Resource.Loading), out)
        assertEquals(0, fetches, "a user read ran before the session was known")

        session.restore()
        runCurrent()
        assertEquals(1, fetches)
        assertEquals("me", (out.last() as Resource.Ready).data)
        assertEquals(listOf(PartitionId.account("7")), factory.listPartitions())
    }

    @Test
    fun coldStartPaintsTheLastKnownValueOfTheStoredAccount() = runTest {
        val factory = MemoryCacheStoreFactory()
        // Previous process: account 7 cached "me v1" (the store handle stays open = still on disk).
        val previous = factory.open(PartitionId.account("7"))
        val seed = cache(backgroundScope, factory)
        seed.setAccount("7")
        seed.write(me, "me v1")

        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        val out = mutableListOf<Resource<String>>()
        backgroundScope.launch { cache.observe(me) { StapelResult.Ok("me v2") }.collect { out += it } }
        runCurrent()
        assertEquals("me v1", (out.first() as Resource.Ready).data, "first paint is not the last known value")
        assertEquals("me v2", (out.last() as Resource.Ready).data)
        previous.close()
    }

    @Test
    fun logoutWipesThePartitionBeforeSignedOutIsPublished() = runTest {
        val factory = KeepingFactory()
        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        cache.write(me, "me v1")
        assertEquals(listOf(PartitionId.account("7")), factory.listPartitions(), "inversion guard")

        var seenAtSignedOut: List<PartitionId>? = null
        backgroundScope.launch {
            session.state.collect { if (it is SessionState.Unauthenticated) seenAtSignedOut = factory.listPartitions() }
        }
        runCurrent()
        session.logout()
        runCurrent()
        assertEquals(emptyList(), seenAtSignedOut, "account data still present when signed-out was published")
        assertNull(cache.peek(me))
    }

    @Test
    fun everySessionEndWipes() = runTest {
        for (cause in listOf(SessionEnd.Expired, SessionEnd.Revoked, SessionEnd.StorageLost)) {
            val factory = KeepingFactory()
            val cache = cache(backgroundScope, factory)
            val session = session(stored = tokens(7))
            val binding = CacheSessionBinding(cache, session, backgroundScope)
            session.restore()
            runCurrent()
            cache.write(me, "me v1")
            assertTrue(session.sessionLost(cause))
            runCurrent()
            assertEquals(emptyList(), factory.listPartitions(), "partition kept after $cause")
            binding.close()
        }
    }

    @Test
    fun signingInAsAnotherAccountSwitchesThePartition() = runTest {
        val factory = KeepingFactory()
        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        cache.write(me, "me-7")

        session.adopt(tokens(8), SessionKind.Authenticated)
        runCurrent()
        assertNull(cache.peek(me), "account 8 sees account 7's data")
        cache.write(me, "me-8")
        // The switch is not a session end: account 7's partition is not wiped.
        assertEquals(setOf(PartitionId.account("7"), PartitionId.account("8")), factory.listPartitions().toSet())
    }

    @Test
    fun orphanPartitionsAreWipedOnFirstSettle() = runTest {
        val factory = MemoryCacheStoreFactory()
        val orphan = factory.open(PartitionId.account("9"))
        orphan.entries.put(StoredEntry("auth\u001Fme\u001F", "auth", byteArrayOf(1), "h", 0, false))
        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        cache.write(me, "me-7")
        assertEquals(listOf(PartitionId.account("7")), factory.listPartitions())
    }

    @Test
    fun noSessionAtStartWipesEveryAccountPartition() = runTest {
        val factory = MemoryCacheStoreFactory()
        factory.open(PartitionId.account("9")).entries.put(StoredEntry("k\u001F", "k", byteArrayOf(1), "h", 0, false))
        val cache = cache(backgroundScope, factory)
        val session = session(stored = null)
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        assertEquals(emptyList(), factory.listPartitions())
        // Signed out: user reads go to the network and are not kept.
        assertEquals(StapelResult.Ok("x"), cache.get(me) { StapelResult.Ok("x") })
        assertEquals(emptyList(), factory.listPartitions())
    }

    @Test
    fun closedBindingNoLongerFollows() = runTest {
        val factory = MemoryCacheStoreFactory()
        val cache = cache(backgroundScope, factory)
        val session = session(stored = tokens(7))
        CacheSessionBinding(cache, session, backgroundScope).close()
        session.restore()
        runCurrent()
        assertEquals(emptyList(), factory.listPartitions())
        assertIs<SessionState.Authenticated>(session.state.value)
    }

    @Test
    fun theAccountIsWhatTheSessionPublishesNotAGuessFromOtherClaims() = runTest {
        val factory = MemoryCacheStoreFactory()
        val cache = cache(backgroundScope, factory)
        // Inversion: a token naming the user only under `sub` opens no account partition.
        val session = session(stored = TokenPair(jwt("{\"sub\":\"abc\",\"exp\":4102444800}"), "r"))
        CacheSessionBinding(cache, session, backgroundScope)
        session.restore()
        runCurrent()
        assertEquals(SessionState.Authenticated(1, null), session.state.value)
        assertEquals(emptyList(), factory.listPartitions())
    }
}

/** Memory storage that keeps a partition across close, like files on disk. */
private class KeepingFactory : stapel.cache.CacheStoreFactory {
    private val inner = MemoryCacheStoreFactory()
    private val open = mutableMapOf<PartitionId, stapel.cache.PartitionStores>()
    override val durable: Boolean get() = true
    override suspend fun open(partition: PartitionId): stapel.cache.PartitionStores =
        open.getOrPut(partition) { inner.open(partition) }.let { stapel.cache.PartitionStores(it.entries, it.lists, close = {}) }
    override suspend fun destroy(partition: PartitionId) {
        open.remove(partition)
        inner.destroy(partition)
    }
    override suspend fun listPartitions(): List<PartitionId> = inner.listPartitions()
}
