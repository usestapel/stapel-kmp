@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.mvi.paging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.wire.Page
import stapel.wire.PageCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private data class Row(val id: Int, val label: String = "r$id")

// The four wire envelopes, shaped the way the codegen maps them onto `Page` (ModelEmitter).

/** `{items, next_anchor, prev_anchor, has_next, has_prev, count}`. */
private data class AnchorEnvelope(override val items: List<Row>, val nextAnchor: String?, val hasNext: Boolean, val count: Int?) : Page<Row> {
    override val nextCursor: PageCursor? get() = if (hasNext) nextAnchor?.let(::PageCursor) else null
    override val prevCursor: PageCursor? get() = null
    override val total: Int? get() = count
}

/** `{results, count, next}`. */
private data class PageNumberEnvelope(val results: List<Row>, val count: Int, val next: String?) : Page<Row> {
    override val items: List<Row> get() = results
    override val nextCursor: PageCursor? get() = next?.let(::PageCursor)
    override val prevCursor: PageCursor? get() = null
    override val total: Int? get() = count
}

/** `{results, pagination{page, has_next, has_previous, total_count}}`. */
private data class RevisionEnvelope(val results: List<Row>, val page: Int, val hasNextPage: Boolean, val totalCount: Int) : Page<Row> {
    override val items: List<Row> get() = results
    override val nextCursor: PageCursor? get() = if (hasNextPage) PageCursor((page + 1).toString()) else null
    override val prevCursor: PageCursor? get() = if (page > 1) PageCursor((page - 1).toString()) else null
    override val total: Int? get() = totalCount
}

/** `{count, limit, offset, results}`. */
private data class LimitOffsetEnvelope(val results: List<Row>, val count: Int, val limit: Int, val offset: Int) : Page<Row> {
    override val items: List<Row> get() = results
    override val nextCursor: PageCursor? get() = if (offset + limit < count) PageCursor((offset + limit).toString()) else null
    override val prevCursor: PageCursor? get() = if (offset > 0) PageCursor(maxOf(offset - limit, 0).toString()) else null
    override val total: Int? get() = count
}

/** A 5-row table served 2 rows per page in [style]; records the cursors asked for. */
private class Server(val style: String, var rows: List<Row> = (1..5).map { Row(it) }) {
    val asked = mutableListOf<String?>()
    var failNext: StapelError? = null
    var gate: CompletableDeferred<Unit>? = null

    suspend fun fetch(cursor: PageCursor?): StapelResult<Page<Row>, StapelError> {
        asked += cursor?.text
        gate?.let { it.await(); gate = null }
        failNext?.let { failNext = null; return StapelResult.Err(it) }
        val size = 2
        val start = when (style) {
            "anchor" -> cursor?.text?.let { a -> rows.indexOfFirst { it.id.toString() == a } } ?: 0
            "page" -> cursor?.text?.substringAfter("page=")?.toInt()?.let { (it - 1) * size } ?: 0
            "revision" -> cursor?.text?.toInt()?.let { (it - 1) * size } ?: 0
            else -> cursor?.text?.toInt() ?: 0
        }
        val slice = rows.drop(start).take(size)
        val more = start + size < rows.size
        val page: Page<Row> = when (style) {
            "anchor" -> AnchorEnvelope(slice, rows.getOrNull(start + size)?.id?.toString(), more, rows.size)
            "page" -> PageNumberEnvelope(slice, rows.size, if (more) "https://api.example/x?page=${start / size + 2}" else null)
            "revision" -> RevisionEnvelope(slice, start / size + 1, more, rows.size)
            else -> LimitOffsetEnvelope(slice, rows.size, size, start)
        }
        return StapelResult.Ok(page)
    }
}

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

class PagingMachineTest {
    private val styles = listOf("anchor", "page", "revision", "offset")

    private fun ids(s: PagingState<Row>) = s.items.map { it.id }

    @Test
    fun everyStyleWalksToTheEndWithoutAnExtraRequest() = runTest {
        for (style in styles) {
            val server = Server(style)
            val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
            m.start()
            runCurrent()
            assertEquals(listOf(1, 2), ids(m.state.value), style)
            assertEquals(AppendStatus.Idle, m.state.value.append, style)
            assertEquals(5, m.state.value.total, style)
            m.loadMore(); runCurrent()
            m.loadMore(); runCurrent()
            assertEquals(listOf(1, 2, 3, 4, 5), ids(m.state.value), style)
            assertEquals(AppendStatus.EndReached, m.state.value.append, style)
            m.loadMore(); runCurrent()
            assertEquals(3, server.asked.size, "$style asked past the end: ${server.asked}")
            assertEquals(null, server.asked.first(), "$style: the first load asks the head")
        }
    }

    @Test
    fun theFirstLoadIsLoadingThenFailedThenRetried() = runTest {
        val server = Server("anchor").apply { failNext = TransportFailure.Offline() }
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start()
        assertEquals(Resource.Loading, m.state.value.content)
        runCurrent()
        assertIs<Resource.Failed>(m.state.value.content)
        assertFalse(m.state.value.canLoadMore)
        m.loadMore(); runCurrent()
        assertEquals(1, server.asked.size, "an append was asked with nothing held")
        m.retry(); runCurrent()
        assertEquals(listOf(1, 2), ids(m.state.value))
    }

