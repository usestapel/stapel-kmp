package stapel.probe

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeTest {
    @Test
    fun joinsTrimmedLowercasedPartsAndDropsBlanks() {
        assertEquals("stapel-kmp", probeSlug(" Stapel ", "", "KMP"))
    }
}
