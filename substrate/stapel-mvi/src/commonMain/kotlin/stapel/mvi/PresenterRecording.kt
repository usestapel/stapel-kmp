package stapel.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What a [Store] did while recorded: every state it published (as observed by a collector; a
 * `StateFlow` may conflate fast consecutive values) and every effect, in order.
 */
public class PresenterRecording<S : Any, E : Any> internal constructor() {
    internal val stateLog = mutableListOf<S>()
    internal val effectLog = mutableListOf<E>()
    internal var job: Job? = null

    /** States observed so far, the initial one first. */
    public val states: List<S> get() = stateLog.toList()

    /** Effects received so far. The recording is the single collector of the store's effects. */
    public val effects: List<E> get() = effectLog.toList()

    /** The last observed state. */
    public val latest: S get() = stateLog.last()

    /** Stops recording. */
    public fun stop() {
        job?.cancel()
    }
}

/**
 * Records this store's states and effects in [scope] (typically a test's `backgroundScope`). The
 * recording becomes the single collector of [Store.effects], so nothing else may collect them.
 */
public fun <S : Any, I : Any, E : Any> Store<S, I, E>.record(scope: CoroutineScope): PresenterRecording<S, E> {
    val recording = PresenterRecording<S, E>()
    recording.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        launch(start = CoroutineStart.UNDISPATCHED) { state.collect { recording.stateLog += it } }
        launch(start = CoroutineStart.UNDISPATCHED) { effects.collect { recording.effectLog += it } }
    }
    return recording
}
