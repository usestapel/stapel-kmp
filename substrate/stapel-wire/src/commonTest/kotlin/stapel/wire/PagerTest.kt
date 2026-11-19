package stapel.wire

import kotlinx.coroutines.test.runTest
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class NumberPage(
    override val items: List<Int>,
    override val nextCursor: PageCursor?,
    override val total: Int?,
) : Page<Int> {
    override val prevCursor: PageCursor? get() = null
}

class PagerTest {
    private val pages = mapOf(
        null to NumberPage(listOf(1, 2), PageCursor("2"), 5),
        "2" to NumberPage(listOf(3, 4), PageCursor("3"), 5),
        "3" to NumberPage(listOf(5), null, 5),
    )

    @Test
    fun walksEveryPageThenStopsWithoutRequests() = runTest {
        val asked = mutableListOf<String?>()
        val pager = Pager<Int, StapelError> { cursor ->
            asked += cursor?.text
            StapelResult.Ok(pages.getValue(cursor?.text))
        }
        val rows = mutableListOf<Int>()
        while (pager.hasNext) rows += (pager.next() as StapelResult.Ok).value
        assertEquals(listOf(1, 2, 3, 4, 5), rows)
        assertEquals(listOf(null, "2", "3"), asked)
        assertEquals(5, pager.total)
        assertEquals(StapelResult.Ok(emptyList()), pager.next())
        assertEquals(3, asked.size)
    }

    @Test
    fun aFailureKeepsThePositionSoTheSameCallRetries() = runTest {
        var fail = true
        val asked = mutableListOf<String?>()
        val pager = Pager<Int, StapelError> { cursor ->
            asked += cursor?.text
            if (cursor?.text == "2" && fail) {
                fail = false
                StapelResult.Err(TransportFailure.Offline())
            } else {
                StapelResult.Ok(pages.getValue(cursor?.text))
            }
        }
        pager.next()
        assertIs<StapelResult.Err<*>>(pager.next())
        assertTrue(pager.hasNext)
        assertEquals(listOf(3, 4), (pager.next() as StapelResult.Ok).value)
        assertEquals(listOf(null, "2", "2"), asked)
        pager.reset()
        assertNull(pager.total)
        pager.next()
        assertEquals(null, asked.last())
        assertFalse(asked.isEmpty())
    }
}
