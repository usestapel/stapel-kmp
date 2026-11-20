package stapel.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import stapel.network.RefreshVerdict
import stapel.network.RequestAuthenticator
import stapel.runtime.NetworkStatus
import stapel.runtime.SessionTelemetryEvent
import stapel.runtime.info
import stapel.runtime.warn
import stapel.session.vault.StoredSession
import stapel.session.vault.VaultRead
import kotlin.time.Duration
import kotlin.time.Instant

private const val TAG = "stapel.session"

internal class DefaultSessionManager(
    private val deps: SessionDependencies,
    private val refresher: TokenRefresher,
    private val config: SessionConfig,
) : SessionManager {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<SessionEvent> = mutableEvents.asSharedFlow()

    private val hooks = MutableStateFlow<List<LogoutHook>>(emptyList())
    private val farewells = MutableStateFlow<List<FarewellHook>>(emptyList())

    /** Guards [current], [generation], [inflight], [ending], [leaving], [farewellDone]. Never held across a refresh call or a hook. */
    private val lock = Mutex()
    private var current: StoredSession? = null
    private var generation = 0L
    private var inflight: Deferred<RefreshVerdict>? = null
    private var ending = false

    /** The farewell hooks are running (rule 6a): a rejected refresh or [sessionLost] does not end the session meanwhile. */
    private var leaving = false

    /** The generation whose farewell already ran. */
    private var farewellDone = 0L
    private var rotationInterrupted = false

    private val restoreLock = Mutex()
    private var restored = false

    override suspend fun restore() {
        restoreLock.withLock {
            if (restored) return
            restored = true
            var read = readVault()
            val marker = (read as? VaultRead.Present)?.session?.rotationStartedAt
            if (marker != null) {
                val age = deps.clock.now() - marker
                if (age < config.rotationHandoffWindow) {
                    // Another process may still be persisting its rotation: wait it out once.
                    delay(config.rotationHandoffWindow - age)
                    read = readVault()
                }
                if ((read as? VaultRead.Present)?.session?.rotationStartedAt != null) {
                    rotationInterrupted = true
                    deps.logger.info(TAG) { "previous process died mid-rotation; the next refresh decides" }
                }
            }
            when (read) {
                VaultRead.Empty -> mutableState.value = SessionState.Unauthenticated(null)
                is VaultRead.Unreadable -> {
                    deps.logger.warn(TAG) { "vault unreadable: ${read.reason}" }
                    clearVault()
                    mutableState.value = SessionState.Unauthenticated(SessionEnd.StorageLost)
                    mutableEvents.tryEmit(SessionEvent.Ended(SessionEnd.StorageLost))
                }
                is VaultRead.Present -> {
                    val gen = lock.withLock {
                        current = read.session
                        ++generation
                    }
                    mutableState.value = stateFor(read.session.kind, gen, read.session.tokens)
                }
            }
        }
    }

    override suspend fun awaitReady(): SessionState = state.first { it !is SessionState.Initializing }

    override suspend fun adopt(tokens: TokenPair, kind: SessionKind) {
        val gen = lock.withLock {
            val next = StoredSession(tokens, kind)
            persist(next)
            current = next
            rotationInterrupted = false
            ++generation
        }
        restoreLock.withLock { restored = true }
        mutableState.value = stateFor(kind, gen, tokens)
    }

    override suspend fun accessToken(): String? {
        awaitReady()
        val session = lock.withLock { current } ?: return null
        val token = session.tokens.accessToken
        if (!needsProactiveRefresh(session)) return token
        return when (val verdict = refresh(token)) {
            is RefreshVerdict.Refreshed -> verdict.accessToken
            RefreshVerdict.Unavailable -> token
            RefreshVerdict.SessionEnded, RefreshVerdict.NoSession -> null
        }
    }

    override suspend fun currentTokens(): TokenPair? {
        awaitReady()
        return lock.withLock { current?.tokens }
    }

    private fun needsProactiveRefresh(session: StoredSession): Boolean {
        if (config.proactiveRefreshWindow <= Duration.ZERO) return false
        if (deps.connectivity?.status?.value == NetworkStatus.Offline) return false
        val expiry = session.tokens.accessExpiry ?: return false
        return expiry - deps.clock.now() < effectiveProactiveWindow(config.proactiveRefreshWindow, session.tokens.accessToken, expiry)
    }

    override suspend fun refresh(failedToken: String?): RefreshVerdict {
        awaitReady()
        val job = lock.withLock {
            val session = current ?: return RefreshVerdict.NoSession
            if (failedToken != null && session.tokens.accessToken != failedToken) {
                // Rule 3: the token that failed is already superseded; replay with the current one.
                return RefreshVerdict.Refreshed(session.tokens.accessToken)
            }
            inflight ?: deps.scope.async(start = CoroutineStart.LAZY) { rotate(session) }.also {
                inflight = it
                it.start()
            }
        }
        return job.await()
    }

    private suspend fun rotate(session: StoredSession): RefreshVerdict {
        try {
            val marked = StoredSession(session.tokens, session.kind, rotationStartedAt = deps.clock.now())
            lock.withLock { if (current === session) persist(marked) }
            val outcome = try {
                refresher.refresh(session.tokens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RefreshOutcome.Transient(e::class.simpleName)
            }
            return when (outcome) {
                is RefreshOutcome.Rotated -> {
                    val next = StoredSession(outcome.tokens, session.kind)
                    val applied = lock.withLock {
                        if (current !== session || ending) {
                            false
                        } else {
                            // Rule 5: in the vault before any waiter sees it.
                            persist(next)
                            current = next
                            rotationInterrupted = false
                            true
                        }
                    }
                    if (!applied) {
                        return lock.withLock { current }?.let { RefreshVerdict.Refreshed(it.tokens.accessToken) }
                            ?: RefreshVerdict.SessionEnded
                    }
                    mutableEvents.tryEmit(SessionEvent.Refreshed)
                    RefreshVerdict.Refreshed(next.tokens.accessToken)
                }
                is RefreshOutcome.Rejected -> {
                    if (rotationInterrupted) {
                        deps.telemetry.sessionEvent(
                            SessionTelemetryEvent("rotation_interrupted", mapOf("cause" to outcome.cause.name)),
                        )
                    }
                    // Rule 6a: a sign-out is under way; it ends the session as Logout right after.
                    if (!lock.withLock { leaving }) teardown(outcome.cause, explicit = false, expected = session)
                    RefreshVerdict.SessionEnded
                }
                is RefreshOutcome.Transient -> {
                    lock.withLock { if (current === session) persist(session) }
                    deps.logger.info(TAG) { "refresh reached no verdict: ${outcome.detail}" }
                    mutableEvents.tryEmit(SessionEvent.RefreshUnavailable)
                    RefreshVerdict.Unavailable
                }
            }
        } finally {
            withContext(NonCancellable) { lock.withLock { inflight = null } }
        }
    }

    override suspend fun logout() {
        awaitReady()
        farewell()
        teardown(SessionEnd.Logout, explicit = true, expected = null)
    }

    override suspend fun farewell() {
        awaitReady()
        val run = lock.withLock {
            if (current == null || ending || leaving || farewellDone == generation) {
                false
            } else {
                leaving = true
                farewellDone = generation
                true
            }
        }
        if (!run) return
        try {
            withContext(NonCancellable) {
                val all = farewells.value
                val finished = withTimeoutOrNull(config.farewellTimeout) {
                    coroutineScope {
                        for (hook in all) {
                            launch {
                                try {
                                    hook.beforeLogout()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    deps.logger.warn(TAG, e) { "farewell hook failed" }
                                }
                            }
                        }
                    }
                }
                if (finished == null) deps.logger.warn(TAG) { "farewell hooks cut off after ${config.farewellTimeout}" }
            }
        } finally {
            withContext(NonCancellable) { lock.withLock { leaving = false } }
        }
    }

    override suspend fun sessionLost(cause: SessionEnd): Boolean {
        awaitReady()
        if (lock.withLock { leaving }) return false
        return teardown(cause, explicit = false, expected = null)
    }

    private suspend fun teardown(cause: SessionEnd, explicit: Boolean, expected: StoredSession?): Boolean {
        val proceed = lock.withLock {
            when {
                ending -> false
                expected != null && current !== expected -> false
                current == null -> false
                else -> {
                    ending = true
                    current = null
                    true
                }
            }
        }
        if (!proceed) return false
        try {
            withContext(NonCancellable) {
                clearVault()
                for (hook in hooks.value) {
                    try {
                        hook.onSessionEnd(cause)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        deps.logger.warn(TAG, e) { "logout hook failed" }
                    }
                }
                mutableState.value = SessionState.Unauthenticated(cause)
                mutableEvents.tryEmit(SessionEvent.Ended(cause))
            }
        } finally {
            withContext(NonCancellable) { lock.withLock { ending = false } }
        }
        deps.logger.info(TAG) { "session ended: $cause (explicit=$explicit)" }
        return true
    }

    override fun registerFarewellHook(hook: FarewellHook): AutoCloseable {
        farewells.update { it + hook }
        return AutoCloseable { farewells.update { list -> list - hook } }
    }

    override fun registerLogoutHook(hook: LogoutHook): AutoCloseable {
        hooks.update { it + hook }
        return AutoCloseable { hooks.update { list -> list - hook } }
    }

    override val authenticator: RequestAuthenticator = object : RequestAuthenticator {
        override suspend fun accessToken(): String? = this@DefaultSessionManager.accessToken()

        override suspend fun refresh(failedToken: String?): RefreshVerdict = this@DefaultSessionManager.refresh(failedToken)
    }

    private suspend fun readVault(): VaultRead = try {
        deps.vault.read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VaultRead.Unreadable(e::class.simpleName ?: "read failed")
    }

    /** Writes [session]; a failing vault keeps the session in memory for this process only. */
    private suspend fun persist(session: StoredSession) {
        try {
            deps.vault.write(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deps.logger.warn(TAG, e) { "vault write failed; the session lives in memory until the process ends" }
        }
    }

    private suspend fun clearVault() {
        try {
            deps.vault.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deps.logger.warn(TAG, e) { "vault clear failed" }
        }
    }

    private fun stateFor(kind: SessionKind, generation: Long, tokens: TokenPair): SessionState {
        val account = jwtAccountId(tokens.accessToken)
        return when (kind) {
            SessionKind.Authenticated -> SessionState.Authenticated(generation, account)
            SessionKind.Anonymous -> SessionState.Anonymous(generation, account)
        }
    }
}

/** [configured], clamped to [PROACTIVE_WINDOW_SHARE] of the token's `exp − iat` when it carries `iat`. */
internal fun effectiveProactiveWindow(configured: Duration, accessToken: String, expiry: Instant): Duration {
    val issued = jwtIssuedAt(accessToken) ?: return configured
    val lifetime = expiry - issued
    if (lifetime <= Duration.ZERO) return configured
    return minOf(configured, lifetime * PROACTIVE_WINDOW_SHARE)
}

