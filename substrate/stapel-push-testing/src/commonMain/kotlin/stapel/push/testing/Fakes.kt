package stapel.push.testing

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import stapel.push.DevicePlatform
import stapel.push.DeviceStatus
import stapel.push.ForegroundPresentation
import stapel.push.ProviderAvailability
import stapel.push.PushChannelSpec
import stapel.push.PushDevice
import stapel.push.PushDeviceRegistry
import stapel.push.PushError
import stapel.push.PushEvent
import stapel.push.PushMessage
import stapel.push.PushPermission
import stapel.push.PushProvider
import stapel.push.PushToken
import stapel.push.RegistryError
import stapel.push.TokenKind
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.StapelResult

/**
 * A scriptable [PushProvider]: tokens are `fake-token-<n>` (see `tokenPrefix`), the OS answers [osAnswer] to a prompt,
 * and every call is appended to [log] (`permission`, `request:<provisional>`, `token`, `delete`,
 * `channels:<ids>`, `foreground:<mode>`). Passes [PushProviderContract].
 *
 * @param availability what the provider reports; anything but Ready answers every call without throwing.
 * @param platform the platform its tokens are registered for.
 * @param journal where calls are appended; share one list with [FakeDeviceRegistry] to assert the order across both.
 * @param tokenPrefix tokens are `<prefix>-<n>`; give each run its own against a real registry (tokens are unique there).
 */
