package stapel.session.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.withContext
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.application
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

public actual fun platformTokenVault(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    config: TokenVaultConfig,
): TokenVault = EncryptedFileTokenVault(
    file = File(platform.application.noBackupFilesDir, "${config.namespace}.vault"),
    envelope = GcmEnvelope { keystoreKey("${config.namespace}.key") },
    dispatchers = dispatchers,
)

/** The AES-256-GCM key in `AndroidKeyStore`, generated on first use. Not exportable. */
private fun keystoreKey(alias: String): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build(),
    )
    return generator.generateKey()
}

/**
 * `[version=1][12-byte IV][ciphertext+tag]` with AES/GCM/NoPadding. The key comes from [key]; any
 * crypto failure (invalidated key, tampered file) surfaces as an exception the vault maps to
 * [VaultRead.Unreadable].
 */
internal class GcmEnvelope(private val key: () -> SecretKey) {
    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        if (sealed.size <= 1 + IV_BYTES || sealed[0] != VERSION) throw GeneralSecurityException("unknown envelope")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, IV_BYTES))
        return cipher.doFinal(sealed, 1 + IV_BYTES, sealed.size - 1 - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** One encrypted file, replaced through a temp file and a rename. */
internal class EncryptedFileTokenVault(
    private val file: File,
    private val envelope: GcmEnvelope,
    private val dispatchers: StapelDispatchers,
) : TokenVault {
    override suspend fun read(): VaultRead = withContext(dispatchers.io) {
        if (!file.exists()) return@withContext VaultRead.Empty
        val plain = try {
            envelope.open(file.readBytes())
        } catch (e: GeneralSecurityException) {
            return@withContext VaultRead.Unreadable("decrypt failed: ${e::class.simpleName}")
        } catch (e: IOException) {
            return@withContext VaultRead.Unreadable("read failed: ${e::class.simpleName}")
        }
        decodeRecord(plain)?.let { VaultRead.Present(it) } ?: VaultRead.Unreadable("unrecognised record")
    }

    override suspend fun write(session: StoredSession) {
        withContext(dispatchers.io) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(envelope.seal(encodeRecord(session)))
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IOException("could not replace ${file.name}")
            }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) { file.delete() }
    }
}
