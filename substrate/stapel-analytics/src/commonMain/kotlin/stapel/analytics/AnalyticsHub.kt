package stapel.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import stapel.runtime.Analytics
import stapel.runtime.AppLifecycle
import stapel.runtime.AppPhase
import stapel.runtime.IdGenerator
import stapel.runtime.LogLevel
import stapel.runtime.StapelLogger
import stapel.runtime.debug
import stapel.runtime.info
import stapel.runtime.warn
import kotlin.time.Clock
import kotlin.time.Instant

/** The control half of the hub; the emit half is [stapel.runtime.Analytics], which the hub also implements. */
public interface AnalyticsControl {
    /** The current answer; starts from [AnalyticsOptions.consentDefault] until the stored one is read. */
    public val consent: StateFlow<Consent>

    /**
     * Records the person's answer (persisted per installation). Granted switches vendor collection on
     * for the sinks it covers and flushes; Denied switches it off, drops the buffer and the persisted
     * queue, and turns every later emit into a no-op. Returns once applied.
     */
    public suspend fun setConsent(consent: Consent)

    /** Sets (null: removes) a user property on every sink consent covers, now and after a later grant. */
    public fun setUserProperty(name: String, value: String?)

    /** Delivers everything deliverable now; returns when the attempt settled. */
    public suspend fun flush()

    /** Per-sink delivery state, for a diagnostics screen. */
    public val sinks: StateFlow<List<SinkStatus>>
}

/** The log tag of the hub's markers (`stapel.analytics consent granted`, `stapel.analytics unavailable firebase …`). */
public const val ANALYTICS_LOG_TAG: String = "stapel.analytics"

/**
 * The analytics hub: consent gate, registry check, PII guard, durable queue and fan-out to
 * [AnalyticsSink]s with per-sink delivery state. Bricks and presenters see it only as
 * [stapel.runtime.Analytics]; the app and a consent screen use [AnalyticsControl].
 *
 * Rules it keeps (each proven by a test that fails when it is broken):
 * - before consent covers a sink's purposes, nothing reaches that sink and its vendor collection is
 *   switched off — `setCollectionEnabled(false)` reaches every sink before the hub does anything else;
 * - pending consent buffers at most [AnalyticsOptions.pendingBufferLimit] events (oldest dropped);
 *   denied drops memory and storage and ignores later emits;
 * - a batch a sink took is never re-sent to it because another sink failed; events a sink rejected
 *   ([SinkReceipt.Partial]) are never re-sent; a sink that throws is retried with backoff and dropped
 *   after [BatchOptions.maxAttempts];
 * - a sink that is not [SinkAvailability.Ready] is skipped and reported, never an error;
 * - undelivered events survive a restart (the [AnalyticsStorage] `queue` value).
 *
 * All work runs in one coroutine in [scope], in call order; emits never suspend.
 *
 * @param sinks the fan-out targets (ids must be unique).
 * @param storage where consent, queue and anonymous id persist.
 * @param options consent default, guard, registry, batching.
 * @param lifecycle flush on background, new session after [AnalyticsOptions.sessionTimeout]; null = neither.
 * @param clock event time stamps.
 * @param ids session ids and the hub's own anonymous id.
 * @param scope the app scope; cancelling it stops the hub.
 * @param logger markers and warnings (tag [ANALYTICS_LOG_TAG]); never a property value.
 * @param anonymousId overrides the hub's own anonymous id.
 */
