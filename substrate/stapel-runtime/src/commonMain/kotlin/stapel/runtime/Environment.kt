package stapel.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stapel.result.text.LocaleTag
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The app's effective locale: the user's in-app choice when set, else the device's. */
public interface LocaleProvider {
    /** The effective locale. Hot: emits on every change. */
    public val locale: StateFlow<LocaleTag>

    /** Sets the in-app locale; null follows the device again. */
    public fun setAppLocale(tag: LocaleTag?)
}

/** The device's time zone as an IANA id (`Europe/Berlin`). Hot: emits on change where the OS reports it. */
public interface TimeZoneProvider {
    /** The current IANA time-zone id. */
    public val timeZoneId: StateFlow<String>
}

/** Whether the app is in front of the user. */
public enum class AppPhase {
    /** Visible and interactive. */
    Foreground,

    /** Not visible (or not started yet). */
    Background,
}

/** The app's foreground/background phase. */
public interface AppLifecycle {
    /** The current phase. */
    public val phase: StateFlow<AppPhase>
}

/** What is known about network reachability. */
public sealed interface NetworkStatus {
    /** Nothing observed yet. */
    public data object Unknown : NetworkStatus

    /** No usable network. */
    public data object Offline : NetworkStatus

    /**
     * A usable network.
     *
     * @property metered true on a metered link, null when the platform cannot tell.
     */
    public data class Online(public val metered: Boolean? = null) : NetworkStatus
}

/**
 * Network reachability. Transport outcomes are fed back through [reportReachable] and
 * [reportUnreachable], so a platform with no OS observer (desktop) still converges.
 */
public interface Connectivity {
    /** The current status. */
    public val status: StateFlow<NetworkStatus>

    /** A request reached a server. */
    public fun reportReachable()

    /** A request failed without reaching a server. */
    public fun reportUnreachable()
}

/** The device locale, re-read when the OS reports a change; [LocaleProvider.setAppLocale] overrides it. */
public expect fun platformLocaleProvider(platform: PlatformContext): LocaleProvider

/** The device time zone. */
public expect fun platformTimeZoneProvider(platform: PlatformContext): TimeZoneProvider

/**
 * The app's phase from OS callbacks. Android: a started-activity counter; iOS: `UIApplication`
 * notifications; desktop: always [AppPhase.Foreground] (window focus is the app's business).
 */
public expect fun platformAppLifecycle(platform: PlatformContext, scope: CoroutineScope): AppLifecycle

/**
 * Reachability from the OS where one exists (Android `ConnectivityManager` with validation, iOS
 * `NWPathMonitor`); desktop has no observer and is driven only by reports.
 */
public expect fun platformConnectivity(platform: PlatformContext, scope: CoroutineScope): Connectivity

/** A [LocaleProvider] whose device locale the platform pushes in. */
internal class DeviceLocaleProvider(device: LocaleTag) : LocaleProvider {
    private val device = MutableStateFlow(device)
    private val override = MutableStateFlow<LocaleTag?>(null)
    private val effective = MutableStateFlow(device)

    override val locale: StateFlow<LocaleTag> = effective.asStateFlow()

    override fun setAppLocale(tag: LocaleTag?) {
        override.value = tag
        effective.value = tag ?: device.value
    }

    fun deviceLocaleChanged(tag: LocaleTag) {
        device.value = tag
        effective.value = override.value ?: tag
    }
}

/** A [TimeZoneProvider] the platform pushes changes into. */
internal class DeviceTimeZoneProvider(initial: String) : TimeZoneProvider {
    private val state = MutableStateFlow(initial)
    override val timeZoneId: StateFlow<String> = state.asStateFlow()

    fun changed(id: String) {
        state.value = id
    }
}

/** An [AppLifecycle] the platform pushes phases into. */
internal class MutableAppLifecycle(initial: AppPhase) : AppLifecycle {
    private val state = MutableStateFlow(initial)
    override val phase: StateFlow<AppPhase> = state.asStateFlow()

    fun changed(phase: AppPhase) {
        state.value = phase
    }
}

/**
 * The reachability state machine shared by every platform.
 *
 * With an OS observer ([osObserved]) the OS is authoritative; reports only turn an [NetworkStatus.Unknown]
 * into [NetworkStatus.Online]. Without one, [reportUnreachable] marks [NetworkStatus.Offline] and the
 * mark decays back to [NetworkStatus.Unknown] after [offlineDecay] — otherwise a caller that refuses
 * to send while offline would never send again and the state could never recover.
 */
internal class ConnectivityState(
    private val scope: CoroutineScope,
    private val osObserved: Boolean,
    private val offlineDecay: Duration = 5.seconds,
) : Connectivity {
    private val state = MutableStateFlow<NetworkStatus>(NetworkStatus.Unknown)
    private var decay: Job? = null

    override val status: StateFlow<NetworkStatus> = state.asStateFlow()

    fun osChanged(status: NetworkStatus) {
        state.value = status
    }

    override fun reportReachable() {
        if (osObserved) {
            if (state.value == NetworkStatus.Unknown) state.value = NetworkStatus.Online()
            return
        }
        decay?.cancel()
        if (state.value !is NetworkStatus.Online) state.value = NetworkStatus.Online()
    }

    override fun reportUnreachable() {
        if (osObserved) return
        state.value = NetworkStatus.Offline
        decay?.cancel()
        decay = scope.launch {
            delay(offlineDecay)
            if (state.value == NetworkStatus.Offline) state.value = NetworkStatus.Unknown
        }
    }
}
