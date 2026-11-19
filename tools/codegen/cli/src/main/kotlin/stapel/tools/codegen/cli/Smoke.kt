package stapel.tools.codegen.cli

import stapel.tools.codegen.ContractSet
import stapel.tools.codegen.GenerationFailed
import stapel.tools.codegen.Generator
import stapel.tools.codegen.Pins
import java.nio.file.Files

import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Generation over every brick repository in a directory (bare or not) at a revision: the census smoke
 * of §11. Nothing is committed; the table says what generated and what failed and why.
 */
internal object Smoke {
    fun run(a: Args, out: Appendable): Int {
        val src = Paths.get(a.req("src"))
        val rev = a.opt("ref") ?: "HEAD"
        val keep = a.opt("out")?.let(Paths::get)
        val repos = Files.list(src).use { s -> s.filter { it.isDirectory() }.sorted().toList() }
        out.appendLine("| brick | rev | result | ops | types | files | errors (E-*) | warnings (top) |")
        out.appendLine("|---|---|---|---|---|---|---|---|")
        var failed = 0
        var total = 0
        for (repo in repos) {
            val name = repo.name.removeSuffix(".git").removePrefix("stapel-")
            val tmp = Files.createTempDirectory("stapel-smoke-$name")
            try {
                val sha = runCatching { String(Pins.git(repo, "rev-parse", "--short=10", rev)).trim() }.getOrNull() ?: continue
                var hasSchema = false
                for ((file, source) in Pins.DEFAULT_FILES) {
                    val bytes = runCatching { Pins.git(repo, "show", "$rev:$source") }.getOrNull() ?: continue
                    Files.write(tmp.resolve(file), bytes)
                    if (file == ContractSet.SCHEMA) hasSchema = true
                }
                if (!hasSchema) continue
                total++
                try {
                    val g = Generator(name, tmp).run()
                    val warnings = g.model.diagnostics.counts().entries.sortedByDescending { it.value }.take(4)
                        .joinToString(", ") { "${it.key} ${it.value}" }
                    out.appendLine("| $name | $sha | ok | ${g.model.operations.size} | ${g.model.types.size} | ${g.files.size} | 0 | $warnings |")
                    keep?.let { k -> g.files.forEach { (p, t) -> val f = k.resolve(name).resolve(p); Files.createDirectories(f.parent); Files.writeString(f, t) } }
                } catch (e: GenerationFailed) {
                    failed++
                    val lines = e.message.orEmpty().lines().drop(1).map { it.trim() }
                    val byCode = lines.groupBy { it.substringBefore(' ') }.entries.joinToString("; ") { (code, l) ->
                        "$code ×${l.size}: " + l.first().substringAfter(": ").take(90)
                    }
                    out.appendLine("| $name | $sha | **failed** | | | | ${byCode.replace("|", "\\|")} | |")
                } catch (e: Exception) {
                    failed++
                    out.appendLine("| $name | $sha | **crashed** | | | | ${(e::class.simpleName + ": " + e.message).take(160).replace("|", "\\|")} | |")
                }
            } finally {
                tmp.toFile().deleteRecursively()
            }
        }
        out.appendLine()
        out.appendLine("$total contracts, ${total - failed} generated, $failed failed")
        return 0
    }

}
