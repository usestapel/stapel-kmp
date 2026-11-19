package stapel.conventions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `scripts/setup-hooks.sh` fails, with the exact install line, until `stapel-exposure-lint` from
 * stapel-tools >= 0.82.0 (the one with `--strict`) is on PATH; `scripts/doctor.sh` carries the same line.
 */
class SetupHooksScriptTest {
    private val root = File(System.getProperty("stapel.repoRoot"))
    private val installLine = "uv tool install 'stapel-tools>=0.82.0'"

    @TempDir
    lateinit var bin: File

    private fun lint(help: String) {
        File(bin, "stapel-exposure-lint").apply {
            writeText("#!/bin/sh\necho '$help'\n")
            setExecutable(true)
        }
    }

    private fun setupHooks(): Pair<Int, String> {
        val git = listOf("/usr/bin", "/bin", "/usr/local/bin", "/opt/homebrew/bin").filter { File(it, "git").exists() }
        val p = ProcessBuilder("sh", File(root, "scripts/setup-hooks.sh").path).redirectErrorStream(true).also {
            it.environment()["PATH"] = (listOf(bin.path) + git + listOf("/usr/bin", "/bin")).distinct().joinToString(":")
        }.start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(30, TimeUnit.SECONDS))
        return p.exitValue() to out
    }

    @Test
    fun `no lint on PATH fails with the install line`() {
        val (code, out) = setupHooks()
        assertNotEquals(0, code, out)
        assertTrue(installLine in out, out)
    }

    @Test
    fun `a lint older than 0_82 (no --strict) fails with the install line`() {
        lint("usage: stapel-exposure-lint [--pushed SHA]")
        val (code, out) = setupHooks()
        assertNotEquals(0, code, out)
        assertTrue(installLine in out, out)
    }

    @Test
    fun `a current lint passes`() {
        lint("usage: stapel-exposure-lint [--strict] [--pushed SHA]")
        val (code, out) = setupHooks()
        assertEquals(0, code, out)
    }

    @Test
    fun `doctor fails without the lint and prints the same line`() {
        val doctor = File(root, "scripts/doctor.sh").readText()
        val block = doctor.substringAfter("if command -v stapel-exposure-lint").substringBefore("\nfi\n")
        assertTrue("bad \"stapel-exposure-lint not on PATH" in block && "\$LINT_FIX" in block, block)
        assertTrue(installLine in doctor, doctor)
    }
}