    @Test
    fun emptyIsOnlyASuccessfulLoadWithNoRows() = runTest {
        val server = Server("offset", rows = emptyList())
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start()
        assertFalse(m.state.value.isEmpty, "loading is not empty")
        runCurrent()
        assertTrue(m.state.value.isEmpty)
        assertEquals(AppendStatus.EndReached, m.state.value.append)
        val failing = PagingMachine(backgroundScope, FixedClock, { it.id }, Server("offset").apply { failNext = TransportFailure.Offline() }::fetch)
        failing.start(); runCurrent()
        assertFalse(failing.state.value.isEmpty, "a failed load is not empty")
    }

    @Test
    fun aFailedAppendKeepsTheRowsAndRetriesTheSameCursor() = runTest {
        val server = Server("page")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start(); runCurrent()
        server.failNext = TransportFailure.Offline()
        m.loadMore(); runCurrent()
        assertIs<AppendStatus.Failed>(m.state.value.append)
        assertEquals(listOf(1, 2), ids(m.state.value))
        m.loadMore(); runCurrent()
        assertEquals(2, server.asked.size, "loadMore after a failure must wait for retry")
        m.retry(); runCurrent()
        assertEquals(server.asked[1], server.asked[2], "retry asked another cursor")
        assertEquals(listOf(1, 2, 3, 4), ids(m.state.value))
    }

    @Test
    fun appendsAreNotDoubledWhileOneIsInFlight() = runTest {
        val server = Server("anchor")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start(); runCurrent()
        server.gate = CompletableDeferred()
        m.loadMore(); m.loadMore(); m.loadMore()
        runCurrent()
        assertEquals(AppendStatus.Loading, m.state.value.append)
        server.gate!!.complete(Unit); runCurrent()
        assertEquals(2, server.asked.size)
    }

    @Test
    fun aRowAlreadyHeldIsReplacedInPlaceNotDuplicated() = runTest {
        val server = Server("offset")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start(); runCurrent()
        // The table shifted between pages: row 2 comes again, edited, on page 2.
        server.rows = listOf(Row(1), Row(3), Row(2, "edited"), Row(4), Row(5))
        m.loadMore(); runCurrent()
        assertEquals(listOf(1, 2, 4), ids(m.state.value))
        assertEquals("edited", m.state.value.items[1].label)
    }

    @Test
    fun refreshKeepsRowsOnScreenThenReplacesThemOrKeepsThemWithTheError() = runTest {
        val server = Server("revision")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start(); runCurrent()
        m.loadMore(); runCurrent()
        assertEquals(listOf(1, 2, 3, 4), ids(m.state.value))

        server.failNext = TransportFailure.Offline()
        m.refresh()
        val during = assertIs<Resource.Ready<List<Row>>>(m.state.value.content)
        assertTrue(during.freshness.refreshing)
        assertEquals(listOf(1, 2, 3, 4), during.data.map { it.id })
        runCurrent()
        val failed = assertIs<Resource.Ready<List<Row>>>(m.state.value.content)
        assertEquals(listOf(1, 2, 3, 4), failed.data.map { it.id }, "a failed refresh dropped the rows")
        assertIs<TransportFailure.Offline>(failed.freshness.refreshError)
        assertFalse(failed.freshness.refreshing)

        server.rows = (10..12).map { Row(it) }
        m.retry(); runCurrent()
        assertEquals(listOf(10, 11), ids(m.state.value), "a refresh replaces the window with the head page")
        assertEquals(null, (m.state.value.content as Resource.Ready).freshness.refreshError)
        assertEquals(AppendStatus.Idle, m.state.value.append)
    }

    @Test
    fun aRefreshDiscardsTheLateAnswerOfAnAppendInFlight() = runTest {
        val server = Server("anchor")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        m.start(); runCurrent()
        server.gate = CompletableDeferred()
        m.loadMore(); runCurrent()
        val gate = server.gate!!
        server.rows = (20..21).map { Row(it) }
        m.refresh()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(20, 21), ids(m.state.value), "the old append landed on the refreshed list")
        assertEquals(AppendStatus.EndReached, m.state.value.append)
    }

    @Test
    fun heldRowsShowStaleWhileTheHeadRevalidates() = runTest {
        val server = Server("anchor")
        val m = PagingMachine(backgroundScope, FixedClock, { it.id }, server::fetch)
        server.gate = CompletableDeferred()
        m.start(Resource.Ready(listOf(Row(9, "cached")), Freshness(Origin.Disk, 1)))
        runCurrent()
        val shown = assertIs<Resource.Ready<List<Row>>>(m.state.value.content)
        assertEquals(listOf(9), shown.data.map { it.id })
        assertTrue(shown.freshness.stale && shown.freshness.refreshing)
        server.gate!!.complete(Unit); runCurrent()
        val fresh = assertIs<Resource.Ready<List<Row>>>(m.state.value.content)
        assertEquals(listOf(1, 2), fresh.data.map { it.id })
        assertFalse(fresh.freshness.stale || fresh.freshness.refreshing)
        assertEquals(Origin.Network, fresh.freshness.origin)
    }

    @Test
    fun aThrowingFetchIsAFailureNotACrash() = runTest {
        val m = PagingMachine<Row, StapelError>(backgroundScope, FixedClock, { it.id }) { error("boom") }
        m.start(); runCurrent()
        assertIs<Resource.Failed>(m.state.value.content)
    }
}
