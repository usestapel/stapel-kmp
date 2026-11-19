package stapel.probe.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeTagTest {
    @Test
    fun tagIsStable() {
        assertEquals("stapel.probe.label", PROBE_LABEL_TAG)
    }
}
