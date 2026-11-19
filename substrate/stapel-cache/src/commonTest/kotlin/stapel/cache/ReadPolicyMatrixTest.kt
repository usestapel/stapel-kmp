@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Every read policy x {absent, fresh, stale, past retention} x {network ok, network error}: the
 * exact emission sequence and the number of requests made.
 */
class ReadPolicyMatrixTest {
    private val swr = ReadPolicy.StaleWhileRevalidate(30.seconds, 24.hours)
    private val cacheFirst = ReadPolicy.CacheFirst(30.seconds, 24.hours)
    private val networkFirst = ReadPolicy.NetworkFirst(24.hours)

    private enum class Held(val age: Duration?) { Absent(null), Fresh(10.seconds), Stale(60.seconds), Expired(25.hours) }

    private class Row(val policy: ReadPolicy, val held: Held, val ok: Boolean, val expected: List<String>, val fetches: Int)

    private val rows = listOf(
        Row(swr, Held.Absent, true, listOf("L", "R:new"), 1),
        Row(swr, Held.Absent, false, listOf("L", "F"), 1),
        Row(swr, Held.Fresh, true, listOf("R:old"), 0),
        Row(swr, Held.Fresh, false, listOf("R:old"), 0),
        Row(swr, Held.Stale, true, listOf("R:old:sr", "R:new"), 1),
        Row(swr, Held.Stale, false, listOf("R:old:sr", "R:old:se"), 1),
        Row(swr, Held.Expired, true, listOf("L", "R:new"), 1),
        Row(swr, Held.Expired, false, listOf("L", "F"), 1),

        Row(cacheFirst, Held.Absent, true, listOf("L", "R:new"), 1),
        Row(cacheFirst, Held.Absent, false, listOf("L", "F"), 1),
        Row(cacheFirst, Held.Fresh, true, listOf("R:old"), 0),
        Row(cacheFirst, Held.Fresh, false, listOf("R:old"), 0),
        Row(cacheFirst, Held.Stale, true, listOf("L", "R:new"), 1),
        Row(cacheFirst, Held.Stale, false, listOf("L", "R:old:se"), 1),
        Row(cacheFirst, Held.Expired, true, listOf("L", "R:new"), 1),
        Row(cacheFirst, Held.Expired, false, listOf("L", "F"), 1),

        Row(networkFirst, Held.Absent, true, listOf("L", "R:new"), 1),
        Row(networkFirst, Held.Absent, false, listOf("L", "F"), 1),
        Row(networkFirst, Held.Fresh, true, listOf("L", "R:new"), 1),
        Row(networkFirst, Held.Fresh, false, listOf("L", "R:old:se"), 1),
        Row(networkFirst, Held.Stale, true, listOf("L", "R:new"), 1),
        Row(networkFirst, Held.Stale, false, listOf("L", "R:old:se"), 1),
        Row(networkFirst, Held.Expired, true, listOf("L", "R:new"), 1),
        Row(networkFirst, Held.Expired, false, listOf("L", "F"), 1),

        Row(ReadPolicy.NetworkOnly, Held.Absent, true, listOf("L", "R:new"), 1),
        Row(ReadPolicy.NetworkOnly, Held.Absent, false, listOf("L", "F"), 1),
        Row(ReadPolicy.NetworkOnly, Held.Fresh, true, listOf("L", "R:new"), 1),
        Row(ReadPolicy.NetworkOnly, Held.Fresh, false, listOf("L", "F"), 1),
    )

    @Test
    fun everyPolicyEmitsTheExactSequence() = runTest {
        val failures = rows.mapNotNull { row ->
            val (emissions, fetches) = run(row)
            val name = "${row.policy::class.simpleName} ${row.held} ${if (row.ok) "ok" else "error"}"
            if (emissions != row.expected || fetches != row.fetches) {
                "$name: expected ${row.expected} with ${row.fetches} fetches, got $emissions with $fetches"
            } else {
                null
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    private suspend fun TestScope.run(row: Row): Pair<List<String>, Int> {
        val clock = TestClock()
        val cache = newCache(backgroundScope, clock)
        cache.setAccount("acc")
        val spec = spec("auth", "me", policy = row.policy)
        row.held.age?.let { age ->
            cache.write(spec, "old")
            clock.advance(age)
        }
        val fetch = FakeFetch()
        if (!row.ok) fetch.fail()
        val emissions = mutableListOf<String>()
        val job = backgroundScope.launch { cache.observe(spec) { fetch.invoke() }.collect { emissions += it.short() } }
        runCurrent()
        job.cancel()
        cache.close()
        return emissions to fetch.calls
    }
}
