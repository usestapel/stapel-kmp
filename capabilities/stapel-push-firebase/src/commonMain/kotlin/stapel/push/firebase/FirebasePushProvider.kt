package stapel.push.firebase

import stapel.push.DevicePlatform
import stapel.push.PushProvider
import stapel.runtime.NoopLogger
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger

/**
 * The Firebase push provider over a [bridge]: on iOS the app's Swift bridge, in tests a recording
 * fake. A null [bridge] or one that reports [FirebasePushBridge.configured] false gives a provider
 * that answers every call without throwing (unsupported / unconfigured). Create it on the main
 * thread: the bridge is asked for its configuration and given its listener here.
 *
 * @param platform the platform its tokens are registered for.
 */
public fun firebasePushProvider(
    bridge: FirebasePushBridge?,
    platform: DevicePlatform,
    dispatchers: StapelDispatchers,
    logger: StapelLogger = NoopLogger,
): PushProvider = BridgePushProvider(
    bridge = bridge,
    platform = platform,
    configFile = configFileOf(platform),
    unsupportedReason = "no Firebase push bridge installed",
    dispatchers = dispatchers,
    logger = logger,
)

internal fun configFileOf(platform: DevicePlatform): String = when (platform) {
    DevicePlatform.Ios -> "GoogleService-Info.plist"
    DevicePlatform.Android -> "google-services.json"
}
