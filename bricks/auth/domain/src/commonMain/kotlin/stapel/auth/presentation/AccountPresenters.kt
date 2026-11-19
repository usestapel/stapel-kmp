package stapel.auth.presentation

import kotlinx.coroutines.flow.collectLatest
import stapel.auth.GuestPolicy
import stapel.auth.StapelAuth
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.AuthCapabilities
import stapel.auth.model.AuthMethod
import stapel.auth.model.AuthMethodId
import stapel.auth.model.AuthUser
import stapel.auth.model.MethodPlacement
import stapel.auth.model.SessionDevice
import stapel.auth.session.LogoutResult
import stapel.auth.session.SignOutEverywhereResult
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.result.StapelResult
import stapel.result.state.Resource

// ---------------------------------------------------------------- method chooser

/** The sign-in methods to render, cut into the server's placements. */
public data class MethodZones(
    /** Main tabs / lone form (at most three; OAuth and SSO never here). */
    val main: List<AuthMethod>,
    /** Button row under the form. */
    val bottom: List<AuthMethod>,
    /** Behind "more ways to sign in". */
    val overflow: List<AuthMethod>,
    /** Whether "continue as guest" may be offered. */
    val guestAvailable: Boolean,
    /** Whether "create account with a password" may be offered. */
    val passwordRegistration: Boolean,
) {
    /** Every method to render, in zone order. */
    val all: List<AuthMethod> get() = main + bottom + overflow
}

/** State of [MethodChooserPresenter]. */
public sealed interface MethodChooserState {
    /** Reading capabilities. */
    public data object Loading : MethodChooserState

    /** Ready. An empty [zones] is a server configuration error and is shown as such. */
    public data class Ready(
        /** What to render. */
        val zones: MethodZones,
    ) : MethodChooserState

    /** Capabilities could not be read. */
    public data class Failed(
        /** Why. */
        val error: AuthError,
    ) : MethodChooserState
}

/** Intents of [MethodChooserPresenter]. */
public sealed interface MethodChooserIntent {
    /** Re-reads capabilities from the network. */
    public data object Refresh : MethodChooserIntent
}

/**
 * The sign-in method chooser, driven by `capabilities.methods`: a method is offered only when the
 * server enables it for sign-in, the client can complete it ([stapel.auth.platform.AuthPlatform.completable])
 * and the project's fence ([stapel.auth.AuthConfig.methods]) allows it.
 */
public class MethodChooserPresenter(private val auth: StapelAuth, context: PresenterContext) :
    Presenter<MethodChooserState, MethodChooserIntent, Nothing>(MethodChooserState.Loading, context) {

    init {
        launch(KEY) {
            auth.capabilities.capabilities().collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> Unit
                    is Resource.Ready -> reduce { MethodChooserState.Ready(computeZones(resource.data, auth)) }
                    is Resource.Failed -> reduce {
                        if (this is MethodChooserState.Ready) this else MethodChooserState.Failed(AuthError.of(resource.error))
                    }
                }
            }
        }
    }

    override suspend fun handle(intent: MethodChooserIntent) {
        when (intent) {
            MethodChooserIntent.Refresh -> launch("refresh") {
                when (val r = auth.capabilities.refresh()) {
                    is StapelResult.Ok -> reduce { MethodChooserState.Ready(computeZones(r.value, auth)) }
                    is StapelResult.Err -> reduce { if (this is MethodChooserState.Ready) this else MethodChooserState.Failed(r.error) }
                }
            }
        }
    }

    private companion object {
        const val KEY = "capabilities"
    }
}

