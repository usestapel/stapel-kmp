package stapel.network

import stapel.result.VerificationChallenge

/**
 * Where the pipeline gets the bearer token and how it recovers from a 401. Implemented by
 * `stapel-session` (`SessionManager.authenticator`); [None] for a project without sessions.
 */
public interface RequestAuthenticator {
    /**
     * The access token to attach, or null when there is no session. May refresh first when the
     * token is about to expire (an expired token on a public endpoint is silently anonymous on the
     * backend, so reacting to a 401 alone is not enough).
     */
    public suspend fun accessToken(): String?

    /**
     * Called once per logical call on a 401 for a request that carried [failedToken] (null = it
     * carried none). Single-flight across concurrent callers is the implementation's duty.
     */
    public suspend fun refresh(failedToken: String?): RefreshVerdict

    /** Holder of the no-session authenticator. */
    public companion object {
        /** Never attaches a token and never refreshes. */
        public val None: RequestAuthenticator = object : RequestAuthenticator {
            override suspend fun accessToken(): String? = null

            override suspend fun refresh(failedToken: String?): RefreshVerdict = RefreshVerdict.NoSession
        }
    }
}

/** What a refresh attempt concluded. */
public sealed interface RefreshVerdict {
    /** Retry once with [accessToken]. */
    public data class Refreshed(
        /** The token to send on the retry. */
        val accessToken: String,
    ) : RefreshVerdict {
        /** Redacted: tokens are never printed. */
        override fun toString(): String = "Refreshed(***)"
    }

    /** The server said the credential is dead; the session is already torn down. Do not retry. */
    public data object SessionEnded : RefreshVerdict

    /** No verdict (network, 5xx, timeout, 429). The session is untouched. Do not retry. */
    public data object Unavailable : RefreshVerdict

    /** There is no session to refresh. */
    public data object NoSession : RefreshVerdict
}

/** Step-up verification (a 403 carrying a `verification` challenge). Implemented by the auth domain. */
public fun interface VerificationHandler {
    /** Obtains a grant for [challenge] raised by [operationId]; suspends while the user verifies. */
    public suspend fun resolve(challenge: VerificationChallenge, operationId: String): VerificationOutcome
}

/** Result of [VerificationHandler.resolve]. */
public sealed interface VerificationOutcome {
    /** Retry once, sending [token] in `X-Verification-Token` when it is non-null. */
    public data class Verified(
        /** The grant token. */
        val token: String?,
    ) : VerificationOutcome {
        /** Redacted: tokens are never printed. */
        override fun toString(): String = "Verified(${if (token == null) "null" else "***"})"
    }

    /** The user declined or the challenge failed: the 403 is returned to the caller. */
    public data object Declined : VerificationOutcome
}
