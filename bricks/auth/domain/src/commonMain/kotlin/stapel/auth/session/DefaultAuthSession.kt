package stapel.auth.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.auth.api.AuthApi
import stapel.auth.api.model.AnonymousAuth
import stapel.auth.api.model.AuthResponse
import stapel.auth.api.model.LogoutRequest
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.internal.LoginStep
import stapel.auth.internal.kind
import stapel.auth.internal.toDomain
import stapel.auth.internal.toStep
import stapel.auth.model.AuthUser
import stapel.auth.model.SignInKind
import stapel.auth.model.SignInOutcome
import stapel.result.StapelResult
import stapel.session.SessionEnd
import stapel.session.SessionEvent
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenPair
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/** Result of turning a login answer into a session (or a challenge). */
internal sealed interface Completion {
    data class Done(val outcome: SignInOutcome) : Completion

    data class Refused(val error: AuthError) : Completion
}

/**
 * [AuthSession] over a [SessionManager]. The only place in the library that writes tokens: every
 * session-minting answer goes through [complete] / [adopt].
 */
internal class DefaultAuthSession(
    private val api: AuthApi,
    private val sessions: SessionManager,
    scope: CoroutineScope,
    private val clock: Clock,
    private val deviceId: suspend () -> String?,
) : AuthSession {
    private val user = MutableStateFlow<AuthUser?>(null)
    private val changeFlow = MutableSharedFlow<SessionChange>(extraBufferCapacity = 64)
    private val adoptLock = Mutex()
    private var actingUserId: String? = null

    override val changes: SharedFlow<SessionChange> = changeFlow.asSharedFlow()

    override val state: StateFlow<AuthState> = combine(sessions.state, user) { session, known ->
        when (session) {
            SessionState.Initializing -> AuthState.Restoring
            is SessionState.Authenticated -> AuthState.SignedIn(known?.takeUnless { it.isGuest })
            is SessionState.Anonymous -> AuthState.Guest(known?.takeIf { it.isGuest })
            is SessionState.Unauthenticated -> AuthState.SignedOut(session.cause?.toReason())
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Restoring)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.events.collect { event ->
                if (event is SessionEvent.Ended) {
                    val ended = actingUserId
                    // A sign-in adopted between the end and this handler owns the user now: keep it.
                    if (sessions.state.value is SessionState.Unauthenticated) {
                        actingUserId = null
                        user.value = null
                    }
                    changeFlow.emit(SessionChange.SignedOut(ended, event.cause.toReason()))
                }
            }
        }
    }

    override suspend fun restore() {
        sessions.restore()
        val current = sessions.awaitReady()
        if (current is SessionState.Authenticated || current is SessionState.Anonymous) {
            actingUserId = sessions.currentTokens()?.let { userIdClaim(it.accessToken) }
            refreshUser()
        }
    }

    override suspend fun logout(): LogoutResult {
        // Refresh first when the access token is past expiry, so the farewell calls and the logout
        // call are authenticated.
        sessions.accessToken()
        // Farewell hooks (push unregister, …) while the bearer is valid: the server logout below
        // blacklists the access token. Bounded by SessionConfig.farewellTimeout; failures are logged.
        sessions.farewell()
        // Read after the farewell: a hook's call may have rotated the pair.
        val tokens = sessions.currentTokens()
        val revoked = tokens != null && api.logout(LogoutRequest(refreshToken = tokens.refreshToken)) is StapelResult.Ok
        sessions.logout()
        return if (revoked) LogoutResult.RevokedOnServer else LogoutResult.LocalOnly
    }

    override suspend fun logoutEverywhere(): SignOutEverywhereResult {
        val others = sessions.currentTokens() != null && api.deleteSessions() is StapelResult.Ok
        return SignOutEverywhereResult(othersRevoked = others, self = logout())
    }

    override suspend fun refreshUser(): StapelResult<AuthUser, AuthError> = when (val me = api.getMe()) {
        is StapelResult.Ok -> {
            val mapped = me.value.toDomain()
            if (sessions.currentTokens() != null) {
                user.value = mapped
                actingUserId = mapped.id
            }
            StapelResult.Ok(mapped)
        }
        is StapelResult.Err -> StapelResult.Err(AuthError.of(me.error))
    }

    override suspend fun ensureGuest(): StapelResult<AuthUser, AuthError> {
        val current = sessions.awaitReady()
        if (current is SessionState.Authenticated || current is SessionState.Anonymous) {
            return user.value?.let { StapelResult.Ok(it) } ?: refreshUser()
        }
        return when (val minted = api.anonymous(AnonymousAuth(deviceId = deviceId()))) {
            is StapelResult.Ok -> StapelResult.Ok(adopt(minted.value).user)
            is StapelResult.Err -> StapelResult.Err(AuthError.of(minted.error))
        }
    }

    /** Turns a login-union answer into a session (adopted) or a challenge. */
    internal suspend fun complete(answer: stapel.auth.api.model.LoginResponse): Completion =
        when (val step = answer.toStep(clock.now())) {
            is LoginStep.Session -> sessionOrRefusal(step.response)
            is LoginStep.Totp -> Completion.Done(SignInOutcome.TotpRequired(step.challenge))
            is LoginStep.FirstLogin -> Completion.Done(SignInOutcome.FirstLoginRequired(step.requires, step.challenge))
            is LoginStep.Unknown -> Completion.Refused(AuthError(AuthErrorReason.Unsupported, null))
        }

    /** Turns a plain session answer (`AuthResponse`) into an adopted session. */
    internal suspend fun sessionOrRefusal(response: AuthResponse): Completion =
        if (response.kind() == SignInKind.Rejected) {
            Completion.Refused(AuthError(AuthErrorReason.Other, null))
        } else {
            Completion.Done(adopt(response))
        }

    /** Persists the pair through the session manager, publishes the user and the identity change. */
    internal suspend fun adopt(response: AuthResponse): SignInOutcome.Session = adoptLock.withLock {
        val wasGuest = sessions.state.value is SessionState.Anonymous
        val previousId = actingUserId ?: sessions.currentTokens()?.let { userIdClaim(it.accessToken) }
        val next = response.user.toDomain()
        val kind = response.kind()
        user.value = next
        actingUserId = next.id
        sessions.adopt(
            TokenPair(accessToken = response.tokens.access, refreshToken = response.tokens.refresh),
            if (next.isGuest) SessionKind.Anonymous else SessionKind.Authenticated,
        )
        val change = when {
            wasGuest && !next.isGuest && previousId != null && previousId == next.id -> SessionChange.Promoted(next)
            wasGuest && !next.isGuest && previousId != null -> SessionChange.Merged(previousId, next)
            else -> SessionChange.SignedIn(next, kind)
        }
        changeFlow.emit(change)
        SignInOutcome.Session(next, kind)
    }
}

internal fun SessionEnd.toReason(): SignOutReason = when (this) {
    SessionEnd.Logout -> SignOutReason.UserRequested
    SessionEnd.Expired -> SignOutReason.Expired
    SessionEnd.Revoked -> SignOutReason.Revoked
    SessionEnd.AccountDisabled -> SignOutReason.AccountDisabled
    SessionEnd.StorageLost -> SignOutReason.StorageLost
    SessionEnd.Unknown -> SignOutReason.Other
}

/** The `user_id` claim of an access JWT, read without verification (identity bookkeeping only). */
internal fun userIdClaim(token: String): String? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    return try {
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString()
        val json = Json.parseToJsonElement(payload) as? JsonObject ?: return null
        (json["user_id"] as? JsonPrimitive)?.content ?: (json["sub"] as? JsonPrimitive)?.content
    } catch (_: IllegalArgumentException) {
        null
    }
}
