package stapel.tools.codegen.cli

import stapel.tools.codegen.ContractParser
import stapel.tools.codegen.ContractSet
import stapel.tools.codegen.GenerationFailed
import stapel.tools.codegen.Generator
import stapel.tools.codegen.Pins
import stapel.tools.codegen.parseJsonObject
import stapel.tools.codegen.prettyJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

private const val USAGE = """stapel-codegen — generated Kotlin API clients from pinned Stapel contracts

  gen [--check] --module stapel-<brick> --brick <brick> --repo-root <dir>
      [--accept-renames] [--update-ratchet] [--contracts <dir>] [--out <generated root>] [--test-out <dir>]
  inspect <contract dir | schema.json>
  pins sync --brick <brick> --module stapel-<brick> --repo <git dir> --repo-url <url> --tag <tag>
      [--repo-root <dir>] [--file <vendored>=<source> ...]
  pins check --brick <brick> [--repo <git dir>] [--repo-root <dir>]
  smoke --src <dir of brick git repos> [--ref <rev>]"""

/** Exit code of a run; `main` maps it to the process status. */
fun run(args: List<String>, out: Appendable = System.out, err: Appendable = System.err): Int {
    if (args.isEmpty()) {
        err.appendLine(USAGE)
        return 2
    }
    return try {
        when (args[0]) {
            "gen" -> gen(Args(args.drop(1)), out, err)
            "inspect" -> inspect(args.drop(1), out)
            "pins" -> pins(args.drop(1), out, err)
            "smoke" -> Smoke.run(Args(args.drop(1)), out)
            else -> {
                err.appendLine(USAGE)
                2
            }
        }
    } catch (e: GenerationFailed) {
        err.appendLine(e.message)
        1
    } catch (e: IllegalArgumentException) {
        err.appendLine("stapel-codegen: ${e.message}")
        err.appendLine(USAGE)
        2
    }
}

fun main(args: Array<String>) {
    exitProcess(run(args.toList()))
}

internal class Args(raw: List<String>) {
    private val values = mutableMapOf<String, MutableList<String>>()
    private val flags = mutableSetOf<String>()
    val positional = mutableListOf<String>()

    init {
        var i = 0
        while (i < raw.size) {
            val a = raw[i]
            if (a.startsWith("--")) {
                val name = a.removePrefix("--")
                if (name in FLAGS) flags += name
                else {
                    require(i + 1 < raw.size) { "--$name needs a value" }
                    values.getOrPut(name) { mutableListOf() } += raw[i + 1]
                    i++
                }
            } else {
                positional += a
            }
            i++
        }
    }

    fun flag(name: String): Boolean = name in flags
    fun opt(name: String): String? = values[name]?.last()
    fun all(name: String): List<String> = values[name].orEmpty()
    fun req(name: String): String = opt(name) ?: throw IllegalArgumentException("--$name is required")

    companion object {
        val FLAGS = setOf("check", "accept-renames", "update-ratchet")
    }
}

private fun gen(a: Args, out: Appendable, err: Appendable): Int {
    val brick = a.req("brick")
    val module = a.req("module")
    require(module == "stapel-$brick") { "--module $module does not match --brick $brick" }
    val root = Paths.get(a.req("repo-root")).toAbsolutePath()
    val contracts = a.opt("contracts")?.let(Paths::get) ?: root.resolve("contracts/$brick")
    val target = a.opt("out")?.let(Paths::get) ?: root.resolve("bricks/$brick/domain/src/commonMain/generated")
    val testTarget = a.opt("test-out")?.let(Paths::get) ?: target.parent.parent.resolve("commonTest/generated")
    val existing = java.util.TreeMap(Generator.existingFiles(target))
    Generator.existingFiles(testTarget).forEach { (k, v) -> existing[Generator.TEST_PREFIX + k] = v }
    val previousLock = existing[Generator.LOCK]?.let(::parseJsonObject)
    val generation = Generator(brick, contracts, acceptRenames = a.flag("accept-renames")).run(previousLock)
    val set = ContractSet.load(brick, contracts)
    val (ratchetProblems, nextRatchet) = Generator.ratchet(set, generation.report, existing[Generator.REPORT], a.flag("update-ratchet"))

    if (a.flag("check")) {
        val diff = Generator.diff(existing, generation.files)
        if (diff.isNotEmpty() || ratchetProblems.isNotEmpty()) {
            err.appendLine("stapel-codegen gen --check: $module is not what its pinned contract generates.")
            diff.forEach { err.appendLine("  $it") }
            ratchetProblems.forEach { err.appendLine("  ratchet: $it") }
            err.appendLine("Regenerate with ./gradlew :$module:stapelGenerate and commit the result.")
            return 1
        }
        out.appendLine("stapel-codegen: $module is up to date (${generation.files.size} files, ${generation.model.operations.size} operations).")
        return 0
    }
    if (ratchetProblems.isNotEmpty()) {
        err.appendLine("stapel-codegen gen: ratchet for $brick refused the run; nothing written.")
        ratchetProblems.forEach { err.appendLine("  $it") }
        return 1
    }
    write(target, existing.keys.filterNot { it.startsWith(Generator.TEST_PREFIX) }.toSet(), generation.files.filterKeys { !it.startsWith(Generator.TEST_PREFIX) })
    write(
        testTarget,
        existing.keys.filter { it.startsWith(Generator.TEST_PREFIX) }.map { it.removePrefix(Generator.TEST_PREFIX) }.toSet(),
        generation.files.filterKeys { it.startsWith(Generator.TEST_PREFIX) }.mapKeys { it.key.removePrefix(Generator.TEST_PREFIX) },
    )
    nextRatchet?.let { Files.writeString(contracts.resolve(ContractSet.RATCHET), prettyJson(it)) }
    out.appendLine("stapel-codegen: wrote ${generation.files.size} files for $module (${generation.model.operations.size} operations, ${generation.model.types.size} types).")
    return 0
}