/** Pure zoning of the server's method list (port of the web pair's `computeZones` plus mobile clamps). */
internal fun computeZones(capabilities: AuthCapabilities, auth: StapelAuth): MethodZones {
    val fence = auth.config.methods
    val offered = capabilities.methods
        .filter { it.enabled && it.canLogin }
        .filter { auth.platform.completable(it.id) }
        .filter { fence == null || it.id in fence }
        .sortedBy { it.order }
    val main = mutableListOf<AuthMethod>()
    val bottom = mutableListOf<AuthMethod>()
    val overflow = mutableListOf<AuthMethod>()
    for (method in offered) {
        val neverMain = method.id == AuthMethodId.OAuth || method.id == AuthMethodId.Sso
        when {
            method.placement == MethodPlacement.Main && !neverMain && main.size < MAX_MAIN -> main += method
            method.placement == MethodPlacement.Bottom -> bottom += method
            else -> overflow += method
        }
    }
    if (main.isEmpty() && overflow.isNotEmpty()) main += overflow.removeAt(0)
    return MethodZones(
        main = main,
        bottom = bottom,
        overflow = overflow,
        guestAvailable = capabilities.guestSessions && auth.config.guest != GuestPolicy.Off,
        passwordRegistration = capabilities.passwordRegistration,
    )
}

private const val MAX_MAIN = 3

// ---------------------------------------------------------------- guest

/** State of [GuestPresenter]. */
public sealed interface GuestState {
    /** Nothing started. */
    public data object Idle : GuestState

    /** Creating (or reusing) the guest session. */
    public data object Creating : GuestState

    /** Refused (guest sessions off, rate limited, network). */
    public data class Failed(
        /** Why. */
        val error: AuthError,
    ) : GuestState

    /** The acting user: the guest, or the account when a session already existed. */
    public data class Done(
        /** The user. */
        val user: AuthUser,
    ) : GuestState
}

/** Intents of [GuestPresenter]. */
public sealed interface GuestIntent {
    /** Continues as a guest. */
    public data object ContinueAsGuest : GuestIntent
}

/**
 * "Continue as guest". Upgrading a guest has no presenter of its own: any sign-in presenter run while
 * the guest session is active upgrades it, and the session reports `Promoted` or `Merged`.
 */
