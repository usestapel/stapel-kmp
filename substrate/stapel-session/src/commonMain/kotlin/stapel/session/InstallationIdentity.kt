package stapel.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

/**
 * A random id for this installation, sent as `device_id` on auth operations (the backend keys its
 * per-device OTP cooldown and guest dedup on it). Created on first use; survives sign-out (it is
 * deliberately not in the token vault, so signing out cannot reset a cooldown); new after a
 * reinstall; never a hardware identifier.
 */
public interface InstallationIdentity {
    /** The id, creating and storing it on first use. */
    public suspend fun deviceId(): String
}

/**
 * The platform store: Android a file under `noBackupFilesDir` (not restored onto a new install), iOS
 * `NSUserDefaults` (removed with the app), desktop a file next to the token vault.
 */
public expect fun platformInstallationIdentity(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    ids: IdGenerator,
    namespace: String = "stapel.session",
): InstallationIdentity

/** Shared logic over a two-function store. */
internal class StoredInstallationIdentity(
    private val ids: IdGenerator,
    private val load: suspend () -> String?,
    private val save: suspend (String) -> Unit,
) : InstallationIdentity {
    private val lock = Mutex()
    private var cached: String? = null

    override suspend fun deviceId(): String = lock.withLock {
        cached ?: (load()?.takeIf { it.isNotBlank() } ?: ids.uuid().also { save(it) }).also { cached = it }
    }
}
