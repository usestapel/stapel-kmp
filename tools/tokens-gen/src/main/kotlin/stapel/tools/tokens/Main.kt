package stapel.tools.tokens

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.exitProcess

/** A generation run's outputs: path → content, plus the validation findings. */
public class Generation(
    /** Files to write (absolute paths). */
    public val files: Map<File, String>,
    /** Warnings (documented contrast exceptions), for the log. */
    public val warnings: List<ThemeProblem>,
)

/** Raised when a theme fails validation; carries every problem. */
public class ThemeValidationException(
    /** The problems (errors first). */
    public val problems: List<ThemeProblem>,
) : IllegalStateException(problems.filter { it.severity == ThemeProblem.Severity.Error }.joinToString("\n"))

/** File-level driver of the generator (the CLI and the Gradle tasks call this). */
public object TokensGenerator {
    /** Where `stapel-tokens` keeps its generated sources, relative to the repo root. */
    public const val LIBRARY_OUT: String = "substrate/stapel-tokens/src/commonMain/generated/kotlin/stapel/tokens"

    private fun resource(name: String): String =
        TokensGenerator::class.java.getResource("/stapel/tools/tokens/$name")?.readText()
            ?: error("bundled resource $name missing from the tokens-gen jar")

    private fun json(text: String): JsonObject = JsJson.parse(text).jsonObject

    /** The `(theme, ramps)` a host theme resolves with: merged over [defaultTheme], ramps merged over [standardRamps]. */
    public fun prepare(defaultTheme: JsonObject, standardRamps: JsonObject, host: JsonObject?): Pair<JsonObject, JsonObject> {
        val theme = TokenResolver.merge(defaultTheme, host)
        val ramps = TokenResolver.mergeRamps(standardRamps, theme["ramps"] as? JsonObject ?: JsonObject(emptyMap()))
        return theme to ramps
    }

    /** Validates (throws [ThemeValidationException] on any error) and resolves. */
    public fun validateAndResolve(theme: JsonObject, ramps: JsonObject, source: TokenSource): Pair<ResolvedTokens, List<ThemeProblem>> {
        val problems = TokenResolver.validate(theme, ramps)
        if (problems.any { it.severity == ThemeProblem.Severity.Error }) throw ThemeValidationException(problems)
        return TokenResolver.resolve(theme, ramps, source) to problems
    }

    /**
     * The library run: `tokens-source/` → `StapelDefaultTokens.kt` + `StapelPresetTokens.kt`. Refuses to
     * generate when the Kotlin resolution of the default theme differs from the Node golden
     * `tokens-source/tokens.resolved.json`.
     */
    public fun library(repoRoot: File): Generation {
        val src = File(repoRoot, "tokens-source")
        val defaultTheme = json(File(src, "theme.default.json").readText())
        val standardRamps = json(File(src, "ramps.standard.json").readText())
        val sourceJson = json(File(src, "SOURCE.json").readText())
        val source = TokenSource(sourceJson.getValue("package").jsonPrimitive.content, sourceJson.getValue("version").jsonPrimitive.content)
        val (theme, ramps) = prepare(defaultTheme, standardRamps, null)
        val (resolved, warnings) = validateAndResolve(theme, ramps, source)
        val golden = File(src, "tokens.resolved.json").readText()
        check(TokenResolver.toResolvedJson(resolved) == golden) {
            "tokens-gen: the Kotlin resolution of theme.default.json differs from the Node golden tokens-source/tokens.resolved.json " +
                "(run :tools:tokens-gen:test for the per-case diff)"
        }
        val presets = LinkedHashMap<String, ResolvedTokens>()
        for ((name, ramp) in JsJson.entries(json(File(src, "presets.json").readText()))) {
            if (name.startsWith("_")) continue
            presets[name] = if (ramp is JsonObject) {
                val (t, r) = prepare(defaultTheme, standardRamps, buildJsonObject { put("ramps", buildJsonObject { put("brand", ramp) }) })
                validateAndResolve(t, r, source).first
            } else {
                resolved
            }
        }
        val out = File(repoRoot, LIBRARY_OUT)
        val default = ComposeEmitter.render(resolved, "stapel.tokens", "StapelDefaultTokens", Visibility.Public)
        val presetFile = ComposeEmitter.renderPresets(presets, "stapel.tokens", "StapelPresetTokens", Visibility.Public, "default", "StapelDefaultTokens")
        return Generation(
            mapOf(File(out, "StapelDefaultTokens.kt") to default.toString(), File(out, "StapelPresetTokens.kt") to presetFile.toString()),
            warnings,
        )
    }

