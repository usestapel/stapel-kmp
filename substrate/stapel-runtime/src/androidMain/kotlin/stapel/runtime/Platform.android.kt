package stapel.runtime

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import stapel.result.text.LocaleTag
import java.util.TimeZone

/** The Android platform handle: wraps the `Application`. */
public fun PlatformContext(application: Application): PlatformContext = AndroidPlatformContext(application)

private class AndroidPlatformContext(val application: Application) : PlatformContext

/** The `Application` this handle wraps; fails for a handle not created on Android. */
public val PlatformContext.application: Application
    get() = (this as? AndroidPlatformContext)?.application
        ?: error("PlatformContext was not created with PlatformContext(application)")

internal actual fun platformIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

private fun Context.deviceLocale(): LocaleTag = LocaleTag(resources.configuration.locales[0].toLanguageTag())

/** `Configuration.locales[0]`, re-read on `ACTION_LOCALE_CHANGED`. */
public actual fun platformLocaleProvider(platform: PlatformContext): LocaleProvider {
    val app = platform.application
    val provider = DeviceLocaleProvider(app.deviceLocale())
    app.registerReceiver(
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                provider.deviceLocaleChanged(context.deviceLocale())
            }
        },
        IntentFilter(Intent.ACTION_LOCALE_CHANGED),
    )
    return provider
}

/** `TimeZone.getDefault()`, re-read on `ACTION_TIMEZONE_CHANGED`. */
public actual fun platformTimeZoneProvider(platform: PlatformContext): TimeZoneProvider {
    val app = platform.application
    val provider = DeviceTimeZoneProvider(TimeZone.getDefault().id)
    app.registerReceiver(
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                provider.changed(TimeZone.getDefault().id)
            }
        },
        IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
    )
    return provider
}

/** A started-activity counter over `ActivityLifecycleCallbacks`; starts [AppPhase.Background]. */
public actual fun platformAppLifecycle(platform: PlatformContext, scope: CoroutineScope): AppLifecycle {
    val lifecycle = MutableAppLifecycle(AppPhase.Background)
    platform.application.registerActivityLifecycleCallbacks(
        object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                lifecycle.changed(AppPhase.Foreground)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) lifecycle.changed(AppPhase.Background)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        },
    )
    return lifecycle
}

/**
 * `ConnectivityManager.registerDefaultNetworkCallback`; a network counts as online only with
 * `NET_CAPABILITY_VALIDATED`. Needs `ACCESS_NETWORK_STATE` (declared by this library's manifest).
 */
public actual fun platformConnectivity(platform: PlatformContext, scope: CoroutineScope): Connectivity {
    val state = ConnectivityState(scope, osObserved = true)
    val manager = platform.application.getSystemService(ConnectivityManager::class.java)
    manager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                state.osChanged(if (validated) NetworkStatus.Online(metered) else NetworkStatus.Offline)
            }

            override fun onLost(network: Network) {
                state.osChanged(NetworkStatus.Offline)
            }

            override fun onUnavailable() {
                state.osChanged(NetworkStatus.Offline)
            }
        },
    )
    return state
}

/** Writes to Logcat. */
public actual fun platformLogger(minLevel: LogLevel): StapelLogger = object : StapelLogger {
    override fun isEnabled(level: LogLevel, tag: String): Boolean = level >= minLevel

    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        if (level < minLevel) return
        val text = formatRecord(message, null, fields)
        when (level) {
            LogLevel.Verbose -> Log.v(tag, text, error)
            LogLevel.Debug -> Log.d(tag, text, error)
            LogLevel.Info -> Log.i(tag, text, error)
            LogLevel.Warn -> Log.w(tag, text, error)
            LogLevel.Error -> Log.e(tag, text, error)
        }
    }
}
