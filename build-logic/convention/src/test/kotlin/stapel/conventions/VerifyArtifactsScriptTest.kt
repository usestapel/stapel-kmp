package stapel.conventions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `scripts/verify-artifacts.sh` against a staging repository of each artifact kind (KMP library,
 * BOM, version catalog, Gradle plugin, plugin marker): a complete repository passes, and a missing
 * expected file of every kind fails the script and is named.
 */
class VerifyArtifactsScriptTest {
    private val root = File(System.getProperty("stapel.repoRoot"))
    private val props = File(root, "gradle.properties").readLines()
    private val group = props.first { it.startsWith("stapel.group=") }.substringAfter("=")
    private val version = props.first { it.startsWith("VERSION_NAME=") }.substringAfter("=")
    private val pom = """
        <project><name>n</name><description>d</description><url>u</url><licenses></licenses><developers></developers><scm></scm></project>
    """.trimIndent()

    @TempDir
    lateinit var repo: File

    private val g get() = File(repo, group.replace('.', '/'))

    private fun put(path: String, text: String = "x"): String {
        File(g, path).apply { parentFile.mkdirs(); writeText(text) }
        return path
    }

    /** The files of one complete repository, by kind; returns the path of each kind's code/data file. */
    private fun complete(): Map<String, String> {
        val v = version
        put("stapel-lib/$v/stapel-lib-$v.pom", pom)
        put("stapel-lib/$v/stapel-lib-$v.module")
        put("stapel-lib/$v/stapel-lib-$v-sources.jar")
        put("stapel-lib/$v/stapel-lib-$v-javadoc.jar")
        put("stapel-lib-jvm/$v/stapel-lib-jvm-$v.jar")
        put("stapel-lib-android/$v/stapel-lib-android-$v.aar")
        put("stapel-lib-iosarm64/$v/stapel-lib-iosarm64-$v.klib")
        val klib = put("stapel-lib-iossimulatorarm64/$v/stapel-lib-iossimulatorarm64-$v.klib")
        val bom = put("stapel-bom/$v/stapel-bom-$v.pom", pom)
        put("stapel-catalog/$v/stapel-catalog-$v.pom", pom)
        put("stapel-catalog/$v/stapel-catalog-$v.module")
        val toml = put("stapel-catalog/$v/stapel-catalog-$v.toml")
        put("stapel-demo-gradle-plugin/$v/stapel-demo-gradle-plugin-$v.pom", pom)
        put("stapel-demo-gradle-plugin/$v/stapel-demo-gradle-plugin-$v.module")
        put("stapel-demo-gradle-plugin/$v/stapel-demo-gradle-plugin-$v-sources.jar")
        put("stapel-demo-gradle-plugin/$v/stapel-demo-gradle-plugin-$v-javadoc.jar")
        val jar = put("stapel-demo-gradle-plugin/$v/stapel-demo-gradle-plugin-$v.jar")
        val marker = "$group.demo.gradle.plugin"
        val markerPom = put("demo/$marker/$v/$marker-$v.pom", pom)
        return mapOf("klib" to klib, "bom" to bom, "toml" to toml, "jar" to jar, "marker" to markerPom)
    }

    private fun run(): Pair<Int, String> {
        val p = ProcessBuilder("sh", File(root, "scripts/verify-artifacts.sh").path, repo.path)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(60, TimeUnit.SECONDS))
        return p.exitValue() to out
    }

    @Test
    fun `a complete repository of every kind passes`() {
        complete()
        val (code, out) = run()
        assertEquals(0, code, out)
        assertTrue("verify-artifacts: ok" in out, out)
    }

    @Test
    fun `a missing file of each kind fails and is named`() {
        for (kind in listOf("klib", "bom", "toml", "jar", "marker")) {
            repo.listFiles()!!.forEach { it.deleteRecursively() }
            val path = complete().getValue(kind)
            File(g, path).delete()
            val (code, out) = run()
            assertNotEquals(0, code, "$kind: $out")
            assertTrue("MISSING" in out && path.substringAfterLast('/') in out, "$kind: $out")
        }
    }

    @Test
    fun `a plugin marker without a gradle plugin's fields fails`() {
        val marker = complete().getValue("marker")
        File(g, marker).writeText("<project><name>n</name></project>")
        val (code, out) = run()
        assertNotEquals(0, code, out)
        assertTrue("lacks <scm>" in out, out)
    }

    @Test
    fun `a gradle plugin is not held to KMP targets and a catalog is not held to jars`() {
        complete()
        val (_, out) = run()
        assertTrue("stapel-demo-gradle-plugin-jvm" !in out && "stapel-catalog-jvm" !in out && "stapel-catalog-$version-sources" !in out, out)
    }
}