    /** The app run: a host `stapel.theme.json` over the bundled default → one Kotlin file (+ optional resolved JSON). */
    public fun app(
        themeFile: File?,
        rampsFile: File?,
        outDir: File,
        packageName: String,
        name: String,
        visibility: Visibility,
        emitResolved: File?,
    ): Generation {
        val sourceJson = json(resource("SOURCE.json"))
        val source = TokenSource(sourceJson.getValue("package").jsonPrimitive.content, sourceJson.getValue("version").jsonPrimitive.content)
        val host = themeFile?.takeIf { it.isFile }?.let { json(it.readText()) }
        val (theme, ramps) = prepare(json(resource("theme.default.json")), json(rampsFile?.readText() ?: resource("ramps.standard.json")), host)
        val (resolved, warnings) = validateAndResolve(theme, ramps, source)
        val file = ComposeEmitter.render(resolved, packageName, name, visibility)
        val files = linkedMapOf(File(outDir, packageName.replace('.', '/') + "/$name.kt") to file.toString())
        emitResolved?.let { files[it] = TokenResolver.toResolvedJson(resolved) }
        return Generation(files, warnings)
    }

    /** Writes [generation], or with [check] compares it with the disk; returns the paths that differ. */
    public fun apply(generation: Generation, check: Boolean): List<File> {
        val diffs = mutableListOf<File>()
        for ((file, content) in generation.files) {
            if (check) {
                if (!file.isFile || file.readText() != content) diffs.add(file)
            } else {
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        return diffs
    }
}

private fun usage(): Nothing {
    System.err.println(
        """
        stapel-tokens-kt library --repo-root <dir> [--check]
        stapel-tokens-kt app [--theme <stapel.theme.json>] [--ramps <file>] --out <dir> --package <pkg> --name <Name>
                             [--visibility public|internal] [--emit-resolved <file>] [--check]
        """.trimIndent(),
    )
    exitProcess(2)
}

/** CLI entry point (`stapel-tokens-kt`). */
public fun main(args: Array<String>) {
    if (args.isEmpty()) usage()
    val flags = mutableMapOf<String, String>()
    var check = false
    var i = 1
    while (i < args.size) {
        when (val a = args[i]) {
            "--check" -> check = true
            else -> if (a.startsWith("--") && i + 1 < args.size) flags[a.removePrefix("--")] = args[++i] else usage()
        }
        i++
    }
    val generation = try {
        when (args[0]) {
            "library" -> TokensGenerator.library(File(flags["repo-root"] ?: usage()))
            "app" -> TokensGenerator.app(
                flags["theme"]?.let(::File), flags["ramps"]?.let(::File), File(flags["out"] ?: usage()),
                flags["package"] ?: usage(), flags["name"] ?: usage(),
                if (flags["visibility"] == "public") Visibility.Public else Visibility.Internal,
                flags["emit-resolved"]?.let(::File),
            )
            else -> usage()
        }
    } catch (e: ThemeValidationException) {
        e.problems.forEach { System.err.println(it) }
        System.err.println("stapel-tokens-kt: ${e.problems.count { it.severity == ThemeProblem.Severity.Error }} validation error(s) (§68)")
        exitProcess(1)
    } catch (e: ThemeException) {
        System.err.println(e.problem)
        exitProcess(1)
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    generation.warnings.forEach { System.err.println(it) }
    val diffs = TokensGenerator.apply(generation, check)
    if (check && diffs.isNotEmpty()) {
        System.err.println("stapel-tokens-kt --check: ${diffs.size} file(s) out of date:\n" + diffs.joinToString("\n") { "  $it" })
        exitProcess(1)
    }
    System.err.println(if (check) "stapel-tokens-kt --check: up to date" else "stapel-tokens-kt: wrote ${generation.files.size} file(s)")
}
