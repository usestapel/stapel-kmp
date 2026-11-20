package stapel.push

import kotlinx.coroutines.flow.Flow
import stapel.result.StapelResult

/**
 * A push SDK behind the facade. Implemented by provider artifacts (`stapel-push-firebase`); never
 * called by app or brick code, which use [PushController].
 *
 * Every implementation passes `PushProviderConformance` (stapel-push-testing) on every target: an
 * [availability] other than [ProviderAvailability.Ready] answers every call without throwing.
 */
public interface PushProvider {
    /** Stable id: `firebase`. */
    public val id: String

    /** Whether this provider can work here; read once at start. */
    public val availability: ProviderAvailability

    /** The platform the registry should record for its tokens; null where push is unsupported (desktop). */
    public val platform: DevicePlatform?

    /** True when its tokens can be registered with the backend's device registry. */
    public val transactional: Boolean

    /** The current OS permission, without asking. */
    public suspend fun permission(): PushPermission

    /**
     * Shows the OS prompt when the permission is [PushPermission.NotDetermined]; otherwise answers
     * the current one. [provisional] asks for iOS provisional authorization (no prompt is shown);
     * ignored elsewhere.
     */
    public suspend fun requestPermission(provisional: Boolean = false): PushPermission

    /** The current token, minting one when needed. */
    public suspend fun token(): StapelResult<PushToken, PushError>

    /** Invalidates the current token at the provider; the next [token] is a new one. */
    public suspend fun deleteToken(): StapelResult<Unit, PushError>

    /** Tokens the provider rotated on its own. */
    public val tokens: Flow<PushToken>

    /** Incoming notifications; buffered until first collected, so the launch notification is not lost. */
    public val events: Flow<PushEvent>

    /** What the OS shows for a notification while the app is on screen. */
    public fun setForegroundPresentation(presentation: ForegroundPresentation)

    /** Creates or updates Android notification channels; no-op elsewhere. */
    public fun ensureChannels(channels: List<PushChannelSpec>)
}
