package stapel.analytics

import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import java.io.File

public actual fun platformAnalyticsStorage(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    namespace: String,
): AnalyticsStorage = FileAnalyticsStorage(
    File(desktopDataBase(System.getProperty("os.name"), System.getProperty("user.home"), System::getenv), namespace).absolutePath,
    dispatchers.io,
)

/** The per-user data directory of the desktop OS (the same choice as the session's file vault). */
internal fun desktopDataBase(osName: String, home: String, env: (String) -> String?): File {
    val os = osName.lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> File(home, "Library/Application Support")
        os.contains("win") -> env("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, "AppData\\Roaming")
        else -> env("XDG_DATA_HOME")?.let(::File)?.takeIf { it.isAbsolute } ?: File(home, ".local/share")
    }
}
