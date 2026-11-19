package stapel.session

import kotlinx.coroutines.withContext
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.session.vault.defaultVaultDirectory
import java.nio.file.Files

public actual fun platformInstallationIdentity(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    ids: IdGenerator,
    namespace: String,
): InstallationIdentity {
    val file = defaultVaultDirectory(namespace).resolve("installation-id")
    return StoredInstallationIdentity(
        ids,
        load = { withContext(dispatchers.io) { if (Files.exists(file)) Files.readString(file).trim() else null } },
        save = { id ->
            withContext(dispatchers.io) {
                Files.createDirectories(file.parent)
                Files.writeString(file, id)
            }
        },
    )
}
