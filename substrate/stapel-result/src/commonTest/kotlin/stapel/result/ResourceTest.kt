package stapel.result

import stapel.result.state.ActionAvailability
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.result.state.bothReady
import stapel.result.state.dataOrNull
import stapel.result.state.errorOrNull
import stapel.result.state.firstBlock
import stapel.result.state.isLoading
import stapel.result.state.keepPrevious
import stapel.result.state.map
import stapel.result.state.matchList
import stapel.result.state.requireReady
import stapel.result.state.toResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceTest {
    private val fresh = Freshness.network(100)
    private val failure = TransportFailure.Offline()

    @Test
    fun mapKeepsFreshnessAndPassesOtherArms() {
        assertEquals(Resource.Ready(4, fresh), Resource.Ready(2, fresh).map { it * 2 })
        assertSame(Resource.Loading, (Resource.Loading as Resource<Int>).map { it * 2 })
        assertEquals(Resource.Failed(failure), (Resource.Failed(failure) as Resource<Int>).map { it })
        assertEquals(2, Resource.Ready(2, fresh).dataOrNull())
        assertNull(Resource.Ready(2, fresh).errorOrNull())
        assertTrue(Resource.Loading.isLoading)
    }

    @Test
    fun resultToResource() {
        assertEquals(Resource.Ready(1, fresh), StapelResult.Ok(1).toResource(fresh))
        assertEquals(Resource.Failed(failure), StapelResult.Err(failure).toResource(fresh))
    }

    @Test
    fun bothReadyFailureWinsThenLoading() {
        val older = Freshness(Origin.Disk, 50, stale = true)
        val pair = bothReady(Resource.Ready(1, fresh.copy(refreshing = true)), Resource.Ready("b", older))
        assertIs<Resource.Ready<Pair<Int, String>>>(pair)
        assertEquals(1 to "b", pair.data)
        assertEquals(Origin.Disk, pair.freshness.origin)
        assertEquals(50, pair.freshness.fetchedAtMillis)
        assertTrue(pair.freshness.stale)
        assertTrue(pair.freshness.refreshing)
        assertEquals(Resource.Failed(failure), bothReady(Resource.Loading, Resource.Failed(failure)))
        assertEquals(Resource.Loading, bothReady(Resource.Loading, Resource.Ready(1, fresh)))
    }

    @Test
    fun keepPreviousNeverHidesAFailure() {
        val prev = Resource.Ready(listOf(1), fresh)
        val kept = (Resource.Loading as Resource<List<Int>>).keepPrevious(prev)
        assertIs<Resource.Ready<List<Int>>>(kept)
        assertTrue(kept.freshness.refreshing)
        assertEquals(Resource.Failed(failure), (Resource.Failed(failure) as Resource<List<Int>>).keepPrevious(prev))
        assertSame(Resource.Loading, (Resource.Loading as Resource<List<Int>>).keepPrevious(null))
    }

    @Test
    fun matchListSeparatesEmptyFromFailed() {
        fun label(r: Resource<List<Int>>) = r.matchList({ "loading" }, { "failed" }, { "empty" }, { rows, _ -> "rows:${rows.size}" })
        assertEquals("loading", label(Resource.Loading))
        assertEquals("failed", label(Resource.Failed(failure)))
        assertEquals("empty", label(Resource.Ready(emptyList(), fresh)))
        assertEquals("rows:2", label(Resource.Ready(listOf(1, 2), fresh)))
    }

    @Test
    fun actionGate() {
        val loading = requireReady(Resource.Loading)
        assertEquals(ActionAvailability.Blocked(CoreErrorCodes.ActionBlockedLoading), loading)
        val failed = requireReady(Resource.Failed(failure))
        assertIs<ActionAvailability.Blocked>(failed)
        assertEquals(CoreErrorCodes.ActionBlockedLoadFailed, failed.code)
        assertEquals(failure, failed.cause)
        assertEquals(ActionAvailability.Available, requireReady(Resource.Ready(1, fresh)))
        val custom = ActionAvailability.Blocked(ErrorCode("x.blocked"))
        assertEquals(custom, requireReady(Resource.Ready(0, fresh)) { if (it == 0) custom else ActionAvailability.Available })
        assertEquals(custom, firstBlock(ActionAvailability.Available, custom, loading))
        assertEquals(ActionAvailability.Available, firstBlock(ActionAvailability.Available))
    }
}
