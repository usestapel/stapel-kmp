package stapel.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import stapel.result.text.LocaleTag
import java.util.Locale
import java.util.TimeZone

/** The desktop platform handle (carries nothing). */
public fun PlatformContext(): PlatformContext = DesktopPlatformContext

private object DesktopPlatformContext : PlatformContext

internal actual fun platformIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

/** The JVM default locale at start; the JDK reports no change events. */
public actual fun platformLocaleProvider(platform: PlatformContext): LocaleProvider =
    DeviceLocaleProvider(LocaleTag(Locale.getDefault().toLanguageTag()))

/** The JVM default time zone at start; the JDK reports no change events. */
public actual fun platformTimeZoneProvider(platform: PlatformContext): TimeZoneProvider =
    DeviceTimeZoneProvider(TimeZone.getDefault().id)

/** Always [AppPhase.Foreground]: on desktop, window focus is the app's business. */
public actual fun platformAppLifecycle(platform: PlatformContext, scope: CoroutineScope): AppLifecycle =
    MutableAppLifecycle(AppPhase.Foreground)

/** No OS observer: starts [NetworkStatus.Unknown] and is driven only by transport reports. */
public actual fun platformConnectivity(platform: PlatformContext, scope: CoroutineScope): Connectivity =
    ConnectivityState(scope, osObserved = false)

/** Writes `LEVEL tag: message` lines to standard error. */
public actual fun platformLogger(minLevel: LogLevel): StapelLogger = object : StapelLogger {
    override fun isEnabled(level: LogLevel, tag: String): Boolean = level >= minLevel

    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        if (level >= minLevel) System.err.println("${level.name.uppercase()} $tag: ${formatRecord(message, error, fields)}")
    }
}
