package stapel.probe.room

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeRowTest {
    @Test
    fun rowKeepsText() {
        assertEquals("a", ProbeNoteRow(1, "a").text)
    }
}
