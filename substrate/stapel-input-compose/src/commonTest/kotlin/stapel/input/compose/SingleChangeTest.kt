package stapel.input.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class SingleChangeTest {
    private fun change(before: String, after: String, cursor: Int): List<Int> =
        singleChange(before, after, cursor).let { listOf(it.start, it.end, it.insertedStart, it.insertedEnd) }

    @Test
    fun insertionEndsAtTheCursor() {
        assertEquals(listOf(2, 2, 2, 3), change("12", "123", 3))
        assertEquals(listOf(0, 0, 0, 1), change("55", "555", 1), "typing 5 at the start of 55")
        assertEquals(listOf(1, 1, 1, 2), change("55", "555", 2))
    }

    @Test
    fun deletionAndReplacement() {
        assertEquals(listOf(1, 2, 1, 1), change("123", "13", 1))
        assertEquals(listOf(0, 3, 0, 2), change("123", "ab", 2))
        assertEquals(listOf(1, 3, 1, 4), change("1234", "1xyz4", 4))
    }

    @Test
    fun outOfRangeCursorIsClamped() {
        assertEquals(listOf(2, 2, 2, 3), change("12", "123", 99))
        assertEquals(listOf(0, 0, 0, 1), change("12", "x12", -5))
    }
}
