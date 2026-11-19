package stapel.tools.codegen.cli

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The CLI contract the brick-domain Gradle hooks run (`gen [--check] --module … --brick … --repo-root …`),
 * end to end over a copy of the repository's auth inputs, with the drift gate inverted.
 */
class GenCheckTest {
    private val repo: Path = Paths.get(System.getProperty("stapel.repoRoot")!!)

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { s ->
            s.forEach { src ->
                val dst = to.resolve(from.relativize(src).toString())
                if (Files.isDirectory(src)) Files.createDirectories(dst) else Files.copy(src, dst)
            }
        }
    }

    private fun sandbox(): Path {
        val root = Files.createTempDirectory("gen-check")
        copyTree(repo.resolve("contracts/auth"), root.resolve("contracts/auth"))
        copyTree(repo.resolve("bricks/auth/domain/src/commonMain/generated"), root.resolve("bricks/auth/domain/src/commonMain/generated"))
        copyTree(repo.resolve("bricks/auth/domain/src/commonTest/generated"), root.resolve("bricks/auth/domain/src/commonTest/generated"))
        return root
    }

    private fun check(root: Path): Pair<Int, String> {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = run(listOf("gen", "--check", "--module", "stapel-auth", "--brick", "auth", "--repo-root", root.toString()), out, err)
        return code to (out.toString() + err)
    }

    private val generated = "bricks/auth/domain/src/commonMain/generated/kotlin/stapel/auth/api"

    @Test
    fun checkIsGreenOnTheCommittedTree() {
        val (code, text) = check(sandbox())
        assertEquals(0, code, text)
    }

    @Test
    fun aHandEditedGeneratedFileTurnsTheCheckRed() {
        val root = sandbox()
        val file = root.resolve("$generated/model/User.kt")
        Files.writeString(file, Files.readString(file).replace("public val bio", "public val biography"))
        val (code, text) = check(root)
        assertEquals(1, code)
        assertTrue("differs: kotlin/stapel/auth/api/model/User.kt" in text, text)
    }

    @Test
    fun aDeletedOrStrayFileTurnsTheCheckRed() {
        val root = sandbox()
        Files.delete(root.resolve("$generated/AuthKeys.kt"))
        Files.writeString(root.resolve("$generated/Stray.kt"), "package stapel.auth.api\n")
        val (code, text) = check(root)
        assertEquals(1, code)
        assertTrue("missing: kotlin/stapel/auth/api/AuthKeys.kt" in text && "not produced by the generator" in text, text)
    }

    @Test
    fun aChangedContractByteTurnsTheCheckRed() {
        val root = sandbox()
        val schema = root.resolve("contracts/auth/schema.json")
        val bytes = Files.readAllBytes(schema)
        bytes[bytes.size - 2] = if (bytes[bytes.size - 2] == ' '.code.toByte()) '\t'.code.toByte() else ' '.code.toByte()
        Files.write(schema, bytes)
        val (code, text) = check(root)
        assertEquals(1, code)
        assertTrue("E-PIN-DIGEST" in text, text)
    }

    @Test
    fun aContractChangeWithARepinnedDigestStillFailsOnTheDiff() {
        val root = sandbox()
        val schema = root.resolve("contracts/auth/schema.json")
        val text0 = Files.readString(schema).replaceFirst("Get current authenticated user information", "Get the current user")
        Files.writeString(schema, text0)
        val pin = root.resolve("contracts/auth/pin.json")
        val digest = stapel.tools.codegen.sha256(Files.readAllBytes(schema))
        Files.writeString(pin, Files.readString(pin).replace(Regex("(\"schema.json\": \\{\\s*\"sha256\": \")[0-9a-f]+"), "$1$digest"))
        val (code, text) = check(root)
        assertEquals(1, code)
        assertTrue("differs: kotlin/stapel/auth/api/AuthApi.kt" in text, text)
    }

    @Test
    fun genWritesWhatCheckThenAccepts() {
        val root = sandbox()
        Files.delete(root.resolve("$generated/AuthKeys.kt"))
        val out = StringBuilder()
        assertEquals(0, run(listOf("gen", "--module", "stapel-auth", "--brick", "auth", "--repo-root", root.toString()), out, StringBuilder()))
        assertEquals(0, check(root).first)
    }

    @Test
    fun usageErrorsAreExitCodeTwo() {
        assertEquals(2, run(emptyList(), StringBuilder(), StringBuilder()))
        assertEquals(2, run(listOf("gen", "--brick", "auth"), StringBuilder(), StringBuilder()))
        assertEquals(2, run(listOf("gen", "--module", "stapel-chat", "--brick", "auth", "--repo-root", "."), StringBuilder(), StringBuilder()))
    }
}
