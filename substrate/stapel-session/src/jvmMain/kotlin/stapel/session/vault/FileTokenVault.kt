package stapel.session.vault

import kotlinx.coroutines.withContext
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

public actual fun platformTokenVault(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    config: TokenVaultConfig,
): TokenVault = FileTokenVault(defaultVaultDirectory(config.namespace).resolve("session.json"), dispatchers)

/** `<per-user data dir>/<namespace>`: Application Support on macOS, `%APPDATA%` on Windows, XDG elsewhere. */
internal fun defaultVaultDirectory(namespace: String): Path {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        os.contains("mac") -> Paths.get(home, "Library", "Application Support")
        os.contains("win") -> System.getenv("APPDATA")?.let { Paths.get(it) } ?: Paths.get(home, "AppData", "Roaming")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) } ?: Paths.get(home, ".local", "share")
    }
    return base.resolve(namespace)
}

/** The desktop vault: one owner-only file, replaced atomically. See [platformTokenVault]. */
internal class FileTokenVault(private val file: Path, private val dispatchers: StapelDispatchers) : TokenVault {
    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    override suspend fun read(): VaultRead = withContext(dispatchers.io) {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (_: NoSuchFileException) {
            return@withContext VaultRead.Empty
        } catch (e: IOException) {
            return@withContext VaultRead.Unreadable("read failed: ${e::class.simpleName}")
        }
        decodeRecord(bytes)?.let { VaultRead.Present(it) } ?: VaultRead.Unreadable("unrecognised record")
    }

    override suspend fun write(session: StoredSession) {
        withContext(dispatchers.io) {
            val dir = file.parent
            if (posix) {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            } else {
                Files.createDirectories(dir)
            }
            val temp = if (posix) {
                Files.createTempFile(dir, ".session", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            } else {
                Files.createTempFile(dir, ".session", ".tmp")
            }
            try {
                Files.write(temp, encodeRecord(session))
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) { Files.deleteIfExists(file) }
    }
}
