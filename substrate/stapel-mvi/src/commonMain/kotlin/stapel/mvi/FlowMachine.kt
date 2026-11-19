package stapel.mvi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Phase of a flow step reported to [FlowAnalytics]. */
public enum class FlowPhase {
    /** The machine entered the step. */
    Started,

    /** The work of a pending step finished successfully. */
    Completed,

    /** The work of a pending step failed. */
    Failed,
}

/** Receives every transition of a [FlowMachine] under its documented flow id (`flow.<id>.<step>`). */
public fun interface FlowAnalytics {
    /** Called on every transition ([FlowPhase.Started]) and when the work of a pending step settles. */
    public fun step(flowId: String, step: String, phase: FlowPhase)
}

/**
 * A small state machine for multi-step journeys (sign-in, reset, enrolment).
 *
 * Resting states are entered with [to]. Async steps go through [run]: the machine parks in a pending
 * state, runs the work, then moves to the state [run]'s `resolve` or `reject` builds. Any newer
 * transition — [to] or another [run] — cancels the in-flight work and its late result is never applied
 * (last write wins). A failure of the work is folded into a state; [run] never throws into the caller.
 *
 * Not thread-safe: call it from the dispatcher of [scope] (the main thread for a UI presenter).
 *
 * @param id documented flow id used for analytics.
 * @param initial first state.
 * @param scope scope the work runs in; cancelling it cancels the work.
 * @param analytics receiver of `flow.<id>.<step>` events, or null for none.
 * @param stepOf the analytics step name of a state; the class name by default.
 */
public class FlowMachine<S : Any>(
    public val id: String,
    initial: S,
    private val scope: CoroutineScope,
    private val analytics: FlowAnalytics? = null,
    private val stepOf: (S) -> String = { it::class.simpleName ?: "state" },
) {
    private val mutableState = MutableStateFlow(initial)
    private var generation = 0L
    private var inFlight: Job? = null

    /** Current state of the flow. */
    public val state: StateFlow<S> = mutableState.asStateFlow()

    /** Moves to a resting state, cancelling any in-flight work. */
    public fun to(next: S) {
        inFlight?.cancel()
        inFlight = null
        transition(next)
    }

    /**
     * Parks in [pending], runs [block] in the machine's scope, then moves to `resolve(result)` or, when
     * [block] throws, to `reject(error)`. Cancellation is not a failure: cancelled work moves nowhere.
     * Returns the job of the work (already cancelled-safe: a newer transition cancels it).
     */
    public fun <T> run(
        pending: S,
        block: suspend () -> T,
        resolve: (T) -> S,
        reject: (Throwable) -> S,
    ): Job {
        inFlight?.cancel()
        val epoch = transition(pending)
        val step = stepOf(pending)
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val outcome = try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            analytics?.step(id, step, if (outcome.isSuccess) FlowPhase.Completed else FlowPhase.Failed)
            if (generation != epoch) return@launch
            inFlight = null
            transition(outcome.fold(resolve, reject))
        }
        if (job.isActive && generation == epoch) inFlight = job
        return job
    }

    /** Cancels in-flight work and leaves the state where it is. */
    public fun cancel() {
        inFlight?.cancel()
        inFlight = null
        generation++
    }

    private fun transition(next: S): Long {
        generation++
        val epoch = generation
        mutableState.value = next
        analytics?.step(id, stepOf(next), FlowPhase.Started)
        return epoch
    }
}
