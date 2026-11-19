package stapel.session

import platform.Foundation.NSUserDefaults
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

public actual fun platformInstallationIdentity(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    ids: IdGenerator,
    namespace: String,
): InstallationIdentity {
    val key = "$namespace.installation-id"
    val defaults = NSUserDefaults.standardUserDefaults
    return StoredInstallationIdentity(
        ids,
        load = { defaults.stringForKey(key) },
        save = { id -> defaults.setObject(id, key) },
    )
}
