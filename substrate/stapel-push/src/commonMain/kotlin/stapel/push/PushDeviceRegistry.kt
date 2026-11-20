package stapel.push

import stapel.result.StapelError
import stapel.result.StapelResult

/** The platform a device row records. */
public enum class DevicePlatform(
    /** The registry's word. */
    public val wire: String,
) {
    /** iOS. */
    Ios("ios"),

    /** Android. */
    Android("android"),
}

/**
 * One installation's push address, as registered with the backend.
 *
 * @property token the provider token.
 * @property platform the device platform.
 * @property installationId the installation id (`device_id`): one row per installation and account.
 */
public data class PushDevice(
    public val token: PushToken,
    public val platform: DevicePlatform,
    public val installationId: String,
)

/** What the registry knows about a token. */
public enum class DeviceStatus {
    /** Registered to the caller and deliverable. */
    Registered,

    /** Registered, but the provider rejected it on a send: mint a new one. */
    Inactive,

    /** Not registered to the caller. */
    Absent,
}

/**
 * A refusal of the device registry. Each variant carries the failure it came from and answers
 * the [StapelError] members with it.
 */
public sealed interface RegistryError : StapelError {
    /** The failure as the transport or the backend reported it. */
    public val cause: StapelError

    /**
     * The token is live for another account on another installation (409).
     */
    public data class HeldByAnotherAccount(override val cause: StapelError) : RegistryError, StapelError by cause

    /**
     * The session may not hold a device (guest, 403).
     */
    public data class NotAllowed(override val cause: StapelError) : RegistryError, StapelError by cause

    /**
     * Anything else: transport, server, contract.
     */
    public data class Failed(override val cause: StapelError) : RegistryError, StapelError by cause
}

/**
 * The backend seam. Implemented by `stapel-notifications-push` over the notifications brick; a
 * project with another backend binds its own. Every call is made with the session's bearer.
 */
public interface PushDeviceRegistry {
    /** Registers (or refreshes) [device] for the signed-in account and the current session. */
    public suspend fun register(device: PushDevice): StapelResult<Unit, RegistryError>

    /** Removes this installation's row; answers success when there was none. */
    public suspend fun unregister(installationId: String): StapelResult<Unit, RegistryError>

    /** Whether [token] is registered to the caller and deliverable. */
    public suspend fun status(token: PushToken): StapelResult<DeviceStatus, RegistryError>
}
