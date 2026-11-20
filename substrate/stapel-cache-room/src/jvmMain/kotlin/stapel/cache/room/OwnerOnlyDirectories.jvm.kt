package stapel.cache.room

import kotlinx.io.files.Path
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

private val ownerOnly = PosixFilePermissions.fromString("rwx------")

internal actual fun createOwnerOnlyDirectories(path: Path) {
    val dir = Paths.get(path.toString())
    if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) {
        Files.createDirectories(dir)
        return
    }
    Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(ownerOnly))
    if (Files.getPosixFilePermissions(dir) != ownerOnly) Files.setPosixFilePermissions(dir, ownerOnly)
}
