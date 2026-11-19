package stapel.conventions

import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Rules for `.github/workflows/ci.yml`:
 * - every push to main gets a finished verdict: its own concurrency group, never cancelled
 *   (GitHub keeps one pending run per group and cancels the older pending one);
 * - a pull request's newer push still supersedes the older run;
 * - `ci-ok` is red unless every needed job succeeded.
 * Each rule is also run against the shape it replaced, which must be caught.
 */
class CiWorkflowTest {
    private val ci = File(System.getProperty("stapel.repoRoot"), ".github/workflows/ci.yml").readText()

    private val push1 = mapOf("github.event_name" to "push", "github.ref" to "refs/heads/main", "github.sha" to "aaa")
    private val push2 = push1 + ("github.sha" to "bbb")
    private val pr1 = mapOf("github.event_name" to "pull_request", "github.ref" to "refs/pull/7/merge", "github.sha" to "ccc")
    private val pr2 = pr1 + ("github.sha" to "ddd")

    @Test
    fun `pushes to main never share a group and are never cancelled`() {
        assertEquals(emptyList(), concurrencyViolations(ci))
    }

    @Test
    fun `the replaced per-ref group is caught`() {
        val old = """
            concurrency:
              group: ci-${'$'}{{ github.ref }}
              cancel-in-progress: ${'$'}{{ github.event_name == 'pull_request' }}
        """.trimIndent()
        assertTrue(concurrencyViolations(old).any { "share" in it })
        val alwaysCancel = """
            concurrency:
              group: ci-${'$'}{{ github.sha }}
              cancel-in-progress: true
        """.trimIndent()
        assertTrue(concurrencyViolations(alwaysCancel).any { "cancel" in it })
    }

    @Test
    fun `ci-ok is green only when every job succeeded`() {
        val script = ciOkScript(ci)
        assertEquals(0, runGate(script, listOf("success", "success", "success", "success")))
        for (bad in listOf("failure", "cancelled", "skipped")) {
            assertNotEquals(0, runGate(script, listOf("success", bad, "success")), bad)
        }
        assertNotEquals(0, runGate(script, emptyList()), "no jobs")
    }

    @Test
    fun `the replaced grep gate is caught passing a failure`() {
        val old = """echo "${'$'}RESULTS" | grep -v -q -E '"(failure|cancelled|skipped)"' || { echo "a required job did not succeed"; exit 1; }"""
        // toJSON is multi-line, so `grep -v` always finds a line without a bad word.
        assertEquals(0, runGate(old, listOf("success", "failure")))
    }

    private fun concurrencyViolations(text: String): List<String> {
        val block = text.substringAfter("\nconcurrency:", text.substringAfter("concurrency:"))
            .lineSequence().drop(1).takeWhile { it.startsWith("  ") || it.isBlank() }.toList()
        val group = block.first { it.trim().startsWith("group:") }.substringAfter("group:").trim()
        val cancel = block.first { it.trim().startsWith("cancel-in-progress:") }.substringAfter(":").trim()
        val out = mutableListOf<String>()
        if (render(group, push1) == render(group, push2)) out += "two pushes to main share a concurrency group"
        if (render(group, pr1) != render(group, pr2)) out += "two pushes to one pull request do not share a group"
        if (render(group, push1) == render(group, pr1)) out += "a push and a pull request share a group"
        if (render(cancel, push1) != "false") out += "a push to main can be cancelled"
        if (render(cancel, pr1) != "true") out += "a pull request run is not cancelled by a newer push"
        return out
    }

    /** Renders a value with `${{ }}` expressions of the shapes this workflow uses. */
    private fun render(value: String, ctx: Map<String, String>): String =
        Regex("""\$\{\{\s*(.+?)\s*}}""").replace(value) { eval(it.groupValues[1], ctx) }

    private fun eval(expr: String, ctx: Map<String, String>): String {
        Regex("""^(\S+) == '([^']*)' && (\S+) \|\| (\S+)$""").matchEntire(expr)?.let { m ->
            val (lhs, lit, a, b) = m.destructured
            return if (ctx.getValue(lhs) == lit) ctx.getValue(a) else ctx.getValue(b)
        }
        Regex("""^(\S+) == '([^']*)'$""").matchEntire(expr)?.let { m ->
            return (ctx.getValue(m.groupValues[1]) == m.groupValues[2]).toString()
        }
        return ctx[expr] ?: error("unsupported expression in ci.yml concurrency: $expr")
    }

    private fun ciOkScript(text: String): String {
        val job = text.substringAfter("\n  ci-ok:")
        val run = job.substringAfter("run: |\n")
        return run.lineSequence().takeWhile { it.startsWith("          ") }.joinToString("\n") { it.trim() }
    }

    /** Runs the gate script with RESULTS rendered like `toJSON(needs.*.result)` (pretty, multi-line). */
    private fun runGate(script: String, results: List<String>): Int {
        val json = if (results.isEmpty()) "[]" else results.joinToString(",\n", "[\n", "\n]") { "  \"$it\"" }
        val p = ProcessBuilder("bash", "-c", script).redirectErrorStream(true).also {
            it.environment()["RESULTS"] = json
        }.start()
        p.inputStream.readAllBytes()
        check(p.waitFor(30, TimeUnit.SECONDS))
        return p.exitValue()
    }
}