public class FakePushProvider(
    /** What the provider reports. */
    override val availability: ProviderAvailability = ProviderAvailability.Ready,
    /** The platform its tokens are registered for. */
    override val platform: DevicePlatform? = DevicePlatform.Android,
    /** The provider id. */
    override val id: String = "fake",
    /** Whether the registry takes its tokens. */
    override val transactional: Boolean = true,
    journal: MutableList<String> = mutableListOf(),
    private val tokenPrefix: String = "fake-token",
) : PushProvider {
    /** Every call, in order. */
    public val log: MutableList<String> = journal

    /** The current OS permission. */
    public var osPermission: PushPermission = PushPermission.NotDetermined

    /** What the OS prompt answers ([PushPermission.Provisional] is answered to a provisional request automatically). */
    public var osAnswer: PushPermission = PushPermission.Granted

    /** When set, [token] fails with this error. */
    public var tokenFailure: PushError? = null

    /** How many OS prompts were shown. */
    public var prompts: Int = 0
        private set

    private var serial = 0
    private var current: PushToken? = null

    /** The last foreground presentation set. */
    public var foreground: ForegroundPresentation? = null
        private set

    /** The channels last ensured. */
    public var channels: List<PushChannelSpec> = emptyList()
        private set

    private val rotations = MutableSharedFlow<PushToken>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val incoming = Channel<PushEvent>(Channel.UNLIMITED)

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override val tokens: Flow<PushToken> = rotations.asSharedFlow()
    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override val events: Flow<PushEvent> = incoming.receiveAsFlow()

    private val ready get() = availability == ProviderAvailability.Ready

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun permission(): PushPermission {
        log += "permission"
        return if (ready) osPermission else PushPermission.Unsupported
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun requestPermission(provisional: Boolean): PushPermission {
        log += "request:$provisional"
        if (!ready) return PushPermission.Unsupported
        if (osPermission == PushPermission.NotDetermined) {
            if (provisional && platform == DevicePlatform.Ios) {
                osPermission = PushPermission.Provisional
            } else {
                prompts++
                osPermission = osAnswer
            }
        }
        return osPermission
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun token(): StapelResult<PushToken, PushError> {
        log += "token"
        if (!ready) return StapelResult.Err(PushError.Unavailable(availability))
        tokenFailure?.let { return StapelResult.Err(it) }
        val token = current ?: mint()
        return StapelResult.Ok(token)
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun deleteToken(): StapelResult<Unit, PushError> {
        log += "delete"
        if (!ready) return StapelResult.Err(PushError.Unavailable(availability))
        current = null
        return StapelResult.Ok(Unit)
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override fun setForegroundPresentation(presentation: ForegroundPresentation) {
        log += "foreground:${presentation.name}"
        foreground = presentation
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override fun ensureChannels(channels: List<PushChannelSpec>) {
        log += "channels:${channels.joinToString(",") { it.id }}"
        this.channels = channels
    }

    /** The token currently held, without minting. */
    public val currentToken: PushToken? get() = current

    /** The provider rotates its token on its own (as FCM does) and reports it. */
    public fun rotate(): PushToken = mint().also { rotations.tryEmit(it) }

    /** A notification arrives. */
    public fun receive(message: PushMessage, foreground: Boolean) {
        incoming.trySend(PushEvent.Received(message, foreground))
    }

    /** The person taps a notification (also the launch notification). */
    public fun open(message: PushMessage, actionId: String? = null) {
        incoming.trySend(PushEvent.Opened(message, actionId))
    }

    private fun mint(): PushToken = PushToken("$tokenPrefix-${++serial}", TokenKind.Fcm, id).also { current = it }
}

/** A message for tests with the given `data`; the type defaults to `test_message`. */
public fun pushMessage(
    data: Map<String, String> = emptyMap(),
    title: String? = "Title",
    body: String? = "Body",
    provider: String = "fake",
): PushMessage = PushMessage(
    id = "m-${data.hashCode()}",
    title = title,
    body = body,
    data = mapOf("notification_type" to "test_message") + data,
    provider = provider,
)

/**
 * An in-memory device registry with the backend's rules: one row per installation and account, a
 * token live for another account on another installation is refused (409), guests are refused
 * (403). Every call is appended to [log]
 * (`register:<fingerprint>`, `unregister:<installation>`, `status:<fingerprint>`).
 */
public class FakeDeviceRegistry(
    /** Who the bearer is right now; null = a guest or no session. */
    public var account: () -> String?,
    journal: MutableList<String> = mutableListOf(),
) : PushDeviceRegistry {
    /**
     * One registered row.
     *
     * @property account the owning account.
     * @property installationId the installation.
     * @property fingerprint the token's fingerprint.
     * @property active false once the provider rejected it.
     */
    public data class Row(val account: String, val installationId: String, val fingerprint: String, val active: Boolean = true)

    /** The rows. */
    public val rows: MutableList<Row> = mutableListOf()

    /** Every call, in order. */
    public val log: MutableList<String> = journal

    /** When set, the next calls fail with this transport-level refusal. */
    public var failure: StapelResult.Err<RegistryError>? = null

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun register(device: PushDevice): StapelResult<Unit, RegistryError> {
        log += "register:${device.token.fingerprint}"
        failure?.let { return it }
        val who = account() ?: return StapelResult.Err(RegistryError.NotAllowed(refusal(403, "error.403.forbidden")))
        val fp = device.token.fingerprint
        if (rows.any { it.fingerprint == fp && it.account != who && it.installationId != device.installationId }) {
            return StapelResult.Err(RegistryError.HeldByAnotherAccount(refusal(409, "error.409.conflict")))
        }
        // Account switch: the installation's rows under other accounts go; one row per installation and account.
        rows.removeAll { it.installationId == device.installationId || it.fingerprint == fp }
        rows += Row(who, device.installationId, fp)
        return StapelResult.Ok(Unit)
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun unregister(installationId: String): StapelResult<Unit, RegistryError> {
        log += "unregister:$installationId"
        failure?.let { return it }
        val who = account() ?: return StapelResult.Err(RegistryError.NotAllowed(refusal(403, "error.403.forbidden")))
        rows.removeAll { it.installationId == installationId && it.account == who }
        return StapelResult.Ok(Unit)
    }

    /** See [stapel.push.PushProvider] / [stapel.push.PushDeviceRegistry]; recorded in [log]. */
    override suspend fun status(token: PushToken): StapelResult<DeviceStatus, RegistryError> {
        log += "status:${token.fingerprint}"
        failure?.let { return it }
        val who = account() ?: return StapelResult.Err(RegistryError.NotAllowed(refusal(403, "error.403.forbidden")))
        val row = rows.firstOrNull { it.fingerprint == token.fingerprint && it.account == who }
        return StapelResult.Ok(
            when {
                row == null -> DeviceStatus.Absent
                row.active -> DeviceStatus.Registered
                else -> DeviceStatus.Inactive
            },
        )
    }

    /** The provider rejected [token] on a send (the backend marks the row inactive). */
    public fun deactivate(token: PushToken) {
        for (i in rows.indices) if (rows[i].fingerprint == token.fingerprint) rows[i] = rows[i].copy(active = false)
    }

    private fun refusal(status: Int, code: String) = ServerRefusal(ErrorEnvelope(ErrorCode(code), status))
}
