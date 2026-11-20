package stapel.cache.room

import java.io.File

/**
 * The per-user data directory of the desktop app [appId], where `cacheRoomModule()` keeps
 * `stapel-cache/` when no directory is passed:
 *
 * - macOS: `~/Library/Application Support/<appId>`;
 * - Windows: `%LOCALAPPDATA%\<appId>` (machine-local, not roaming: a cache does not follow the
 *   user to another machine), else `~\AppData\Local\<appId>`;
 * - Linux and other systems: `$XDG_DATA_HOME/<appId>` when that is an absolute path (the XDG base
 *   directory rule), else `~/.local/share/<appId>`.
 *
 * Not the OS cache directory (`~/Library/Caches`, `$XDG_CACHE_HOME`): the cache is the app's offline
 * copy and is removed by the app (logout wipe), not by a cleaner — the same choice as Android's
 * `noBackupFilesDir` and iOS's Application Support. Nothing is created here; the storage creates
 * `stapel-cache/` owner-only (`rwx------`) on POSIX file systems. On Windows the directory inherits
 * the user profile's ACL, which already excludes other users; no ACL is changed.
 *
 * @param appId the app's identifier (`StapelEnvironment.client.appId`, e.g. `com.example.shop`):
 *   letters, digits, `.`, `_`, `-`; not `.` or `..`.
 * @return the absolute directory path.
 * @throws IllegalArgumentException when [appId] cannot be a single directory name.
 */
public fun desktopDataDirectory(appId: String): String =
    desktopDataDirectory(appId, System.getProperty("os.name"), System.getProperty("user.home"), System::getenv)

internal fun desktopDataDirectory(appId: String, osName: String, home: String, env: (String) -> String?): String {
    require(APP_ID.matches(appId) && appId != "." && appId != "..") {
        "app id '$appId' cannot name a directory: use letters, digits, '.', '_' and '-'"
    }
    val os = osName.lowercase()
    val base = when {
        os.contains("mac") || os.contains("darwin") -> File(home, "Library/Application Support")
        os.contains("win") -> env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, "AppData\\Local")
        else -> env("XDG_DATA_HOME")?.let(::File)?.takeIf { it.isAbsolute } ?: File(home, ".local/share")
    }
    return File(base, appId).absolutePath
}

private val APP_ID = Regex("[A-Za-z0-9._-]+")
