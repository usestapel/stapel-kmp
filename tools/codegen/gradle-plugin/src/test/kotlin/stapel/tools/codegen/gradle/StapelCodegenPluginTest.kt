package stapel.tools.codegen.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertTrue

/** TestKit: the plugin generates a committed client in the requested package and its check catches drift. */
class StapelCodegenPluginTest {
    private val dir: File = Files.createTempDirectory("stapel-codegen-plugin").toFile()

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(dir)
        .withTestKitDir(File(System.getProperty("stapel.testkitHome")))
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")

    private fun project() {
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n")
        File(dir, "build.gradle.kts").writeText(
            """
            plugins { base; id("io.github.usestapel.codegen") }
            stapelCodegen {
                module("orders") {
                    contractDir.set(layout.projectDirectory.dir("contracts/orders"))
                    packageName.set("shop.orders.api")
                }
            }
            """.trimIndent(),
        )
        val contract = File(dir, "contracts/orders").apply { mkdirs() }
        javaClass.getResourceAsStream("/orders/schema.json")!!.use { input -> File(contract, "schema.json").outputStream().use { input.copyTo(it) } }
    }

    @Test
    fun `generates the client in the app's package and the check passes`() {
        project()
        runner("stapelGenerate").build()
        val api = File(dir, "src/commonMain/generated/api/orders/kotlin/shop/orders/api/OrdersApi.kt")
        assertTrue(api.isFile, "OrdersApi.kt not generated: ${File(dir, "src").walkTopDown().toList()}")
        assertContains(api.readText(), "package shop.orders.api")
        assertTrue(File(dir, "src/commonMain/generated/api/orders/kotlin/shop/orders/api/model/Order.kt").isFile)
        runner("stapelGenerateCheck").build()
    }

    @Test
    fun `an edited generated file fails the check`() {
        project()
        runner("stapelGenerate").build()
        val api = File(dir, "src/commonMain/generated/api/orders/kotlin/shop/orders/api/OrdersApi.kt")
        api.appendText("// hand edit\n")
        val r = runner("check").buildAndFail()
        assertContains(r.output, "committed clients differ from their contracts")
        assertContains(r.output, "OrdersApi.kt")
    }

    @Test
    fun `a contract change fails the check until regenerated`() {
        project()
        runner("stapelGenerate").build()
        val schema = File(dir, "contracts/orders/schema.json")
        schema.writeText(schema.readText().replace("\"What was ordered.\"", "\"What the customer ordered.\""))
        runner("stapelGenerateCheck").buildAndFail()
        runner("stapelGenerate").build()
        runner("stapelGenerateCheck").build()
    }
}
