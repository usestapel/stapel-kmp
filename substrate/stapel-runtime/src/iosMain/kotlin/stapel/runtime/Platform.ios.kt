package stapel.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.resetSystemTimeZone
import platform.Foundation.systemTimeZone
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.darwin.dispatch_get_main_queue
import stapel.result.text.LocaleTag

/** The iOS platform handle (carries nothing). */
public fun PlatformContext(): PlatformContext = IosPlatformContext

private object IosPlatformContext : PlatformContext

internal actual fun platformIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

private fun deviceLocale(): LocaleTag {
    val preferred = NSLocale.preferredLanguages.firstOrNull() as? String
    return LocaleTag(preferred ?: NSLocale.currentLocale.localeIdentifier.replace('_', '-'))
}

/** `NSLocale.preferredLanguages`, re-read on `NSCurrentLocaleDidChangeNotification`. */
public actual fun platformLocaleProvider(platform: PlatformContext): LocaleProvider {
    val provider = DeviceLocaleProvider(deviceLocale())
    NSNotificationCenter.defaultCenter.addObserverForName(
        NSCurrentLocaleDidChangeNotification,
        null,
        NSOperationQueue.mainQueue,
    ) { _ -> provider.deviceLocaleChanged(deviceLocale()) }
    return provider
}

/** `NSTimeZone.systemTimeZone`, re-read on `NSSystemTimeZoneDidChangeNotification`. */
public actual fun platformTimeZoneProvider(platform: PlatformContext): TimeZoneProvider {
    val provider = DeviceTimeZoneProvider(NSTimeZone.systemTimeZone.name)
    NSNotificationCenter.defaultCenter.addObserverForName(
        NSSystemTimeZoneDidChangeNotification,
        null,
        NSOperationQueue.mainQueue,
    ) { _ ->
        NSTimeZone.resetSystemTimeZone()
        provider.changed(NSTimeZone.systemTimeZone.name)
    }
    return provider
}

/** `UIApplication` did-become-active / did-enter-background notifications. Call on the main thread. */
public actual fun platformAppLifecycle(platform: PlatformContext, scope: CoroutineScope): AppLifecycle {
    val active = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    val lifecycle = MutableAppLifecycle(if (active) AppPhase.Foreground else AppPhase.Background)
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
        lifecycle.changed(AppPhase.Foreground)
    }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
        lifecycle.changed(AppPhase.Background)
    }
    return lifecycle
}

/** `NWPathMonitor` on the main queue; "expensive" paths are reported as metered. */
public actual fun platformConnectivity(platform: PlatformContext, scope: CoroutineScope): Connectivity {
    val state = ConnectivityState(scope, osObserved = true)
    val monitor = nw_path_monitor_create()
    nw_path_monitor_set_update_handler(monitor) { path ->
        val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
        state.osChanged(if (satisfied) NetworkStatus.Online(nw_path_is_expensive(path)) else NetworkStatus.Offline)
    }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
    return state
}

/** Writes through `NSLog`. */
public actual fun platformLogger(minLevel: LogLevel): StapelLogger = object : StapelLogger {
    override fun isEnabled(level: LogLevel, tag: String): Boolean = level >= minLevel

    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        if (level >= minLevel) NSLog("%@", "${level.name.uppercase()} $tag: ${formatRecord(message, error, fields)}")
    }
}

/** A handle that stops a [watch]. */
public fun interface Cancellable {
    /** Stops delivery. */
    public fun cancel()
}

/**
 * Collects this flow in [scope] and delivers each value to [onEach] on the main thread — the one
 * helper Swift needs, because Objective-C export gives `Flow` no usable collection API.
 */
public fun <T : Any> Flow<T>.watch(scope: CoroutineScope, onEach: (T) -> Unit): Cancellable {
    val job = scope.launch(Dispatchers.Main) { collect { onEach(it) } }
    return Cancellable { job.cancel() }
}
