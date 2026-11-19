@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.mvi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private sealed interface Step {
    data object Idle : Step
    data class Sending(val n: Int) : Step
    data class Sent(val value: String) : Step
    data class Failed(val message: String) : Step
}

class FlowMachineTest {
    private fun TestScope.owned(): CoroutineScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))

    private val events = mutableListOf<String>()
    private val analytics = FlowAnalytics { id, step, phase -> events += "$id.$step.$phase" }

    @Test
    fun runParksInPendingThenResolves() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("auth.otp", Step.Idle, scope, analytics)
        val gate = CompletableDeferred<String>()
        m.run(Step.Sending(1), { gate.await() }, { Step.Sent(it) }, { Step.Failed(it.message ?: "") })
        assertEquals(Step.Sending(1), m.state.value)
        gate.complete("ok")
        advanceUntilIdle()
        assertEquals(Step.Sent("ok"), m.state.value)
        assertEquals(
            listOf("auth.otp.Sending.Started", "auth.otp.Sending.Completed", "auth.otp.Sent.Started"),
            events,
        )
        scope.cancel()
    }

    @Test
    fun aFailureIsFoldedIntoAStateNotThrown() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("f", Step.Idle, scope, analytics)
        m.run(Step.Sending(1), { error("boom") }, { Step.Sent(it) }, { Step.Failed(it.message ?: "") })
        advanceUntilIdle()
        assertEquals(Step.Failed("boom"), m.state.value)
        assertTrue("f.Sending.Failed" in events)
        scope.cancel()
    }

    @Test
    fun aNewerTransitionCancelsInFlightWorkAndDropsItsLateResult() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("f", Step.Idle, scope)
        val gate = CompletableDeferred<String>()
        val job = m.run(Step.Sending(1), { gate.await() }, { Step.Sent(it) }, { Step.Failed("x") })
        m.to(Step.Idle) // the user edits the identifier while the request is in flight
        gate.complete("late")
        advanceUntilIdle()
        assertEquals(Step.Idle, m.state.value)
        assertTrue(job.isCancelled)
        scope.cancel()
    }

    @Test
    fun aSecondRunWinsOverTheFirst() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("f", Step.Idle, scope)
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val j1 = m.run(Step.Sending(1), { first.await() }, { Step.Sent(it) }, { Step.Failed("x") })
        m.run(Step.Sending(2), { second.await() }, { Step.Sent(it) }, { Step.Failed("x") })
        second.complete("second")
        first.complete("first")
        advanceUntilIdle()
        assertEquals(Step.Sent("second"), m.state.value)
        assertTrue(j1.isCancelled)
        scope.cancel()
    }

    @Test
    fun cancelLeavesStateAndNeverAppliesTheResult() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("f", Step.Idle, scope)
        val gate = CompletableDeferred<String>()
        m.run(Step.Sending(1), { gate.await() }, { Step.Sent(it) }, { Step.Failed("x") })
        m.cancel()
        gate.complete("late")
        advanceUntilIdle()
        assertEquals(Step.Sending(1), m.state.value)
        assertFalse(m.state.value is Step.Failed)
        scope.cancel()
    }

    @Test
    fun synchronousWorkResolvesImmediately() = runTest {
        val scope = owned()
        val m = FlowMachine<Step>("f", Step.Idle, scope)
        m.run(Step.Sending(1), { "now" }, { Step.Sent(it) }, { Step.Failed("x") })
        assertEquals(Step.Sent("now"), m.state.value)
        scope.cancel()
    }
}
