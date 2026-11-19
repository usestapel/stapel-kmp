package stapel.cache.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.cache.ManagedStapelCache
import stapel.cache.WipeTarget
import stapel.session.LogoutHook
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenPair
import kotlin.io.encoding.Base64

/**
 * Names the account a token pair belongs to, from local data only (the partition must open at cold
 * start, before any request). `null` = no account partition (reads go to the network, nothing kept).
 */
public fun interface AccountIdResolver {
    /** The account id of [tokens], or `null`. */
    public suspend fun accountId(tokens: TokenPair): String?

    /** Ready-made resolvers. */
    public companion object {
        /**
         * The first present claim of the access JWT's payload among [claims] (read without verifying
         * the signature: it only picks a local directory). Default: `user_id` (the backend's
         * SimpleJWT claim), then `sub`.
         */
        public fun jwtClaim(vararg claims: String = arrayOf("user_id", "sub")): AccountIdResolver =
            AccountIdResolver { tokens -> jwtClaimOf(tokens.accessToken, claims.toList()) }
    }
}

/**
 * Binds the cache's partitions to the session (one binding per app, created in the composition root):
 *
 * - `Initializing`: nothing happens, so user-scoped reads wait in `Loading` (the session-ready gate);
 * - a session (signed in or guest): the account's partition opens — at cold start from the stored
 *   tokens, before any request, so the last known values paint first;
 * - a new sign-in as another account: that account's partition opens; the previous one is kept;
 * - session end (logout, expiry, revocation, any cause): the account's partition is wiped from a
 *   [LogoutHook], which the session runs before it publishes "signed out" — no opt-out;
 * - first settled state: partitions of other accounts (left by a process killed mid-logout) are wiped.
 *
 * @param cache the app's cache.
 * @param session the app's session.
 * @param scope application scope the binding collects in.
 * @param resolver how the account id is read from the tokens.
 */
public class CacheSessionBinding(
    private val cache: ManagedStapelCache,
    private val session: SessionManager,
    scope: CoroutineScope,
    private val resolver: AccountIdResolver = AccountIdResolver.jwtClaim(),
) : AutoCloseable {
    private val lock = Mutex()
    private var current: String? = null
    private var settledOnce = false
    private val hook: AutoCloseable = session.registerLogoutHook(LogoutHook { endSession() })
    private val job: Job = scope.launch { session.state.collect { follow(it) } }

    private suspend fun follow(state: SessionState) = lock.withLock {
        val account = when (state) {
            SessionState.Initializing -> return@withLock
            is SessionState.Authenticated, is SessionState.Anonymous -> session.currentTokens()?.let { resolver.accountId(it) }
            is SessionState.Unauthenticated -> null
        }
        current = account
        cache.setAccount(account)
        if (!settledOnce) {
            settledOnce = true
            cache.wipe(if (account == null) WipeTarget.AllAccounts else WipeTarget.AllAccountsExcept(account))
        }
    }

    private suspend fun endSession() = lock.withLock {
        val ended = current ?: return@withLock
        current = null
        cache.wipe(WipeTarget.Account(ended))
        cache.setAccount(null)
    }

    /** Stops following the session (the cache keeps its current partition). */
    override fun close() {
        job.cancel()
        hook.close()
    }
}

internal fun jwtClaimOf(token: String, claims: List<String>): String? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    val payload = try {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString()
    } catch (_: IllegalArgumentException) {
        return null
    }
    val json = try {
        Json.parseToJsonElement(payload) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    return claims.firstNotNullOfOrNull { claim ->
        (json[claim] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotEmpty() }
    }
}
