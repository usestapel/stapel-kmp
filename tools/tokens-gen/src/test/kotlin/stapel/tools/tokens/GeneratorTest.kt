package stapel.tools.tokens

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeneratorTest {
    @TempDir
    lateinit var tmp: File

    private fun repoCopy(): File {
        val root = File(tmp, "repo")
        File(Corpus.repoRoot, "tokens-source").copyRecursively(File(root, "tokens-source"))
        return root
    }

    @Test
    fun checkPassesAfterGenerateAndFailsOnEveryKindOfDrift() {
        val root = repoCopy()
        val gen = TokensGenerator.library(root)
        assertEquals(emptyList(), TokensGenerator.apply(gen, check = false))
        assertEquals(emptyList(), TokensGenerator.apply(TokensGenerator.library(root), check = true))

        // Inversion 1: a hand edit of the generated file.
        val file = gen.files.keys.first { it.name == "StapelDefaultTokens.kt" }
        file.writeText(file.readText().replace("0xFF4657D9u", "0xFF4657DAu"))
        assertEquals(listOf(file), TokensGenerator.apply(TokensGenerator.library(root), check = true))

        // Inversion 2: a source change that is not regenerated.
        TokensGenerator.apply(TokensGenerator.library(root), check = false)
        val presets = File(root, "tokens-source/presets.json")
        presets.writeText(presets.readText().replace("\"#0f63a3\"", "\"#0f63a4\""))
        val drift = TokensGenerator.apply(TokensGenerator.library(root), check = true)
        assertEquals(listOf("StapelPresetTokens.kt"), drift.map { it.name })

        // Inversion 3: a missing generated file.
        file.delete()
        assertTrue(file in TokensGenerator.apply(TokensGenerator.library(root), check = true))
    }

    @Test
    fun libraryRefusesWhenTheGoldenDisagrees() {
        val root = repoCopy()
        val golden = File(root, "tokens-source/tokens.resolved.json")
        golden.writeText(golden.readText().replace("\"#4657d9\"", "\"#4657da\""))
        val e = assertFailsWith<IllegalStateException> { TokensGenerator.library(root) }
        assertContains(e.message.orEmpty(), "differs from the Node golden")
    }

    @Test
    fun appThemeWithAFailingPairFailsTheBuild() {
        val theme = File(tmp, "stapel.theme.json").apply {
            writeText("""{"ramps":{"brand":{"100":"#eef0fd","300":"#98a5fa","500":"#d0d4ff","700":"#3948b8","900":"#232b4d"}}}""")
        }
        val e = assertFailsWith<ThemeValidationException> {
            TokensGenerator.app(theme, null, File(tmp, "out"), "app.theme", "AppTokens", Visibility.Internal, null)
        }
        assertTrue(e.problems.any { it.code == "contrast" && "text-on-accent on brand (light)" in it.message }, e.message)
    }

    @Test
    fun appThemeRendersAnInternalObjectAndTheResolvedJson() {
        val theme = File(tmp, "stapel.theme.json").apply { writeText("""{"scales":{"controls":{"height":36}}}""") }
        val resolvedFile = File(tmp, "tokens.resolved.json")
        val gen = TokensGenerator.app(theme, null, File(tmp, "out"), "app.theme", "AppTokens", Visibility.Internal, resolvedFile)
        TokensGenerator.apply(gen, check = false)
        val kt = File(tmp, "out/app/theme/AppTokens.kt").readText()
        assertContains(kt, "internal val AppTokens: StapelTokens")
        assertContains(kt, "import stapel.tokens.StapelTokens")
        assertContains(kt, "height = 36f")
        assertContains(resolvedFile.readText(), "\"schema\": \"stapel.tokens.resolved/1\"")
    }

    @Test
    fun absentHostThemeIsTheLibraryDefault() {
        val gen = TokensGenerator.app(null, null, File(tmp, "out"), "app.theme", "AppTokens", Visibility.Public, File(tmp, "r.json"))
        assertEquals(File(Corpus.repoRoot, "tokens-source/tokens.resolved.json").readText(), gen.files.getValue(File(tmp, "r.json")))
    }

    @Test
    fun emitterRefusesAThemeMissingACanonRole() {
        val json = File(Corpus.repoRoot, "tokens-source/tokens.resolved.json").readText()
        val obj = JsJson.parse(json) as kotlinx.serialization.json.JsonObject
        val colors = (obj.getValue("colors") as kotlinx.serialization.json.JsonObject) - "info-on"
        val broken = ResolvedTokens.fromResolvedJson(kotlinx.serialization.json.JsonObject(obj + ("colors" to kotlinx.serialization.json.JsonObject(colors))))
        val e = assertFailsWith<ThemeException> { ComposeEmitter.render(broken, "p", "T", Visibility.Public) }
        assertEquals("compose-missing", e.problem.code)
    }

    @Test
    fun jsNumberAndStringFormatting() {
        assertEquals("4", JsJson.formatNumber(4.0))
        assertEquals("0.08", JsJson.formatNumber(0.08))
        assertEquals("1e-7", JsJson.formatNumber(1e-7))
        assertEquals("1e+21", JsJson.formatNumber(1e21))
        assertEquals("123456789012", JsJson.formatNumber(123456789012.0))
        // Expected values are Node's own `(x).toFixed(n)`: decided on the exact binary value, an exact tie goes up.
        assertEquals("1.9", JsJson.toFixed(1.95, 1))
        assertEquals("1.4", JsJson.toFixed(1.45, 1))
        assertEquals("1.3", JsJson.toFixed(1.25, 1))
        assertEquals("0.3", JsJson.toFixed(0.35, 1))
        assertEquals("2.67", JsJson.toFixed(2.675, 2))
        assertEquals("\"a\\\"\\n\\u0001é\"", JsJson.quote("a\"\n\u0001é"))
        assertEquals("""{"1":1,"2":2,"b":0,"a":3}""", JsJson.stringify(JsJson.parse("""{"b":0,"2":2,"a":3,"1":1}""")))
    }
}
