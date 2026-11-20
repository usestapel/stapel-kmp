package stapel.push.firebase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import stapel.push.DevicePlatform
import stapel.push.ForegroundPresentation
import stapel.push.Importance
import stapel.push.ProviderAvailability
import stapel.push.PushChannelSpec
import stapel.push.PushError
import stapel.push.PushEvent
import stapel.push.PushMessage
import stapel.push.PushPermission
import stapel.push.PushProvider
import stapel.push.PushToken
import stapel.push.TokenKind
import stapel.result.StapelResult
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.info
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a token request may take: on a simulator without APNs the SDK may never answer. */
internal val TOKEN_BUDGET: Duration = 30.seconds

/**
 * The provider logic shared by every platform, over a [FirebasePushBridge]. [bridge] null = this
 * platform has no implementation ([unsupportedReason]).
 */
internal class BridgePushProvider(
    private val bridge: FirebasePushBridge?,
    override val platform: DevicePlatform?,
    private val configFile: String,
    private val unsupportedReason: String,
    private val dispatchers: StapelDispatchers,
    private val logger: StapelLogger,
) : PushProvider {
    override val id: String = FIREBASE_PUSH_PROVIDER_ID
    override val transactional: Boolean = true

    override val availability: ProviderAvailability = when {
        bridge == null -> ProviderAvailability.Unsupported(unsupportedReason)
        !bridge.configured() -> ProviderAvailability.Unconfigured(configFile)
        else -> ProviderAvailability.Ready
    }

    private val ready = availability == ProviderAvailability.Ready
    private val rotations = MutableSharedFlow<PushToken>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val incoming = Channel<PushEvent>(Channel.UNLIMITED)

    override val tokens: Flow<PushToken> = rotations.asSharedFlow()
    override val events: Flow<PushEvent> = incoming.receiveAsFlow()

    init {
        if (ready) bridge?.setListener(Listener())
    }

    private inner class Listener : FirebasePushListener {
        override fun onToken(token: String) {
            rotations.tryEmit(PushToken(token, TokenKind.Fcm, id))
        }

        override fun onMessage(id: String?, title: String?, body: String?, data: Map<String, String>, foreground: Boolean) {
            incoming.trySend(PushEvent.Received(message(id, title, body, data), foreground))
        }

        override fun onOpened(id: String?, title: String?, body: String?, data: Map<String, String>, actionId: String?) {
            incoming.trySend(PushEvent.Opened(message(id, title, body, data), actionId))
        }
    }

    private fun message(id: String?, title: String?, body: String?, data: Map<String, String>) =
        PushMessage(id = id, title = title, body = body, data = data.toMap(), provider = this.id)

    override suspend fun permission(): PushPermission {
        val b = bridge?.takeIf { ready } ?: return PushPermission.Unsupported
        val answer = CompletableDeferred<String>()
        withContext(dispatchers.main) { b.permission { answer.complete(it) } }
        return parsePermission(answer.await())
    }

    override suspend fun requestPermission(provisional: Boolean): PushPermission {
        val b = bridge?.takeIf { ready } ?: return PushPermission.Unsupported
        val answer = CompletableDeferred<String>()
        withContext(dispatchers.main) { b.requestPermission(provisional) { answer.complete(it) } }
        return parsePermission(answer.await())
    }

    override suspend fun token(): StapelResult<PushToken, PushError> {
        val b = bridge?.takeIf { ready } ?: return StapelResult.Err(PushError.Unavailable(availability))
        val answer = CompletableDeferred<Pair<String?, String?>>()
        withContext(dispatchers.main) { b.token { token, error -> answer.complete(token to error) } }
        val (token, error) = withTimeoutOrNull(TOKEN_BUDGET) { answer.await() }
            ?: return StapelResult.Err(PushError.Transient("no token within $TOKEN_BUDGET"))
        if (token.isNullOrEmpty()) {
            logger.info(TAG) { "stapel.push token unavailable: ${error ?: "empty"}" }
            return StapelResult.Err(PushError.Transient(error ?: "empty token"))
        }
        return StapelResult.Ok(PushToken(token, TokenKind.Fcm, id))
    }

    override suspend fun deleteToken(): StapelResult<Unit, PushError> {
        val b = bridge?.takeIf { ready } ?: return StapelResult.Err(PushError.Unavailable(availability))
        val answer = CompletableDeferred<String?>()
        withContext(dispatchers.main) { b.deleteToken { answer.complete(it) } }
        val error = answer.await()
        return if (error == null) StapelResult.Ok(Unit) else StapelResult.Err(PushError.Transient(error))
    }

    override fun setForegroundPresentation(presentation: ForegroundPresentation) {
        val b = bridge?.takeIf { ready } ?: return
        b.setForegroundPresentation(
            when (presentation) {
                ForegroundPresentation.System -> "system"
                ForegroundPresentation.InApp -> "inApp"
                ForegroundPresentation.Silent -> "silent"
            },
        )
    }

    override fun ensureChannels(channels: List<PushChannelSpec>) {
        val b = bridge?.takeIf { ready } ?: return
        for (c in channels) {
            b.ensureChannel(
                c.id,
                c.name,
                when (c.importance) {
                    Importance.Low -> "low"
                    Importance.Default -> "default"
                    Importance.High -> "high"
                },
            )
        }
    }

    private companion object {
        const val TAG = "stapel.push"
    }
}

/** The bridge's permission words; anything unknown reads as [PushPermission.Denied] (never deliver on a guess). */
internal fun parsePermission(status: String): PushPermission = when (status) {
    "notDetermined" -> PushPermission.NotDetermined
    "granted" -> PushPermission.Granted
    "provisional" -> PushPermission.Provisional
    else -> PushPermission.Denied
}
