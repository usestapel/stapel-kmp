package stapel.push.firebase

import stapel.push.ProviderAvailability
import stapel.push.PushProvider
import stapel.runtime.NoopLogger
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger

/**
 * The Firebase push provider on desktop: Firebase Cloud Messaging has no desktop client, so it
 * reports [ProviderAvailability.Unsupported] (`desktop`) and answers every call without throwing.
 */
public fun desktopFirebasePushProvider(dispatchers: StapelDispatchers, logger: StapelLogger = NoopLogger): PushProvider =
    BridgePushProvider(
        bridge = null,
        platform = null,
        configFile = "",
        unsupportedReason = "desktop",
        dispatchers = dispatchers,
        logger = logger,
    )
