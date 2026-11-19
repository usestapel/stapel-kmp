@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class CounterState(val log: List<String> = emptyList(), val query: String = "", val results: String = "")

private sealed interface CounterIntent {
    data class Slow(val name: String) : CounterIntent
    data class Fast(val name: String) : CounterIntent
    data class Search(val query: String) : CounterIntent
    data class Notify(val message: String) : CounterIntent
    data class Type(val query: String) : CounterIntent
}

private class CounterPresenter(context: PresenterContext) :
    Presenter<CounterState, CounterIntent, String>(CounterState(), context) {
    var searchesStarted = 0
    var searchesCancelled = 0

    init {
        saved("query", String.serializer()) { query }?.let { restored -> reduce { copy(query = restored) } }
    }

    override suspend fun handle(intent: CounterIntent) {
        when (intent) {
            is CounterIntent.Slow -> {
                delay(100)
                reduce { copy(log = log + intent.name) }
            }
            is CounterIntent.Fast -> reduce { copy(log = log + intent.name) }
            is CounterIntent.Search -> launch(key = "search") {
                searchesStarted++
                try {
                    delay(50)
                    reduce { copy(results = "results for ${intent.query}") }
                } finally {
                    if (state.value.results != "results for ${intent.query}") searchesCancelled++
                }
            }
            is CounterIntent.Notify -> emit(intent.message)
            is CounterIntent.Type -> reduce { copy(query = intent.query) }
        }
    }
}

/** In-memory stand-in for the platform's saved-state registry. */
private class MapSaver(private val saved: MutableMap<String, Any> = mutableMapOf()) : StateSaver {
    private val providers = mutableMapOf<String, () -> Any?>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> restore(key: String, serializer: KSerializer<T>): T? = saved[key] as T?

    override fun <T : Any> register(key: String, serializer: KSerializer<T>, provider: () -> T?) {
        providers[key] = provider
    }

    /** What the platform would write on process death. */
    fun save(): MutableMap<String, Any> = providers.mapNotNull { (k, p) -> p()?.let { k to it } }.toMap().toMutableMap()
}

class PresenterTest {
    private fun TestScope.presenterScope(): CoroutineScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))

    @Test
    fun intentsAreHandledInDispatchOrderEvenWhenTheFirstIsSlow() = runTest {
        val scope = presenterScope()
        val p = CounterPresenter(PresenterContext(scope))
        p.dispatch(CounterIntent.Slow("a"))
        p.dispatch(CounterIntent.Fast("b"))
        p.dispatch(CounterIntent.Fast("c"))
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), p.state.value.log)
        scope.cancel()
    }

    @Test
    fun aSecondLaunchWithTheSameKeyCancelsTheFirst() = runTest {
        val scope = presenterScope()
        val p = CounterPresenter(PresenterContext(scope))
        p.dispatch(CounterIntent.Search("ko"))
        runCurrent()
        p.dispatch(CounterIntent.Search("kot"))
        advanceUntilIdle()
        assertEquals("results for kot", p.state.value.results)
        assertEquals(2, p.searchesStarted)
        assertEquals(1, p.searchesCancelled)
        scope.cancel()
    }

    @Test
    fun effectsAreBufferedWithoutACollectorAndDeliveredExactlyOnce() = runTest {
        val scope = presenterScope()
        val p = CounterPresenter(PresenterContext(scope))
        // No collector (e.g. the screen is being recreated): the effects must wait, not vanish.
        p.dispatch(CounterIntent.Notify("one"))
        p.dispatch(CounterIntent.Notify("two"))
        advanceUntilIdle()
        val first = p.effects.take(2).toList()
        assertEquals(listOf("one", "two"), first)
        // A later collector (the recreated screen) never sees them again.
        val second = mutableListOf<String>()
        val job = launch { p.effects.collect { second += it } }
        p.dispatch(CounterIntent.Notify("three"))
        advanceUntilIdle()
        assertEquals(listOf("three"), second)
        job.cancel()
        scope.cancel()
    }

    @Test
    fun savedValuesSurviveAProcessDeathRoundTrip() = runTest {
        val scope1 = presenterScope()
        val saver1 = MapSaver()
        val p1 = CounterPresenter(PresenterContext(scope1, saver1))
        p1.dispatch(CounterIntent.Type("kotlin"))
        advanceUntilIdle()
        val written = saver1.save()
        scope1.cancel()

        val scope2 = presenterScope()
        val p2 = CounterPresenter(PresenterContext(scope2, MapSaver(written)))
        assertEquals("kotlin", p2.state.first().query)
        scope2.cancel()
    }

    @Test
    fun cancellingTheOwnerScopeStopsIntentHandling() = runTest {
        val scope = presenterScope()
        val p = CounterPresenter(PresenterContext(scope))
        scope.cancel()
        p.dispatch(CounterIntent.Fast("late"))
        advanceUntilIdle()
        assertTrue(p.state.value.log.isEmpty())
    }

    @Test
    fun recordCapturesStatesAndEffects() = runTest {
        val scope = presenterScope()
        val p = CounterPresenter(PresenterContext(scope))
        val rec = p.record(scope)
        p.dispatch(CounterIntent.Fast("x"))
        p.dispatch(CounterIntent.Notify("done"))
        advanceUntilIdle()
        assertEquals(listOf(emptyList(), listOf("x")), rec.states.map { it.log })
        assertEquals(listOf("done"), rec.effects)
        rec.stop()
        scope.cancel()
    }
}
