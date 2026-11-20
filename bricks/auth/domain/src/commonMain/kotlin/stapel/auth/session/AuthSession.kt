package stapel.auth.session

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import stapel.auth.error.AuthError
import stapel.auth.model.AuthUser
import stapel.auth.model.SignInKind
import stapel.result.StapelResult

/** Who is acting, as the auth library sees it. */
public sealed interface AuthState {
    /** The stored session has not been read yet (cold start). Never shown as "signed out". */
    public data object Restoring : AuthState

    /** No session. [reason] is null when there was none in this process. */
    public data class SignedOut(
        /** Why the last session ended. */
        val reason: SignOutReason?,
    ) : AuthState

    /** A guest session. [user] is null until the profile was read after a cold start. */
    public data class Guest(
        /** The guest user, when known. */
        val user: AuthUser?,
    ) : AuthState

    /** A signed-in account. [user] is null until the profile was read after a cold start. */
    public data class SignedIn(
        /** The user, when known. */
        val user: AuthUser?,
    ) : AuthState
}

/** Why a session ended. */
public enum class SignOutReason {
    /** The user signed out on this device. */
    UserRequested,

    /** Revoked server-side: another device, an administrator, a ban (`refresh_revoked`, `token_revoked`). */
    Revoked,

    /** The refresh token expired or is invalid (`refresh_invalid`, `refresh_not_provided`). */
    Expired,

    /** The account is disabled (`account_disabled`). */
    AccountDisabled,

    /** The secure store could not be read (key invalidated, keychain item lost). */
    StorageLost,

    /** Any other definitive refusal. */
    Other,
}

/** A transition that changed the acting identity. One event per transition. */
public sealed interface SessionChange {
    /** A sign-in (or the first guest session). */
    public data class SignedIn(
        /** The user now acting. */
        val user: AuthUser,
        /** How the sign-in ended. */
        val kind: SignInKind,
    ) : SessionChange

    /** A guest was folded into an existing account: the user id changed. */
    public data class Merged(
        /** Id of the guest that disappeared. */
        val fromGuestId: String,
        /** The account it was merged into. */
        val into: AuthUser,
    ) : SessionChange

    /** A guest was promoted in place: same id, no longer a guest. */
    public data class Promoted(
        /** The promoted user. */
        val user: AuthUser,
    ) : SessionChange

    /** The session ended. */
    public data class SignedOut(
        /** Id of the user whose session ended, when it was known. */
        val userId: String?,
        /** Why. */
        val reason: SignOutReason,
    ) : SessionChange
}

/** What a sign-out achieved on the server. Local state is cleared in every case. */
public enum class LogoutResult {
    /** The server revoked the session. */
    RevokedOnServer,

    /** Only local state was cleared; the server may still hold the session (offline, or it refused). */
    LocalOnly,
}

/** What "sign out everywhere" achieved. Local state is cleared in every case. */
public data class SignOutEverywhereResult(
    /** True when the server revoked every other session. */
    val othersRevoked: Boolean,
    /** What happened to this device's session. */
    val self: LogoutResult,
)

/**
 * The auth-shaped view of the session: identity semantics (guest, merge, reasons) over
 * `stapel-session`, which owns the tokens, storage and the refresh single-flight.
 */
public interface AuthSession {
    /** Current identity. Starts at [AuthState.Restoring]. */
    public val state: StateFlow<AuthState>

    /** One event per identity change; buffered for late collectors only while they subscribe. */
    public val changes: SharedFlow<SessionChange>

    /** Reads the stored session (idempotent) and, when one exists, re-reads the profile in the background. */
    public suspend fun restore()

    /**
     * Signs this device out in three steps: the session's farewell hooks (push unregister, …) while
     * the bearer is still valid, bounded by `SessionConfig.farewellTimeout`, a failing or slow hook
     * never blocking the rest; then the server-side revoke when possible; then always the local end.
     * Never throws.
     */
    public suspend fun logout(): LogoutResult

    /** Revokes every other session (`DELETE /sessions/`), then signs this device out. Never throws. */
    public suspend fun logoutEverywhere(): SignOutEverywhereResult

    /** Re-reads `GET /me/` and publishes the user. */
    public suspend fun refreshUser(): StapelResult<AuthUser, AuthError>

    /** Returns the current guest or user, or mints a guest session when there is none. */
    public suspend fun ensureGuest(): StapelResult<AuthUser, AuthError>
}
