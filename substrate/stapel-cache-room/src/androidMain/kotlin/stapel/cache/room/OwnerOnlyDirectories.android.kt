package stapel.cache.room

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun createOwnerOnlyDirectories(path: Path) {
    SystemFileSystem.createDirectories(path)
}
