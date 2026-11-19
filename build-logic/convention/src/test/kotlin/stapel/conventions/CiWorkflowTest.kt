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
 * - Linux jobs run for every commit with no job-level group; every macOS job has its own job-level
 *   group shared by all pushes to main, so a newer commit's job supersedes the older one while it is
 *   QUEUED, and never cancels a running one;
 * - `ci-ok` (scripts/ci-verdict.sh) is green only when every needed job succeeded, and tells a
 *   superseded commit (a job cancelled before it started, the branch moved on) from a failed one.
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
    fun `macOS jobs supersede queued older commits and never cancel a running one`() {
        assertEquals(emptyList(), jobConcurrencyViolations(ci))
    }

    @Test
    fun `macOS jobs without a shared group, or with a per-commit group, are caught`() {
        val noGroup = ci.replace(Regex("""(?m)^    concurrency:\n      group: .*\n      cancel-in-progress: .*\n"""), "")
        assertTrue(jobConcurrencyViolations(noGroup).any { "no job-level concurrency" in it })
        val perSha = ci.replace("&& github.ref || 'main' }}", "&& github.ref || github.sha }}")
        assertTrue(jobConcurrencyViolations(perSha).any { "do not share" in it })
        val cancelRunning = ci.replace(
            "      cancel-in-progress: ${'$'}{{ github.event_name == 'pull_request' }}\n",
            "      cancel-in-progress: true\n",
        )
        assertTrue(jobConcurrencyViolations(cancelRunning).any { "cancel a running" in it })
    }

    @Test
    fun `ci-ok is green only when every job succeeded`() {
        assertEquals("GREEN" to 0, verdict(mapOf("fast" to "success", "ios" to "success")))
        for (bad in listOf("failure", "cancelled", "skipped")) {
            val (out, code) = verdict(mapOf("fast" to "success", "ios" to bad), started = setOf("ios"))
            assertNotEquals(0, code, bad)
            assertTrue(out.startsWith("FAILED"), "$bad: $out")
        }
        assertNotEquals(0, verdict(emptyMap()).second, "no jobs")
    }

    @Test
    fun `a commit superseded in the macOS queue is reported superseded, never green`() {
        val needs = mapOf("fast" to "success", "publish-smoke" to "cancelled", "sample" to "skipped")
        val (out, code) = verdict(needs)
        assertNotEquals(0, code)
        assertTrue(out.startsWith("SUPERSEDED by bbb") && "publish-smoke" in out && "sample" in out, out)
    }

    @Test
    fun `a cancelled job is not superseded when it had started, when the commit is still newest, or next to a failure`() {
        val needs = mapOf("fast" to "success", "ios" to "cancelled")
        assertTrue(verdict(needs, started = setOf("ios")).first.startsWith("FAILED"), "started")
        assertTrue(verdict(needs, latest = "aaa").first.startsWith("FAILED"), "newest")
        assertTrue(verdict(needs + ("fast" to "failure")).first.startsWith("FAILED"), "failure")
    }

    @Test
    fun `the replaced grep gate is caught passing a failure`() {
        val old = """echo "${'$'}RESULTS" | grep -v -q -E '"(failure|cancelled|skipped)"' || { echo "a required job did not succeed"; exit 1; }"""
        // toJSON is multi-line, so `grep -v` always finds a line without a bad word.
        val p = ProcessBuilder("bash", "-c", old).redirectErrorStream(true)
            .also { it.environment()["RESULTS"] = "[\n  \"success\",\n  \"failure\"\n]" }.start()
        p.inputStream.readAllBytes()
        check(p.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, p.exitValue())
    }

    @Test
    fun `ci-ok runs the verdict script with the run's jobs`() {
        val job = ci.substringAfter("\n  ci-ok:")
        assertTrue("scripts/ci-verdict.sh" in job && "actions: read" in job && "set -o pipefail" in job, job)
    }

    /** The macOS jobs' job-level concurrency, rendered for two pushes to main and two pull-request pushes. */
    private fun jobConcurrencyViolations(text: String): List<String> {
        val jobsText = text.substringAfter("\njobs:\n")
        val jobs = Regex("""(?m)^  ([a-z0-9-]+):\n""").findAll(jobsText).toList()
        val out = mutableListOf<String>()
        val groups = mutableMapOf<String, String>()
        for ((i, m) in jobs.withIndex()) {
            val id = m.groupValues[1]
            val body = jobsText.substring(m.range.last + 1, if (i + 1 < jobs.size) jobs[i + 1].range.first else jobsText.length)
            val runsOn = Regex("""(?m)^    runs-on: (\S+)""").find(body)?.groupValues?.get(1) ?: continue
            val block = body.substringAfter("\n    concurrency:\n", "").lineSequence().takeWhile { it.startsWith("      ") }.toList()
            val group = block.firstOrNull { it.trim().startsWith("group:") }?.substringAfter("group:")?.trim()
            val cancel = block.firstOrNull { it.trim().startsWith("cancel-in-progress:") }?.substringAfter(":")?.trim()
            if (!runsOn.startsWith("macos")) {
                if (group != null) out += "$id (Linux) has a job-level group: it must run for every commit"
                continue
            }
            if (group == null || cancel == null) { out += "$id (macOS) has no job-level concurrency"; continue }
            if (render(group, push1) != render(group, push2)) out += "$id: two pushes to main do not share a group (no superseding)"
            if (render(group, push1) == render(group, pr1)) out += "$id: a push and a pull request share a group"
            if (render(cancel, push1) != "false") out += "$id: a newer push would cancel a running job"
            groups[id] = render(group, push1)
        }
        if (groups.values.toSet().size != groups.size) out += "two macOS jobs share one group: $groups"
        if (groups.isEmpty()) out += "no macOS job found"
        return out
    }

    /** Runs scripts/ci-verdict.sh; jobs not in [started] were cancelled before a runner took them. */
    private fun verdict(needs: Map<String, String>, started: Set<String> = emptySet(), latest: String = "bbb"): Pair<String, Int> {
        val needsJson = needs.entries.joinToString(",\n", "{\n", "\n}") { "  \"${it.key}\": {\n    \"result\": \"${it.value}\",\n    \"outputs\": {}\n  }" }
        val jobsJson = needs.entries.joinToString(",", "[", "]") { (id, result) ->
            val conclusion = if (result == "skipped") "skipped" else result
            if (id in started) """{"name":"$id (x)","conclusion":"$conclusion","runner_name":"GitHub Actions 1","steps":[{"name":"Set up job"}]}"""
            else """{"name":"$id (x)","conclusion":"$conclusion","runner_name":null,"steps":[]}"""
        }
        val p = ProcessBuilder("bash", File(System.getProperty("stapel.repoRoot"), "scripts/ci-verdict.sh").path)
            .redirectErrorStream(true).also {
                it.environment()["NEEDS"] = if (needs.isEmpty()) "{}" else needsJson
                it.environment()["JOBS"] = jobsJson
                it.environment()["THIS_SHA"] = "aaa"
                it.environment()["LATEST_SHA"] = latest
            }.start()
        val out = p.inputStream.bufferedReader().readText().trim()
        check(p.waitFor(30, TimeUnit.SECONDS))
        return out to p.exitValue()
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

    /** A context key or a quoted literal. */
    private fun operand(token: String, ctx: Map<String, String>): String =
        if (token.startsWith("'") && token.endsWith("'")) token.trim('\'') else ctx.getValue(token)

    private fun eval(expr: String, ctx: Map<String, String>): String {
        Regex("""^(\S+) == '([^']*)' && (\S+) \|\| (\S+)$""").matchEntire(expr)?.let { m ->
            val (lhs, lit, a, b) = m.destructured
            return if (ctx.getValue(lhs) == lit) operand(a, ctx) else operand(b, ctx)
        }
        Regex("""^(\S+) == '([^']*)'$""").matchEntire(expr)?.let { m ->
            return (ctx.getValue(m.groupValues[1]) == m.groupValues[2]).toString()
        }
        return ctx[expr] ?: error("unsupported expression in ci.yml concurrency: $expr")
    }
}
