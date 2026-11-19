package stapel.tools.tokens.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertTrue

/** TestKit: a preset theme becomes a value in the requested package; a failing-contrast theme fails `check`. */
class StapelTokensPluginTest {
    private val repo = File(System.getProperty("stapel.repoRoot"))
    private val dir: File = Files.createTempDirectory("stapel-tokens-plugin").toFile()

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(dir)
        .withTestKitDir(File(System.getProperty("stapel.testkitHome")))
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")

    private fun project(themeCase: String) {
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n")
        File(dir, "build.gradle.kts").writeText(
            """
            plugins { base; id("io.github.usestapel.tokens") }
            stapelTokens { packageName.set("shop.theme"); objectName.set("AppTokens") }
            """.trimIndent(),
        )
        File(repo, "tokens-source/conformance/cases/$themeCase.theme.json").copyTo(File(dir, "stapel.theme.json"))
    }

    @Test
    fun `a preset theme generates the app value and passes the check`() {
        project("preset-ocean")
        runner("generateStapelTokens", "check").build()
        val f = File(dir, "build/generated/stapelTokens/shop/theme/AppTokens.kt")
        assertTrue(f.isFile)
        assertContains(f.readText(), "internal val AppTokens: StapelTokens")
    }

    @Test
    fun `check alone on a fresh checkout generates first and passes`() {
        // No build/ yet (a CI checkout): the check must not call never-generated tokens stale.
        project("preset-ocean")
        val r = runner("checkStapelTokens").build()
        assertContains(r.output, ":generateStapelTokens")
        assertTrue(File(dir, "build/generated/stapelTokens/shop/theme/AppTokens.kt").isFile)
    }

    @Test
    fun `a theme failing contrast fails the check with the problem named`() {
        project("failing-contrast")
        val r = runner("check").buildAndFail()
        assertContains(r.output, "stapel.theme.json is not a valid theme")
    }
}
