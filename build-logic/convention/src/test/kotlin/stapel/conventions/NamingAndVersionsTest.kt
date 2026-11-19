package stapel.conventions

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamingAndVersionsTest {
    private val root = File("/repo")

    private fun names(path: String) = deriveNames(root, File(root, path), path.substringAfterLast('/'), "stapel")

    @Test
    fun `directory derives artifact and package`() {
        assertEquals(StapelNames("stapel-result", "stapel.result", null), names("substrate/stapel-result"))
        assertEquals(StapelNames("stapel-cache-room", "stapel.cache.room", null), names("substrate/stapel-cache-room"))
        assertEquals(StapelNames("stapel-captcha", "stapel.captcha", null), names("capabilities/stapel-captcha"))
        assertEquals(StapelNames("stapel-auth", "stapel.auth", "auth"), names("bricks/auth/domain"))
        assertEquals(StapelNames("stapel-auth-ui", "stapel.auth.ui", "auth"), names("bricks/auth/ui"))
        assertEquals(StapelNames("stapel-auth-ui-demo", "stapel.auth.ui.demo", "auth"), names("bricks/auth/demo"))
        assertEquals(StapelNames("stapel-auth-biometric", "stapel.auth.biometric", "auth"), names("bricks/auth/adapters/biometric"))
    }

    @Test
    fun `pre-release detection`() {
        listOf("1.11.0-rc02", "0.8.0-RC", "1.13.0-alpha01", "2.5.0-Beta2", "1.0-SNAPSHOT", "6.2.0-M1", "2.4.21-dev-123", "1.0.0-eap")
            .forEach { assertTrue(isPreRelease(it), it) }
        listOf("1.11.0", "2.4.21", "3.6.3", "0.8.0", "18.3.1", "1.12.2", "0.10.16", "4.2.2")
            .forEach { assertFalse(isPreRelease(it), it) }
    }

    @Test
    fun `junit xml counting ignores skipped tests`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        File(dir, "TEST-a.xml").writeText("""<testsuite name="a" tests="3" skipped="1" failures="0" errors="0">""")
        assertEquals(2, countExecutedTests(dir))
    }
}
