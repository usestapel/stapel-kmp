package smoke

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun resolvesThePublishedProbeByCoordinates() {
        assertEquals("consumer-smoke", smokeSlug())
    }
}
