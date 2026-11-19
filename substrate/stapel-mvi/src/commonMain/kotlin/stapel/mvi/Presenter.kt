package stapel.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/**
 * Base class of a headless MVI presenter.
 *
 * Intents go through one queue and are handled one at a time, in the order they were dispatched, in
 * [PresenterContext.scope]. Long work is started with [launch], so a slow request does not hold back
 * the next intent. A presenter never holds a navigator: it emits a domain effect and the UI decides
 * where that leads.
 *
 * @param initial state before any intent; ignored for the parts restored with [saved].
 * @param context scope and saver supplied by the owner.
 */
public abstract class Presenter<S : Any, I : Any, E : Any>(
    initial: S,
    context: PresenterContext,
) : Store<S, I, E> {
    private val scope: CoroutineScope = context.scope
    private val saver: StateSaver = context.saver
    private val mutableState = MutableStateFlow(initial)
    private val intents = Channel<I>(Channel.UNLIMITED)
    private val effectChannel = Channel<E>(Channel.UNLIMITED)
    private val keyedJobs = mutableMapOf<Any, Job>()

    final override val state: StateFlow<S> = mutableState.asStateFlow()

    final override val effects: Flow<E> = effectChannel.receiveAsFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (intent in intents) handle(intent)
        }
    }

    /** Thread-safe; the intent is queued and handled after every intent dispatched before it. */
    final override fun dispatch(intent: I) {
        intents.trySend(intent)
    }

    /** Handles one intent. The next intent waits until this returns. */
    protected abstract suspend fun handle(intent: I)

    /** Replaces the state with [transform] applied to the current one (atomically). */
    protected fun reduce(transform: S.() -> S) {
        mutableState.update(transform)
    }

    /** Queues [effect] for the single collector of [effects]; it is delivered exactly once. */
    protected fun emit(effect: E) {
        effectChannel.trySend(effect)
    }

    /**
     * Starts long work in the presenter's scope. A second launch with the same non-null [key] cancels
     * the first (latest wins). Call it from [handle] or from another coroutine of this presenter; the
     * key table is confined to the presenter's dispatcher.
     */
    protected fun launch(key: Any? = null, block: suspend CoroutineScope.() -> Unit): Job {
        if (key == null) return scope.launch(block = block)
        keyedJobs.remove(key)?.cancel()
        val job = scope.launch(block = block)
        keyedJobs[key] = job
        job.invokeOnCompletion { if (keyedJobs[key] === job) keyedJobs.remove(key) }
        return job
    }

    /**
     * Registers a part of the state for process-death saving and returns the value a previous process
     * saved, if any. [read] is called on the current state whenever the platform saves.
     */
    protected fun <T : Any> saved(key: String, serializer: KSerializer<T>, read: S.() -> T?): T? {
        saver.register(key, serializer) { mutableState.value.read() }
        return saver.restore(key, serializer)
    }
}
