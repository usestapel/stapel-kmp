package stapel.tools.tokens

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kotlin resolver against the corpus produced by the Node implementation
 * (tokens-source/conformance/produce.mjs): same errors and warnings verbatim, byte-equal
 * `tokens.resolved.json`, the same colours/gutter as the unmodified bin's `tokens.css`, the same
 * `resolved@1` refusals.
 */
class ConformanceTest {
    private val corpus = Corpus.load()

    @TestFactory
    fun everyCaseMatchesNode(): List<DynamicTest> = corpus.cases.map { c ->
        DynamicTest.dynamicTest(c.name) {
            val failures = Corpus.compare(c) { theme, ramps -> TokenResolver.validate(theme, ramps) }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        }
    }

    @Test
    fun corpusCoversWhatItMustCover() {
        val names = corpus.cases.map { it.name }.toSet()
        assertTrue("default" in names)
        assertEquals(5, names.count { it.startsWith("preset-") }, "every non-default preset is a case")
        assertTrue(corpus.cases.count { !it.valid } >= 10, "error cases present")
        assertTrue(corpus.cases.count { it.resolvedError != null } >= 2, "resolved@1 refusals present")
    }

    /** Inversion: a resolver with a mutated rule must fail the corpus, or the corpus proves nothing. */
    @Test
    fun mutatedResolversFailTheCorpus() {
        val mutants: Map<String, (JsonObject, JsonObject) -> List<ThemeProblem>> = mapOf(
            "text threshold 3.0" to { t, r -> TokenResolver.validate(t, r, TokenResolver.THRESHOLDS + ("text" to 3.0)) },
            "decorative threshold 1.0" to { t, r -> TokenResolver.validate(t, r, TokenResolver.THRESHOLDS + ("decorative" to 1.0)) },
            "drops warnings" to { t, r -> TokenResolver.validate(t, r).filter { it.severity == ThemeProblem.Severity.Error } },
        )
        for ((name, mutant) in mutants) {
            val failing = corpus.cases.filter { Corpus.compare(it, mutant).isNotEmpty() }
            assertTrue(failing.isNotEmpty(), "mutant '$name' passed the whole corpus")
        }
        // A resolution mutant: swapping light and dark of one role must break byte-equality.
        val c = corpus.cases.first { it.name == "default" }
        val mutated = Corpus.resolve(c).json.let { j ->
            val colors = j.getValue("colors").jsonObject.toMutableMap()
            val brand = colors.getValue("brand").jsonObject
            colors["brand"] = JsonObject(brand + mapOf("light" to brand.getValue("dark"), "dark" to brand.getValue("light")))
            JsonObject(j + ("colors" to JsonObject(colors)))
        }
        assertTrue(JsJson.stringify(mutated, 2) + "\n" != c.expectedResolved, "swapped brand still equal to the golden")
    }

    @Test
    fun goldenIsTheDefaultCase() {
        val golden = File(Corpus.repoRoot, "tokens-source/tokens.resolved.json").readText()
        assertEquals(corpus.cases.first { it.name == "default" }.expectedResolved, golden)
    }
}

/** Loading and comparing the corpus. */
internal object Corpus {
    val repoRoot = File(System.getProperty("stapel.repoRoot") ?: error("stapel.repoRoot not set"))
    private val src = File(repoRoot, "tokens-source")
    val defaultTheme: JsonObject = JsJson.parse(File(src, "theme.default.json").readText()).jsonObject
    val standardRamps: JsonObject = JsJson.parse(File(src, "ramps.standard.json").readText()).jsonObject
    val source = TokenSource("@stapel/tokens", "0.8.1")

    class Case(
        val name: String,
        val host: JsonObject,
        val valid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val resolvedError: String?,
        val expectedResolved: String?,
        val expectedCss: String?,
    )

    class Loaded(val cases: List<Case>)

