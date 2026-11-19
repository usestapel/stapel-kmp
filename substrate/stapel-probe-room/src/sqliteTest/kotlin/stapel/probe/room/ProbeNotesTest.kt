package stapel.probe.room

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeNotesTest {
    @Test
    fun storesAndReadsOnEveryTarget() = runTest {
        val notes = ProbeNotes()
        notes.add(2, "world")
        notes.add(1, "hello")
        assertEquals(listOf("hello", "world"), notes.texts())
        notes.close()
    }
}
