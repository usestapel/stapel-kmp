package stapel.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Drift gate: the committed shell-templates.json (mirrored by the scaffold) equals the code. */
class ShellTemplatesDriftTest {
    @Test
    fun committedTemplatesMatchTheCode() {
        val file = File("shell-templates.json")
        if (System.getProperty("stapel.shell.writeTemplates") == "true") file.writeText(shellTemplatesJson())
        assertEquals(shellTemplatesJson(), file.readText(), "regenerate shell-templates.json from shellTemplatesJson()")
    }
}
