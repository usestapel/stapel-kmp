package stapel.cache.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.cache.ManagedStapelCache
import stapel.cache.WipeTarget
import stapel.session.LogoutHook
import stapel.session.SessionManager
import stapel.session.SessionState

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
 * The account is [SessionState.Authenticated.accountId] / [SessionState.Anonymous.accountId] as the
 * session publishes it (read from the stored tokens, so the partition opens before any request); a
 * session whose token names no account gets no account partition.
 *
 * @param scope application scope the binding collects in.
 */
public class CacheSessionBinding(
    private val cache: ManagedStapelCache,
    private val session: SessionManager,
    scope: CoroutineScope,
) : AutoCloseable {
    private val lock = Mutex()
    private var current: String? = null
    private var settledOnce = false
    private val hook: AutoCloseable = session.registerLogoutHook(LogoutHook { endSession() })
    private val job: Job = scope.launch { session.state.collect { follow(it) } }

    private suspend fun follow(state: SessionState) = lock.withLock {
        val account = when (state) {
            SessionState.Initializing -> return@withLock
            is SessionState.Authenticated -> state.accountId
            is SessionState.Anonymous -> state.accountId
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
