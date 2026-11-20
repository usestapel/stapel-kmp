package stapel.tools.codegen.cli

import stapel.tools.codegen.ContractSet
import stapel.tools.codegen.GenerationFailed
import stapel.tools.codegen.Generator
import stapel.tools.codegen.parseJsonObject
import stapel.tools.codegen.prettyJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

/**
 * `gen` for a module that is not the brick's own domain library: a capability that talks to one
 * brick (`stapel-analytics-collector` over `contracts/analytics`) gets the brick's generated API in
 * its own package and source root. Same pins, ratchet and drift check as `gen`.
 *
 * ```
 * module-gen [--check] --brick <brick> --module <module> --package <api package> --out <generated root> --repo-root <dir>
 * ```
 */
object ModuleGen {
    private const val USAGE =
        "module-gen [--check] --brick <brick> --module <module> --package <api package> --out <generated root> --repo-root <dir> [--update-ratchet]"

    /** Exit code of a run. */
    fun run(args: List<String>, out: Appendable = System.out, err: Appendable = System.err): Int = try {
        val a = Args(args)
        val brick = a.req("brick")
        val module = a.req("module")
        val pkg = a.req("package")
        require(pkg.matches(Regex("stapel(\\.[a-z][a-z0-9]*)+"))) { "--package $pkg is not a stapel package" }
        val root = Paths.get(a.req("repo-root")).toAbsolutePath()
        val contracts = root.resolve("contracts/$brick")
        val target = Paths.get(a.req("out")).let { if (it.isAbsolute) it else root.resolve(it) }
        val testTarget = target.parent.parent.resolve("commonTest/generated")
        val existing = java.util.TreeMap(Generator.existingFiles(target))
        Generator.existingFiles(testTarget).forEach { (k, v) -> existing[Generator.TEST_PREFIX + k] = v }
        val previousLock = existing[Generator.LOCK]?.let(::parseJsonObject)
        val generation = Generator(brick, contracts, apiPackage = pkg).run(previousLock)
        val set = ContractSet.load(brick, contracts)
        val (problems, nextRatchet) = Generator.ratchet(set, generation.report, existing[Generator.REPORT], a.flag("update-ratchet"))
        if (a.flag("check")) {
            val diff = Generator.diff(existing, generation.files)
            if (diff.isNotEmpty() || problems.isNotEmpty()) {
                err.appendLine("stapel-codegen module-gen --check: $module is not what contracts/$brick generates.")
                diff.forEach { err.appendLine("  $it") }
                problems.forEach { err.appendLine("  ratchet: $it") }
                err.appendLine("Regenerate with ./gradlew :$module:stapelGenerate and commit the result.")
                1
            } else {
                out.appendLine("stapel-codegen: $module is up to date (${generation.files.size} files).")
                0
            }
        } else if (problems.isNotEmpty()) {
            err.appendLine("stapel-codegen module-gen: ratchet for $brick refused the run; nothing written.")
            problems.forEach { err.appendLine("  $it") }
            1
        } else {
            writeTree(target, existing.keys.filterNot { it.startsWith(Generator.TEST_PREFIX) }.toSet(), generation.files.filterKeys { !it.startsWith(Generator.TEST_PREFIX) })
            writeTree(
                testTarget,
                existing.keys.filter { it.startsWith(Generator.TEST_PREFIX) }.map { it.removePrefix(Generator.TEST_PREFIX) }.toSet(),
                generation.files.filterKeys { it.startsWith(Generator.TEST_PREFIX) }.mapKeys { it.key.removePrefix(Generator.TEST_PREFIX) },
            )
            nextRatchet?.let { Files.writeString(contracts.resolve(ContractSet.RATCHET), prettyJson(it)) }
            out.appendLine("stapel-codegen: wrote ${generation.files.size} files for $module ($pkg).")
            0
        }
    } catch (e: GenerationFailed) {
        err.appendLine(e.message)
        1
    } catch (e: IllegalArgumentException) {
        err.appendLine("stapel-codegen module-gen: ${e.message}")
        err.appendLine(USAGE)
        2
    }

    /** Process entry point (`stapel.tools.codegen.cli.ModuleGen`). */
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList()))
    }
}

/** Writes [files] under [target], deleting what [existing] had and [files] no longer has, then empty directories. */
internal fun writeTree(target: Path, existing: Set<String>, files: Map<String, String>) {
    for (stale in existing - files.keys) Files.deleteIfExists(target.resolve(stale))
    for ((path, text) in files) {
        val file = target.resolve(path)
        Files.createDirectories(file.parent)
        if (!file.exists() || Files.readString(file) != text) Files.writeString(file, text)
    }
    if (target.exists()) {
        Files.walk(target).use { s -> s.sorted(Comparator.reverseOrder()).filter { it.isDirectory() && it != target }.toList() }
            .forEach { d -> Files.list(d).use { if (!it.findAny().isPresent) Files.delete(d) } }
    }
}