    fun load(): Loaded {
        val dir = File(src, "conformance")
        val cases = File(dir, "cases").listFiles { f -> f.name.endsWith(".theme.json") }!!.sortedBy { it.name }.map { f ->
            val name = f.name.removeSuffix(".theme.json")
            val e = JsJson.parse(File(dir, "expected/$name.expect.json").readText()).jsonObject
            Case(
                name,
                JsJson.parse(f.readText()).jsonObject,
                e.getValue("valid").jsonPrimitive.boolean,
                e.getValue("errors").jsonArray.map { it.jsonPrimitive.content },
                e.getValue("warnings").jsonArray.map { it.jsonPrimitive.content },
                (e["resolvedError"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                File(dir, "expected/$name.tokens.resolved.json").takeIf { it.isFile }?.readText(),
                File(dir, "expected/$name.tokens.css").takeIf { it.isFile }?.readText(),
            )
        }
        return Loaded(cases)
    }

    fun resolve(c: Case): ResolvedTokens {
        val (theme, ramps) = TokensGenerator.prepare(defaultTheme, standardRamps, c.host)
        return TokenResolver.resolve(theme, ramps, source)
    }

    /** Every difference between the Kotlin port (with [validate]) and Node on [c]; empty = conformant. */
    fun compare(c: Case, validate: (JsonObject, JsonObject) -> List<ThemeProblem>): List<String> {
        val out = mutableListOf<String>()
        val (theme, ramps) = TokensGenerator.prepare(defaultTheme, standardRamps, c.host)
        val problems = validate(theme, ramps)
        val errors = problems.filter { it.severity == ThemeProblem.Severity.Error }.map { it.message }
        val warnings = problems.filter { it.severity == ThemeProblem.Severity.Warning }.map { it.message }
        if (errors != c.errors) out += "${c.name}: errors differ\n  kotlin: $errors\n  node:   ${c.errors}"
        if (warnings != c.warnings) out += "${c.name}: warnings differ (${warnings.size} vs ${c.warnings.size})"
        if (errors.isNotEmpty() || !c.valid) return out
        val resolved = try {
            TokenResolver.resolve(theme, ramps, source)
        } catch (e: ThemeException) {
            if (e.problem.message != c.resolvedError) out += "${c.name}: resolved@1 refusal differs\n  kotlin: ${e.problem.message}\n  node:   ${c.resolvedError}"
            return out
        }
        if (c.resolvedError != null) out += "${c.name}: Node refuses resolved@1 (${c.resolvedError}), Kotlin resolved it"
        val text = TokenResolver.toResolvedJson(resolved)
        if (text != c.expectedResolved) out += "${c.name}: tokens.resolved.json differs from Node\n" + firstDiff(text, c.expectedResolved.orEmpty())
        c.expectedCss?.let { css -> out += cssDifferences(c.name, css, resolved) }
        return out
    }

    private fun firstDiff(a: String, b: String): String {
        val la = a.lines()
        val lb = b.lines()
        val i = la.indices.firstOrNull { it >= lb.size || la[it] != lb[it] } ?: la.size
        return "  line ${i + 1}\n  kotlin: ${la.getOrNull(i)}\n  node:   ${lb.getOrNull(i)}"
    }

    private val DECL = Regex("""--stapel-([a-z0-9-]+): ([^;]+);""")

    /** The unmodified bin's `tokens.css`: every role value per mode and the page gutter per breakpoint. */
    private fun cssDifferences(name: String, css: String, r: ResolvedTokens): List<String> {
        val out = mutableListOf<String>()
        val darkStart = css.indexOf("[data-theme=\"dark\"]")
        val mediaStart = css.indexOf("@media").let { if (it < 0) css.length else it }
        val light = DECL.findAll(css.substring(0, darkStart)).associate { it.groupValues[1] to it.groupValues[2] }
        val dark = DECL.findAll(css.substring(darkStart, mediaStart)).associate { it.groupValues[1] to it.groupValues[2] }
        for ((role, c) in r.colors) {
            val l = light[role]?.substringBefore(" /*")?.trim()
            val d = dark[role]?.substringBefore(" /*")?.trim()
            if (l == null || ColorText.normalise(JsonPrimitive(l), role) != c.light) out += "$name: $role light css=$l kotlin=${c.light}"
            if (d == null || ColorText.normalise(JsonPrimitive(d), role) != c.dark) out += "$name: $role dark css=$d kotlin=${c.dark}"
        }
        for ((role, byBp) in r.responsive) {
            val base = light[role]
            val phone = byBp.getValue("phone")
            if (base != (if (phone == 0.0) "0" else "${JsJson.formatNumber(phone)}px")) out += "$name: $role phone css=$base kotlin=$phone"
            for ((bp, px) in byBp) {
                if (bp == "phone") continue
                val width = JsJson.formatNumber(r.breakpoints.getValue(bp))
                val arm = Regex("""@media \(min-width: ${width}px\) \{[^}]*--stapel-$role: ([^;]+);""").find(css)?.groupValues?.get(1)
                if (arm != "${JsJson.formatNumber(px)}px" && !(px == 0.0 && arm == "0")) out += "$name: $role $bp css=$arm kotlin=$px"
            }
        }
        return out
    }
}
