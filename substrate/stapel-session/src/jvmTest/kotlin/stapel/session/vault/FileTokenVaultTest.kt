package stapel.session.vault

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** G-S2 (desktop): owner-only file, atomic replace, unreadable → Unreadable. */
class FileTokenVaultTest {
    private val root = Files.createTempDirectory("stapel-vault")
    private val file = root.resolve("app").resolve("session.json")
    private val vault = FileTokenVault(file, TestDispatchers)

    @Test
    fun roundTripWithOwnerOnlyPermissions() = runTest {
        assertIs<VaultRead.Empty>(vault.read())
        vault.write(sample)
        assertSameRecord(sample, (vault.read() as VaultRead.Present).session)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.parent)))
        vault.write(sample)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)), "replace keeps owner-only")
        assertEquals(listOf("session.json"), Files.list(file.parent).use { s -> s.map { it.fileName.toString() }.toList() }, "no temp file left")
        vault.clear()
        assertFalse(Files.exists(file))
        assertIs<VaultRead.Empty>(vault.read())
    }

    @Test
    fun corruptFileIsUnreadableNotACrash() = runTest {
        vault.write(sample)
        Files.write(file, "not json".toByteArray())
        assertIs<VaultRead.Unreadable>(vault.read())
    }

    @Test
    fun defaultDirectoryIsPerUser() {
        val dir = defaultVaultDirectory("com.example.app")
        assertTrue(dir.startsWith(System.getProperty("user.home")) || System.getenv("XDG_DATA_HOME") != null, "$dir")
        assertTrue(dir.endsWith("com.example.app"))
    }
}
