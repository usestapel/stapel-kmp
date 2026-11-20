package stapel.cache.room

import kotlinx.io.files.Path

/**
 * Creates [path] and its missing parents. JVM on a POSIX file system: created `rwx------`, and [path]
 * itself is set to `rwx------` when it already existed wider. Android and iOS: plain creation (the
 * app sandbox is private already).
 */
internal expect fun createOwnerOnlyDirectories(path: Path)