private fun write(target: Path, existing: Set<String>, files: Map<String, String>) {
    for (stale in existing - files.keys) Files.deleteIfExists(target.resolve(stale))
    for ((path, text) in files) {
        val file = target.resolve(path)
        Files.createDirectories(file.parent)
        if (!file.exists() || Files.readString(file) != text) Files.writeString(file, text)
    }
    // Remove directories the run emptied.
    if (target.exists()) {
        Files.walk(target).use { s -> s.sorted(Comparator.reverseOrder()).filter { it.isDirectory() && it != target }.toList() }
            .forEach { d -> Files.list(d).use { if (!it.findAny().isPresent) Files.delete(d) } }
    }
}

private fun inspect(args: List<String>, out: Appendable): Int {
    require(args.size == 1) { "inspect takes one path" }
    var path = Paths.get(args[0])
    var tmp: Path? = null
    if (!path.isDirectory()) {
        tmp = Files.createTempDirectory("stapel-inspect")
        Files.copy(path, tmp.resolve(ContractSet.SCHEMA))
        path = tmp
    }
    val set = ContractSet.load(path.fileName.toString(), path)
    val model = ContractParser(set).parse()
    out.appendLine("operations ${model.operations.size} · types ${model.types.size} · error codes ${model.errors.size} · profile ${model.stamp.profile}")
    model.diagnostics.counts().forEach { (code, n) -> out.appendLine("  $code $n") }
    model.diagnostics.errors.forEach { out.appendLine("  ${it.code} ${it.pointer}: ${it.message}") }
    tmp?.toFile()?.deleteRecursively()
    return if (model.diagnostics.errors.isEmpty()) 0 else 1
}

private fun pins(args: List<String>, out: Appendable, err: Appendable): Int {
    require(args.isNotEmpty()) { "pins needs sync or check" }
    val a = Args(args.drop(1))
    val brick = a.req("brick")
    val root = Paths.get(a.opt("repo-root") ?: ".").toAbsolutePath()
    val dir = root.resolve("contracts/$brick")
    return when (args[0]) {
        "sync" -> {
            val files = a.all("file").takeIf { it.isNotEmpty() }?.associate { it.substringBefore('=') to it.substringAfter('=') }?.toSortedMap()
                ?: Pins.DEFAULT_FILES
            val pin = Pins.sync(Paths.get(a.req("repo")), a.req("repo-url"), a.req("module"), a.req("tag"), dir, files)
            out.appendLine("pinned ${pin.module} ${pin.tag} @ ${pin.ref} (${pin.files.size} files) into $dir")
            0
        }
        "check" -> {
            val problems = Pins.check(dir, a.opt("repo")?.let(Paths::get))
            if (problems.isEmpty()) {
                out.appendLine("pins check: contracts/$brick matches its pin")
                0
            } else {
                problems.forEach { err.appendLine("pins check: $it") }
                1
            }
        }
        else -> throw IllegalArgumentException("unknown pins command ${args[0]}")
    }
}
