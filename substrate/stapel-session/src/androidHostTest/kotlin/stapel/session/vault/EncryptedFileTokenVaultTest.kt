package stapel.session.vault

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * G-S2 (Android, host): the AES-GCM envelope and the encrypted file, with a JVM-generated key.
 * The `AndroidKeyStore` key itself needs a device and is NOT exercised here.
 */
class EncryptedFileTokenVaultTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val dir: File = Files.createTempDirectory("stapel-vault").toFile()
    private val file = File(dir, "stapel.session.vault")

    @Test
    fun ciphertextOnDiskAndRoundTrip() = runTest {
        val vault = EncryptedFileTokenVault(file, GcmEnvelope { key }, TestDispatchers)
        assertIs<VaultRead.Empty>(vault.read())
        vault.write(sample)
        val onDisk = file.readBytes().decodeToString(throwOnInvalidSequence = false)
        assertFalse("access-1" in onDisk || "refresh-1" in onDisk, "token material is stored in plaintext")
        assertSameRecord(sample, (vault.read() as VaultRead.Present).session)
        vault.clear()
        assertIs<VaultRead.Empty>(vault.read())
    }

    @Test
    fun anotherKeyOrATamperedFileIsUnreadable() = runTest {
        EncryptedFileTokenVault(file, GcmEnvelope { key }, TestDispatchers).write(sample)
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertIs<VaultRead.Unreadable>(EncryptedFileTokenVault(file, GcmEnvelope { otherKey }, TestDispatchers).read())
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertIs<VaultRead.Unreadable>(EncryptedFileTokenVault(file, GcmEnvelope { key }, TestDispatchers).read())
    }

    @Test
    fun envelopeUsesAFreshIvPerSeal() {
        val envelope = GcmEnvelope { key }
        val a = envelope.seal("x".encodeToByteArray())
        val b = envelope.seal("x".encodeToByteArray())
        assertTrue(!a.contentEquals(b))
        assertFailsWith<java.security.GeneralSecurityException> { envelope.open(byteArrayOf(9, 9)) }
    }
}