public class GuestPresenter(private val auth: StapelAuth, context: PresenterContext) :
    Presenter<GuestState, GuestIntent, Nothing>(GuestState.Idle, context) {

    override suspend fun handle(intent: GuestIntent) {
        when (intent) {
            GuestIntent.ContinueAsGuest -> {
                if (state.value == GuestState.Creating) return
                if (auth.config.guest == GuestPolicy.Off) {
                    reduce { GuestState.Failed(AuthError(AuthErrorReason.Unsupported, null)) }
                    return
                }
                reduce { GuestState.Creating }
                launch("guest") {
                    when (val r = auth.session.ensureGuest()) {
                        is StapelResult.Ok -> reduce { GuestState.Done(r.value) }
                        is StapelResult.Err -> reduce { GuestState.Failed(r.error) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sign-out

/** State of [SignOutPresenter]. */
public sealed interface SignOutState {
    /** Nothing started. */
    public data object Idle : SignOutState

    /** Signing out. */
    public data class SigningOut(
        /** True for "sign out everywhere". */
        val everywhere: Boolean,
    ) : SignOutState

    /** Signed out locally; [result] says what the server did. */
    public data class Done(
        /** What the server did. */
        val result: SignOutEverywhereResult,
    ) : SignOutState
}

/** Intents of [SignOutPresenter]. */
public sealed interface SignOutIntent {
    /** Signs this device out. */
    public data object SignOut : SignOutIntent

    /** Revokes every other session, then signs this device out. */
    public data object SignOutEverywhere : SignOutIntent
}

/** Sign-out and sign-out-everywhere. Local state is cleared in every case. */
public class SignOutPresenter(private val auth: StapelAuth, context: PresenterContext) :
    Presenter<SignOutState, SignOutIntent, Nothing>(SignOutState.Idle, context) {

    override suspend fun handle(intent: SignOutIntent) {
        if (state.value is SignOutState.SigningOut) return
        val everywhere = intent == SignOutIntent.SignOutEverywhere
        reduce { SignOutState.SigningOut(everywhere) }
        launch("sign-out") {
            val result = if (everywhere) {
                auth.session.logoutEverywhere()
            } else {
                SignOutEverywhereResult(othersRevoked = false, self = auth.session.logout())
            }
            reduce { SignOutState.Done(result) }
        }
    }

    /** True when the server confirmed this device's revocation. */
    public val revokedOnServer: Boolean
        get() = (state.value as? SignOutState.Done)?.result?.self == LogoutResult.RevokedOnServer
}

// ---------------------------------------------------------------- sessions and devices

/** State of [SessionsPresenter]. */
public data class SessionsState(
    /** The list. */
    val sessions: Resource<List<SessionDevice>> = Resource.Loading,
    /** Session ids being revoked. */
    val revoking: Set<String> = emptySet(),
    /** Session ids being confirmed ("this was me"). */
    val confirming: Set<String> = emptySet(),
    /** True while "revoke all others" runs. */
    val revokingOthers: Boolean = false,
    /** The last mutation error. */
    val error: AuthError? = null,
)

/** Intents of [SessionsPresenter]. */
public sealed interface SessionsIntent {
    /** Re-reads the list from the network. */
    public data object Refresh : SessionsIntent

    /** Revokes one session (the UI confirms first). */
    public data class Revoke(
        /** Session id. */
        val sessionId: String,
    ) : SessionsIntent

    /** Revokes every session except this one. */
    public data object RevokeOthers : SessionsIntent

    /** Marks a suspicious session as the user's own ("this was me"); its flag is cleared on success. */
    public data class ConfirmMine(
        /** Session id. */
        val sessionId: String,
    ) : SessionsIntent
}

/** The signed-in user's sessions and devices with revoke, revoke-others and "this was me". */
public class SessionsPresenter(private val auth: StapelAuth, context: PresenterContext) :
    Presenter<SessionsState, SessionsIntent, Nothing>(SessionsState(), context) {

    init {
        launch("observe") { auth.devices.sessions().collectLatest { list -> reduce { copy(sessions = list) } } }
    }

    override suspend fun handle(intent: SessionsIntent) {
        when (intent) {
            SessionsIntent.Refresh -> launch("refresh") {
                when (val r = auth.devices.load()) {
                    is StapelResult.Ok -> reduce { copy(sessions = Resource.Ready(r.value, stapel.result.state.Freshness.network(auth.config.clock.now().toEpochMilliseconds())), error = null) }
                    is StapelResult.Err -> reduce { copy(error = r.error) }
                }
            }
            is SessionsIntent.Revoke -> {
                if (intent.sessionId in state.value.revoking) return
                reduce { copy(revoking = revoking + intent.sessionId, error = null) }
                launch("revoke-${intent.sessionId}") {
                    val r = auth.devices.revoke(intent.sessionId)
                    reduce {
                        val list = (sessions as? Resource.Ready)?.let { ready ->
                            if (r is StapelResult.Ok) Resource.Ready(ready.data.filterNot { it.id == intent.sessionId }, ready.freshness) else ready
                        } ?: sessions
                        copy(sessions = list, revoking = revoking - intent.sessionId, error = (r as? StapelResult.Err)?.error)
                    }
                }
            }
            is SessionsIntent.ConfirmMine -> {
                if (intent.sessionId in state.value.confirming) return
                reduce { copy(confirming = confirming + intent.sessionId, error = null) }
                launch("confirm-${intent.sessionId}") {
                    val r = auth.devices.confirm(intent.sessionId)
                    reduce {
                        val list = (sessions as? Resource.Ready)?.let { ready ->
                            if (r is StapelResult.Ok) {
                                Resource.Ready(ready.data.map { if (it.id == intent.sessionId) it.copy(isSuspicious = false) else it }, ready.freshness)
                            } else {
                                ready
                            }
                        } ?: sessions
                        copy(sessions = list, confirming = confirming - intent.sessionId, error = (r as? StapelResult.Err)?.error)
                    }
                }
            }
            SessionsIntent.RevokeOthers -> {
                if (state.value.revokingOthers) return
                reduce { copy(revokingOthers = true, error = null) }
                launch("revoke-others") {
                    val r = auth.devices.revokeOthers()
                    reduce {
                        val list = (sessions as? Resource.Ready)?.let { ready ->
                            if (r is StapelResult.Ok) Resource.Ready(ready.data.filter { it.isCurrent }, ready.freshness) else ready
                        } ?: sessions
                        copy(sessions = list, revokingOthers = false, error = (r as? StapelResult.Err)?.error)
                    }
                }
            }
        }
    }
}
