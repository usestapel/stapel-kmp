package stapel.session

import kotlin.time.Instant

/** Whether a session exists. Born [Initializing]: nothing may read "signed out" before the vault was read. */
public sealed interface SessionState {
    /** The vault has not been read yet. Nothing that depends on "is there a session" may act. */
    public data object Initializing : SessionState

    /** A signed-in session. [generation] changes on every sign-in (adopt), never on a refresh. */
    public data class Authenticated(
        /** Id of this session within the process; keys session-scoped objects and caches. */
        val generation: Long,
        /**
         * The account the session belongs to: the access token's [ACCOUNT_ID_CLAIM], read when the
         * session is adopted or restored (no request needed). Null when the token carries none.
         */
        val accountId: String? = null,
    ) : SessionState

    /** A guest session: real tokens, anonymous principal. */
    public data class Anonymous(
        /** Id of this session within the process. */
        val generation: Long,
        /** The guest account's id ([ACCOUNT_ID_CLAIM]); null when the token carries none. */
        val accountId: String? = null,
    ) : SessionState

    /** No session. [cause] is null when there never was one in this process. */
    public data class Unauthenticated(
        /** Why the last session ended. */
        val cause: SessionEnd?,
    ) : SessionState
}

/** Why a session ended. */
public enum class SessionEnd {
    /** The user signed out. */
    Logout,

    /** The refresh token is invalid or expired (`error.401.refresh_invalid`, `refresh_not_provided`). */
    Expired,

    /** Revoked server-side (`error.401.refresh_revoked`, `token_revoked`): another device, admin, reuse detection. */
    Revoked,

    /** The account is disabled (`error.401.account_disabled`). */
    AccountDisabled,

    /** The vault could not be read (Keystore key invalidated, Keychain item unreadable). */
    StorageLost,

    /** Any other definitive refusal. */
    Unknown,
}

/** What kind of principal the tokens belong to. */
public enum class SessionKind {
    /** A registered user. */
    Authenticated,

    /** A guest. */
    Anonymous,
}

/** An access/refresh pair. Never printed: [toString] is redacted. */
public class TokenPair(
    /** The access JWT sent as `Authorization: Bearer`. */
    public val accessToken: String,
    /** The refresh token; rotates on every refresh. */
    public val refreshToken: String,
    /** When the access token expires; null = read from the JWT `exp` claim (see [accessExpiry]). */
    public val accessExpiresAt: Instant? = null,
) {
    /** [accessExpiresAt], or the access JWT's `exp` claim read without verification, or null. */
    public val accessExpiry: Instant? get() = accessExpiresAt ?: jwtExpiry(accessToken)

    /** Equal when both tokens and the expiry are equal. */
    override fun equals(other: Any?): Boolean =
        other is TokenPair && other.accessToken == accessToken && other.refreshToken == refreshToken &&
            other.accessExpiresAt == accessExpiresAt

    /** Consistent with [equals]. */
    override fun hashCode(): Int = accessToken.hashCode() * 31 + refreshToken.hashCode()

    /** Redacted: tokens are never printed. */
    override fun toString(): String = "TokenPair(***)"
}

/** One-shot session events. */
public sealed interface SessionEvent {
    /** Tokens rotated; the session continues. */
    public data object Refreshed : SessionEvent

    /** The session ended for [cause]. */
    public data class Ended(
        /** Why. */
        val cause: SessionEnd,
    ) : SessionEvent

    /** A refresh reached no verdict; the session is untouched. Show "reconnecting", never "signed out". */
    public data object RefreshUnavailable : SessionEvent
}

/**
 * Performs the refresh call. Implemented by the authenticating module (stapel-auth), which owns the
 * endpoint and the body. Must use a transport with NO authenticator (no re-entry into refresh) and
 * must not retry on its own.
 */
public fun interface TokenRefresher {
    /** Exchanges [current] for a new pair. A thrown exception counts as [RefreshOutcome.Transient]. */
    public suspend fun refresh(current: TokenPair): RefreshOutcome
}

/** What the refresh endpoint answered. */
public sealed interface RefreshOutcome {
    /** A new pair; the old refresh token is superseded. */
    public data class Rotated(
        /** The new pair. */
        val tokens: TokenPair,
    ) : RefreshOutcome

    /** The server refused the credential definitively; the session ends with [cause]. */
    public data class Rejected(
        /** Why. */
        val cause: SessionEnd,
        /** The refusal's error code, for diagnostics. */
        val code: String? = null,
    ) : RefreshOutcome

    /** No verdict: transport fault, timeout, 5xx, 429. The session is untouched. */
    public data class Transient(
        /** Diagnostic text. */
        val detail: String? = null,
    ) : RefreshOutcome
}

/**
 * Work that needs the session's bearer on an explicit sign-out: unregistering this device's push
 * token, telling a server goodbye. Runs before the tokens are dropped (and before [LogoutHook]s), so
 * an authenticated call still works; see [SessionManager.farewell] for the order and the budget.
 * Never called when a session ends without a sign-out (expired, revoked, storage lost): the bearer
 * is already dead then, and server-side cleanup is the backend's.
 */
public fun interface FarewellHook {
    /** Called at most once per session. A throwing or slow hook never blocks the sign-out. */
    public suspend fun beforeLogout()
}

/** Local erasure on session end (caches, user files). Runs after the tokens are gone, before state publishes. */
public fun interface LogoutHook {
    /** Called once per session end. A throwing hook does not stop the others. */
    public suspend fun onSessionEnd(cause: SessionEnd)
}
