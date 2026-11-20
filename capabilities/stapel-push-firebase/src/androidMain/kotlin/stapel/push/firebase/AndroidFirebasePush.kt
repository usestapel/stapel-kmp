package stapel.push.firebase

import stapel.push.DevicePlatform
import stapel.push.PushProvider
import stapel.runtime.NoopLogger
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.application

/**
 * The Firebase push provider on Android, over FCM. Without `google-services.json` (the
 * google-services Gradle plugin then generates no default app) it reports unconfigured and
 * answers every call without throwing. Create it once per process, on the main thread.
 */
public fun androidFirebasePushProvider(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    logger: StapelLogger = NoopLogger,
): PushProvider = firebasePushProvider(
    FirebasePushRelay.bridge ?: AndroidFirebasePushBridge(platform.application),
    DevicePlatform.Android,
    dispatchers,
    logger,
)
