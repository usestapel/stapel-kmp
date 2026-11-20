package stapel.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import stapel.result.StapelResult
import stapel.runtime.AppPhase
import stapel.runtime.info
import stapel.runtime.warn
import stapel.session.SessionState
import kotlin.time.Duration.Companion.seconds

/** Bound of the provider-token deletion in the logout hook: a hung SDK call never holds a sign-out. */
private val DELETE_BUDGET = 5.seconds

internal class DefaultPushController(
    private val deps: PushDependencies,
    private val options: PushOptions,
) : PushController {
    private val provider = deps.provider
    private val availability: ProviderAvailability =
        provider?.availability ?: ProviderAvailability.Unsupported("no push provider installed")
    private val ready get() = availability == ProviderAvailability.Ready

    private val mutableState = MutableStateFlow(
        PushState(
            availability = availability,
            permission = if (ready) PushPermission.NotDetermined else PushPermission.Unsupported,
            registration = Registration.Off,
            enabled = false,
        ),
    )
    override val state: StateFlow<PushState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PushEvent>(extraBufferCapacity = 64)
    override val events: Flow<PushEvent> = mutableEvents.asSharedFlow()

    /** Serializes every registry/provider operation of the lifecycle. Never held across the rationale. */
    private val ops = Mutex()
    private var record = PushRecord()
    private val loaded = MutableStateFlow(false)

    /** The provider token was already deleted for the session end in progress (by the farewell). */
    private var deletedForEnd = false

    private val opener = MutableStateFlow<PushLinkOpener?>(null)
    private val opens = Channel<PushEvent.Opened>(Channel.UNLIMITED)

    fun start() {
        deps.sessions.registerFarewellHook { farewell() }
        deps.sessions.registerLogoutHook { onSessionEnd() }
        deps.scope.launch {
            ops.withLock {
                record = deps.store.load()
                mutableState.update { it.copy(enabled = record.enabled) }
            }
            loaded.value = true
            val p = provider
            if (p == null || !ready) {
                log("unavailable ${availability.marker()}")
                return@launch
            }
            p.setForegroundPresentation(options.foreground)
            p.ensureChannels(options.routing.channels)
            refreshPermission()
            launch { p.events.collect { onEvent(it) } }
            launch { p.tokens.collect { onRotated(it) } }
            launch { routeOpens() }
            launch { deps.sessions.state.collect { onSession(it) } }
            deps.lifecycle?.let { lifecycle ->
                launch { lifecycle.phase.drop(1).collect { if (it == AppPhase.Foreground) onForeground() } }
            }
        }
    }

    // --- person's choice -----------------------------------------------------------------------

    override suspend fun enable(): PushState {
        loaded.first { it }
        val p = provider
        if (p == null || !ready) {
            log("unavailable ${availability.marker()}")
            return state.value
        }
        ops.withLock {
            record = record.copy(enabled = true, optedOut = false)
            save()
            mutableState.update { it.copy(enabled = true) }
        }
        // Rationale and OS prompt outside the lock: a person may take minutes.
        val permission = ask(p, provisional = false)
        ops.withLock {
            setPermission(permission)
            if (!permission.allowsDelivery) {
                setRegistration(Registration.Blocked)
            } else {
                syncLocked()
            }
        }
        return state.value
    }

    override suspend fun disable(): PushState {
        loaded.first { it }
        ops.withLock {
            record = record.copy(enabled = false, optedOut = true)
            mutableState.update { it.copy(enabled = false) }
            if (ready) {
                unregisterLocked()
                deleteTokenLocked()
            }
            record = record.copy(registeredFingerprint = null, registeredAccount = null)
            save()
            setRegistration(Registration.Off)
        }
        return state.value
    }

    /** NotDetermined → rationale → OS prompt; anything else is answered as it is. */
    private suspend fun ask(p: PushProvider, provisional: Boolean): PushPermission {
        val current = p.permission()
        if (current != PushPermission.NotDetermined) return current
        if (provisional) return p.requestPermission(provisional = true)
        val proceed = deps.rationale?.let { rationale ->
            try {
                rationale.explain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deps.logger.warn(PUSH_LOG_TAG, e) { "push rationale failed" }
                false
            }
        } ?: true
        return if (proceed) p.requestPermission(provisional = false) else current
    }

    // --- session --------------------------------------------------------------------------------

    private suspend fun onSession(session: SessionState) {
        when (session) {
            is SessionState.Authenticated -> {
                if (options.ask == AskPolicy.AfterSignIn && !record.enabled && !record.optedOut) {
                    val p = provider ?: return
                    // iOS: provisional authorization shows no prompt; Android: rationale + prompt.
                    val permission = ask(p, provisional = p.platform == DevicePlatform.Ios)
                    ops.withLock {
                        setPermission(permission)
                        if (permission.allowsDelivery) {
                            record = record.copy(enabled = true)
                            save()
                            mutableState.update { it.copy(enabled = true) }
                        }
                    }
                }
                ops.withLock { syncLocked() }
            }
            is SessionState.Anonymous, is SessionState.Unauthenticated, SessionState.Initializing ->
                ops.withLock { if (state.value.registration != Registration.Blocked) setRegistration(Registration.Off) }
        }
    }

    /** Explicit sign-out, bearer still valid: unregister, then delete the token. */
    private suspend fun farewell() {
        if (!ready) return
        ops.withLock {
            unregisterLocked()
            deletedForEnd = deleteTokenLocked()
        }
    }

    /** Every session end, after the tokens are gone: the next account must get a fresh token. */
    private suspend fun onSessionEnd() {
        if (!ready) return
        ops.withLock {
            if (!deletedForEnd) withTimeoutOrNull(DELETE_BUDGET) { deleteTokenLocked() }
            deletedForEnd = false
            record = record.copy(registeredFingerprint = null, registeredAccount = null)
            save()
            if (state.value.registration != Registration.Blocked) setRegistration(Registration.Off)
        }
    }

    private suspend fun onRotated(token: PushToken) {
        ops.withLock {
            if (token.fingerprint == record.registeredFingerprint) return
            syncLocked()
        }
    }

    private suspend fun onForeground() {
        val p = provider ?: return
        val permission = p.permission()
        ops.withLock {
            val before = state.value.permission
            setPermission(permission)
            if (permission == before) return
            if (!permission.allowsDelivery) {
                // Revoked in the system settings: stop the server sending; keep the choice so a re-grant restores.
                unregisterLocked()
                record = record.copy(registeredFingerprint = null, registeredAccount = null)
                save()
                if (record.enabled) setRegistration(Registration.Blocked)
            } else {
                syncLocked()
            }
        }
    }

    private suspend fun refreshPermission() {
        val p = provider ?: return
        val permission = p.permission()
        ops.withLock { setPermission(permission) }
    }

    // --- registration (callers hold [ops]) ---------------------------------------------------------

    /** Registers the current token when everything allows it. */
    private suspend fun syncLocked() {
        val p = provider ?: return
        if (!ready || !record.enabled) return
        val session = deps.sessions.state.value as? SessionState.Authenticated ?: return
        val registry = deps.registry ?: return
        val platform = p.platform ?: return
        val permission = p.permission()
        setPermission(permission)
        if (!permission.allowsDelivery) {
            setRegistration(Registration.Blocked)
            return
        }
        setRegistration(Registration.Registering)
        val account = session.accountId
        // A previous account's registration that was never cleared (process died mid-sign-out):
        // its token is not this account's to reuse.
        if (record.registeredAccount != null && record.registeredAccount != account) deleteTokenLocked()

        var token = when (val t = p.token()) {
            is StapelResult.Ok -> t.value
            is StapelResult.Err -> return failed("token ${t.error.code.value}")
        }
        if (token.fingerprint == record.registeredFingerprint) {
            val status = registry.status(token)
            if (status is StapelResult.Ok && status.value == DeviceStatus.Inactive) {
                log("token inactive; minting a new one")
                token = freshTokenLocked(p) ?: return failed("token after inactive")
            }
        }
        val deviceId = deps.identity.deviceId()
        var result = registry.register(PushDevice(token, platform, deviceId))
        if (result is StapelResult.Err && result.error is RegistryError.HeldByAnotherAccount) {
            // One new token and one retry; a second 409 is a failure, not a loop.
            token = freshTokenLocked(p) ?: return failed("token after conflict")
            result = registry.register(PushDevice(token, platform, deviceId))
        }
        when (result) {
            is StapelResult.Ok -> {
                record = record.copy(registeredFingerprint = token.fingerprint, registeredAccount = account)
                save()
                setRegistration(Registration.Registered)
            }
            is StapelResult.Err -> when (result.error) {
                is RegistryError.NotAllowed -> setRegistration(Registration.Off)
                else -> failed("register ${result.error.code.value}")
            }
        }
    }

    private suspend fun freshTokenLocked(p: PushProvider): PushToken? {
        deleteTokenLocked()
        return (p.token() as? StapelResult.Ok)?.value
    }

    /** Removes this installation's row when one was registered. */
    private suspend fun unregisterLocked() {
        val registry = deps.registry ?: return
        if (record.registeredFingerprint == null) return
        when (val r = registry.unregister(deps.identity.deviceId())) {
            is StapelResult.Ok -> log("unregistered")
            is StapelResult.Err -> log("unregister failed ${r.error.code.value}")
        }
    }

    /** True when the provider confirmed the deletion. */
    private suspend fun deleteTokenLocked(): Boolean {
        val p = provider ?: return false
        return when (val r = p.deleteToken()) {
            is StapelResult.Ok -> true
            is StapelResult.Err -> {
                log("token delete failed ${r.error.code.value}")
                false
            }
        }
    }

    private fun failed(what: String) {
        log("failed $what")
        setRegistration(Registration.Failed)
    }

    // --- events and taps -------------------------------------------------------------------------

    private suspend fun onEvent(event: PushEvent) {
        when (event) {
            is PushEvent.Received -> {
                log("received ${event.message.type} fg=${event.foreground}")
                mutableEvents.emit(event)
            }
            is PushEvent.Opened -> {
                mutableEvents.emit(event)
                opens.send(event)
            }
        }
    }

    override fun attach(opener: PushLinkOpener): AutoCloseable {
        this.opener.value = opener
        return AutoCloseable { this.opener.compareAndSet(opener, null) }
    }

    private suspend fun routeOpens() {
        for (event in opens) {
            val target = opener.filterNotNull().first()
            route(event, target)
        }
    }

    private suspend fun route(event: PushEvent.Opened, target: PushLinkOpener) {
        val message = event.message
        val link = options.routing.linkKeys.firstNotNullOfOrNull { key -> message.data[key]?.takeIf { it.isNotBlank() } }
        val outcome = when {
            link != null && target.open(link) -> "opened"
            link != null -> {
                options.routing.fallbackUri?.let { target.open(it) }
                "refused"
            }
            else -> {
                options.routing.fallbackUri?.let { target.open(it) }
                "none"
            }
        }
        log("opened ${message.type} link=$outcome")
        for (listener in deps.openListeners) {
            try {
                listener.onOpened(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deps.logger.warn(PUSH_LOG_TAG, e) { "push open listener failed" }
            }
        }
    }

    // --- helpers ------------------------------------------------------------------------------

    private suspend fun save() {
        try {
            deps.store.save(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deps.logger.warn(PUSH_LOG_TAG, e) { "push store write failed" }
        }
    }

    private fun setPermission(permission: PushPermission) {
        mutableState.update { it.copy(permission = permission) }
    }

    private fun setRegistration(registration: Registration) {
        val before = mutableState.value.registration
        mutableState.update { it.copy(registration = registration) }
        if (before != registration) log("state ${registration.name.lowercase()}")
    }

    private fun log(text: String) {
        deps.logger.info(PUSH_LOG_TAG) { "stapel.push $text" }
    }
}

private fun ProviderAvailability.marker(): String = when (this) {
    ProviderAvailability.Ready -> "ready"
    is ProviderAvailability.Unconfigured -> "unconfigured ($reason)"
    is ProviderAvailability.Unsupported -> "unsupported ($reason)"
}