public class AnalyticsHub(
    sinks: List<AnalyticsSink>,
    private val storage: AnalyticsStorage,
    private val options: AnalyticsOptions,
    private val lifecycle: AppLifecycle?,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val scope: CoroutineScope,
    private val logger: StapelLogger,
    private val anonymousId: AnalyticsAnonymousId? = null,
) : Analytics, AnalyticsControl {
    private val sinkList: List<AnalyticsSink> = sinks.toList()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val consentState = MutableStateFlow(
        Consent(if (options.consentDefault == ConsentDefault.OptOut) ConsentState.Granted else ConsentState.Pending),
    )
    private val status = MutableStateFlow(sinkList.map { SinkStatus(it.id, safeAvailability(it), null, 0) })

    override val consent: StateFlow<Consent> = consentState.asStateFlow()
    override val sinks: StateFlow<List<SinkStatus>> = status.asStateFlow()

    private val queue = ArrayDeque<AnalyticsEvent>()
    private var inflight: Inflight? = null
    private var userHash: String? = null
    private var anon = ""
    private var sessionId = ""
    private var backgroundSince: Instant? = null
    private var seq = 0L
    private var dropped = 0
    private var retry: Job? = null
    private val userProps = LinkedHashMap<String, String?>()
    private val warnedPii = mutableSetOf<String>()
    private val warnedRegistry = mutableSetOf<String>()
    private val collection = mutableMapOf<String, Boolean>()

    init {
        require(sinkList.map { it.id }.toSet().size == sinkList.size) { "two analytics sinks share an id: ${sinkList.map { it.id }}" }
        // The consent gate starts closed on the vendor side, before anything else happens.
        sinkList.forEach { applyCollection(it, false) }
        scope.launch { run() }
        lifecycle?.let { lc -> scope.launch { lc.phase.drop(1).collect { commands.trySend(Command.Phase(it, clock.now())) } } }
        scope.launch {
            while (true) {
                delay(options.batch.flushInterval)
                commands.trySend(Command.Tick)
            }
        }
    }

    /** Queues a named event (never suspends); dropped while consent is denied. */
    override fun track(event: String, props: Map<String, String>) {
        commands.trySend(Command.Emit(EventKind.Track, event, props, clock.now()))
    }

    /** Queues a screen view (never suspends); dropped while consent is denied. */
    override fun page(name: String, props: Map<String, String>) {
        commands.trySend(Command.Emit(EventKind.Page, name, props, clock.now()))
    }

    /** [userHash] is already hashed ([sha256Hex]); null signs out and resets every sink. */
    override fun identify(userHash: String?) {
        commands.trySend(Command.Identify(userHash, clock.now()))
    }

    override suspend fun setConsent(consent: Consent) {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.SetConsent(consent, done))
        done.await()
    }

    override fun setUserProperty(name: String, value: String?) {
        commands.trySend(Command.UserProperty(name, value))
    }

    override suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Flush(done))
        done.await()
    }

    private sealed interface Command {
        data class Emit(val kind: EventKind, val name: String, val props: Map<String, String>, val at: Instant) : Command
        data class Identify(val userHash: String?, val at: Instant) : Command
        data class SetConsent(val consent: Consent, val done: CompletableDeferred<Unit>) : Command
        data class UserProperty(val name: String, val value: String?) : Command
        data class Flush(val done: CompletableDeferred<Unit>) : Command
        data class Phase(val phase: AppPhase, val at: Instant) : Command
        data object Tick : Command
    }

    private class Inflight(val events: List<AnalyticsEvent>, val delivered: MutableSet<String>, var attempts: Int)

    private suspend fun run() {
        load()
        while (true) {
            var command = commands.receive()
            while (true) {
                handle(command)
                command = commands.tryReceive().getOrNull() ?: break
            }
            persistQueue()
        }
    }

    private suspend fun load() {
        val stored = guarded("read consent") { storage.read(KEY_CONSENT) }?.let(::decodeConsent)
        if (stored != null) consentState.value = stored
        val saved = guarded("read queue") { storage.read(KEY_QUEUE) }?.let(::decodeQueue)
        if (saved != null && consentState.value.analytics != ConsentState.Denied) {
            saved.inflight?.let { inflight = Inflight(it.events.map { e -> e.toEvent() }, it.delivered.toMutableSet(), it.attempts) }
            saved.queue.forEach { queue.addLast(it.toEvent()) }
        }
        anon = anonymousId?.let { source -> guarded("anonymous id") { source.get() } }
            ?: guarded("read anon") { storage.read(KEY_ANON) }?.takeIf { it.isNotBlank() }
            ?: ids.uuid().also { id -> guarded("write anon") { storage.write(KEY_ANON, id) } }
        sessionId = ids.uuid()
        logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics consent ${consentState.value.analytics.wire}" }
        sinkList.forEach { sink ->
            when (val a = safeAvailability(sink)) {
                SinkAvailability.Ready -> Unit
                is SinkAvailability.Unconfigured -> logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics unavailable ${sink.id} unconfigured ${a.reason}" }
                is SinkAvailability.Unsupported -> logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics unavailable ${sink.id} unsupported ${a.reason}" }
            }
        }
        applyConsentToSinks()
    }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Emit -> emit(command.kind, command.name, command.props, command.at)
            is Command.Identify -> {
                if (command.userHash == null) {
                    userHash = null
                    sinkList.forEach { sink -> safely(sink, "reset") { sink.reset() } }
                } else {
                    userHash = command.userHash
                    emit(EventKind.Identify, "identify", emptyMap(), command.at)
                }
            }
            is Command.SetConsent -> {
                applyConsent(command.consent)
                command.done.complete(Unit)
            }
            is Command.UserProperty -> {
                userProps[command.name] = command.value
                sinkList.filter { consentCovers(it) }.forEach { sink -> safely(sink, "user property") { sink.setUserProperty(command.name, command.value) } }
            }
            is Command.Flush -> {
                deliver()
                command.done.complete(Unit)
            }
            is Command.Phase -> when (command.phase) {
                AppPhase.Background -> {
                    backgroundSince = command.at
                    deliver()
                }
                AppPhase.Foreground -> {
                    val since = backgroundSince
                    backgroundSince = null
                    if (since != null && command.at - since >= options.sessionTimeout) sessionId = ids.uuid()
                }
            }
            Command.Tick -> if (queue.isNotEmpty() || inflight != null) deliver()
        }
    }

    private suspend fun emit(kind: EventKind, name: String, props: Map<String, String>, at: Instant) {
        if (consentState.value.analytics == ConsentState.Denied) return
        val registry = options.registry
        if (registry != null && kind != EventKind.Identify && kind != EventKind.Page && !name.startsWith("flow.") && name !in registry) {
            if (warnedRegistry.add(name)) {
                logger.warn(ANALYTICS_LOG_TAG) { "stapel.analytics unregistered $name ${if (options.unregistered == Unregistered.Drop) "dropped" else "delivered"}" }
            }
            if (options.unregistered == Unregistered.Drop) return
        }
        val guarded = when (val outcome = guardPii(props, options.piiGuard)) {
            PiiOutcome.Reject -> {
                logger.warn(ANALYTICS_LOG_TAG) { "stapel.analytics pii rejected $name" }
                return
            }
            is PiiOutcome.Keep -> {
                if (outcome.hit && warnedPii.add(name)) {
                    logger.warn(ANALYTICS_LOG_TAG) { "stapel.analytics pii ${if (options.piiGuard == PiiGuard.Strip) "redacted" else "kept"} in $name" }
                }
                outcome.props
            }
        }
        seq++
        queue.addLast(AnalyticsEvent("${at.toEpochMilliseconds()}-$seq", kind, name, guarded, userHash, anon, sessionId, at))
        while (queue.size + (inflight?.events?.size ?: 0) > options.pendingBufferLimit && queue.isNotEmpty()) {
            queue.removeFirst()
            dropped++
            if (dropped == 1 || dropped % 100 == 0) {
                logger.warn(ANALYTICS_LOG_TAG) { "stapel.analytics buffer full: $dropped oldest event(s) dropped" }
            }
        }
        if (queue.size >= options.batch.maxSize) deliver()
    }

    private suspend fun applyConsent(next: Consent) {
        consentState.value = next
        guarded("write consent") { storage.write(KEY_CONSENT, encodeConsent(next)) }
        logger.info(ANALYTICS_LOG_TAG) { "stapel.analytics consent ${next.analytics.wire}" }
        if (next.analytics == ConsentState.Denied) {
            queue.clear()
            inflight = null
            retry?.cancel()
            retry = null
            guarded("delete queue") { storage.write(KEY_QUEUE, null) }
        }
        applyConsentToSinks()
        if (next.analytics == ConsentState.Granted) deliver()
    }

    private fun applyConsentToSinks() {
        sinkList.forEach { sink ->
            val on = consentCovers(sink)
            applyCollection(sink, on)
            if (on) userProps.forEach { (k, v) -> safely(sink, "user property") { sink.setUserProperty(k, v) } }
        }
    }

    private fun applyCollection(sink: AnalyticsSink, enabled: Boolean) {
        if (collection[sink.id] == enabled) return
        collection[sink.id] = enabled
        safely(sink, "collection") { sink.setCollectionEnabled(enabled) }
    }

    private fun consentCovers(sink: AnalyticsSink): Boolean = consentState.value.grants(sink.purposes + ConsentPurpose.Analytics)

    /** Delivers batch after batch until the queue is empty or a batch is waiting for a retry. */
    private suspend fun deliver() {
        if (consentState.value.analytics != ConsentState.Granted) return
        while (true) {
            val current = inflight ?: run {
                if (queue.isEmpty()) return
                val n = minOf(options.batch.maxSize, queue.size)
                Inflight(List(n) { queue.removeFirst() }, mutableSetOf(), 0).also { inflight = it }
            }
            if (!attempt(current)) return
        }
    }

    /** One attempt of [current] on every sink that still owes it; true when the batch is settled. */
    private suspend fun attempt(current: Inflight): Boolean {
        val targets = sinkList.filter { it.id !in current.delivered && safeAvailability(it) == SinkAvailability.Ready && consentCovers(it) }
        refreshAvailability()
        if (targets.isEmpty()) {
            // Nobody can take it now (all delivered, or every owing sink unavailable): settled for the available ones.
            inflight = null
            return true
        }
        current.attempts++
        val receipts = coroutineScope {
            targets.map { sink -> async { sink to receiptOf(sink, current.events) } }.awaitAll()
        }
        for ((sink, receipt) in receipts) {
            when (receipt) {
                SinkReceipt.Accepted -> {
                    current.delivered += sink.id
                    updateStatus(sink.id, null, current.events.size)
                    logger.debug(ANALYTICS_LOG_TAG) { "stapel.analytics delivered ${current.events.size} to ${sink.id}" }
                }
                is SinkReceipt.Partial -> {
                    current.delivered += sink.id
                    val taken = current.events.count { it.id !in receipt.rejected }
                    updateStatus(sink.id, null, taken)
                    logger.warn(ANALYTICS_LOG_TAG) { "stapel.analytics ${sink.id} rejected ${receipt.rejected.size} event(s): ${receipt.rejected.values.toSet()}" }
                }
                is SinkReceipt.Retry -> updateStatus(sink.id, receipt.detail ?: "retry", 0)
            }
        }
        val owing = targets.filter { it.id !in current.delivered }
        if (owing.isEmpty()) {
            inflight = null
            return true
        }
        if (current.attempts >= options.batch.maxAttempts) {
            logger.warn(ANALYTICS_LOG_TAG) {
                "stapel.analytics dropped a batch of ${current.events.size} after ${current.attempts} attempts; undelivered to ${owing.map { it.id }}"
            }
            inflight = null
            return true
        }
        scheduleRetry(options.batch.backoff(current.attempts))
        return false
    }

    private suspend fun receiptOf(sink: AnalyticsSink, events: List<AnalyticsEvent>): SinkReceipt = try {
        sink.deliver(events)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SinkReceipt.Retry(e::class.simpleName ?: "exception")
    }

    private fun scheduleRetry(after: kotlin.time.Duration) {
        if (retry?.isActive == true) return
        retry = scope.launch {
            delay(after)
            commands.trySend(Command.Tick)
        }
    }

    private fun refreshAvailability() {
        status.value = status.value.map { s -> sinkList.first { it.id == s.id }.let { s.copy(availability = safeAvailability(it)) } }
    }

    private fun updateStatus(id: String, error: String?, delivered: Int) {
        status.value = status.value.map { if (it.id == id) it.copy(lastError = error, delivered = it.delivered + delivered) else it }
    }

    private suspend fun persistQueue() {
        if (consentState.value.analytics == ConsentState.Denied) return
        val current = inflight
        val value = if (current == null && queue.isEmpty()) null else json.encodeToString(
            StoredQueue.serializer(),
            StoredQueue(
                inflight = current?.let { StoredInflight(it.events.map(::StoredEvent), it.delivered.sorted(), it.attempts) },
                queue = queue.map(::StoredEvent),
            ),
        )
        guarded("write queue") { storage.write(KEY_QUEUE, value) }
    }

    private fun safeAvailability(sink: AnalyticsSink): SinkAvailability = try {
        sink.availability
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SinkAvailability.Unconfigured("availability threw ${e::class.simpleName}")
    }

    private fun safely(sink: AnalyticsSink, what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(ANALYTICS_LOG_TAG, e) { "stapel.analytics ${sink.id} $what failed" }
        }
    }

    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (logger.isEnabled(LogLevel.Warn, ANALYTICS_LOG_TAG)) logger.log(LogLevel.Warn, ANALYTICS_LOG_TAG, "stapel.analytics storage: $what failed", e)
        null
    }

    private companion object {
        const val KEY_CONSENT = "consent"
        const val KEY_QUEUE = "queue"
        const val KEY_ANON = "anon"
        val json = Json { ignoreUnknownKeys = true }

        fun encodeConsent(c: Consent): String = json.encodeToString(StoredConsent.serializer(), StoredConsent(c.analytics.wire, c.advertising.wire))

        fun decodeConsent(text: String): Consent? = try {
            val s = json.decodeFromString(StoredConsent.serializer(), text)
            val a = ConsentState.entries.firstOrNull { it.wire == s.analytics }
            val ad = ConsentState.entries.firstOrNull { it.wire == s.advertising } ?: ConsentState.Denied
            a?.let { Consent(it, ad) }
        } catch (e: IllegalArgumentException) {
            null
        }

        fun decodeQueue(text: String): StoredQueue? = try {
            json.decodeFromString(StoredQueue.serializer(), text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

@Serializable
internal class StoredConsent(val analytics: String, val advertising: String)

@Serializable
internal class StoredEvent(
    val id: String,
    val kind: String,
    val name: String,
    val props: Map<String, String>,
    val userHash: String? = null,
    val anonId: String,
    val sessionId: String,
    val ts: Long,
) {
    constructor(e: AnalyticsEvent) : this(e.id, e.kind.wire, e.name, e.props, e.userHash, e.anonId, e.sessionId, e.timestamp.toEpochMilliseconds())

    fun toEvent(): AnalyticsEvent = AnalyticsEvent(
        id, EventKind.entries.firstOrNull { it.wire == kind } ?: EventKind.Track, name, props, userHash, anonId, sessionId,
        Instant.fromEpochMilliseconds(ts),
    )
}

@Serializable
internal class StoredInflight(val events: List<StoredEvent>, val delivered: List<String>, val attempts: Int)

@Serializable
internal class StoredQueue(val v: Int = 1, val inflight: StoredInflight? = null, val queue: List<StoredEvent> = emptyList())
