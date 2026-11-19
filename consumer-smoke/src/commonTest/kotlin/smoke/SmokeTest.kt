package smoke

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun resolvesThePublishedResultArtifactByCoordinates() {
        assertEquals("21 HTTP 503", smokeText())
    }
}
