package stapel.session

import kotlinx.coroutines.withContext
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.application
import java.io.File

public actual fun platformInstallationIdentity(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    ids: IdGenerator,
    namespace: String,
): InstallationIdentity {
    val file = File(platform.application.noBackupFilesDir, "$namespace.installation-id")
    return StoredInstallationIdentity(
        ids,
        load = { withContext(dispatchers.io) { if (file.exists()) file.readText().trim() else null } },
        save = { id -> withContext(dispatchers.io) { file.writeText(id) } },
    )
}
