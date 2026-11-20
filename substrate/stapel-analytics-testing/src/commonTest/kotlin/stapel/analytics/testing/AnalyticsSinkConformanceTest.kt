package stapel.analytics.testing

import kotlinx.coroutines.test.runTest
import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The fake passes the suite; each deliberately broken sink fails exactly the case for its rule. */
class AnalyticsSinkConformanceTest {
    private class Suite(
        private val make: () -> FakeAnalyticsSink = { FakeAnalyticsSink() },
        private val wrap: (FakeAnalyticsSink) -> AnalyticsSink = { it },
        private val unconfiguredState: SinkAvailability = SinkAvailability.Unconfigured("config"),
    ) : AnalyticsSinkConformance() {
        override fun configured(): SinkUnderTest = make().let { SinkUnderTest(wrap(it), it) }
        override fun unconfigured(): SinkUnderTest = make().apply { reported = unconfiguredState }.let { SinkUnderTest(wrap(it), it) }
    }

    @Test
    fun theFakeSinkConforms() = runTest {
        val suite = Suite()
        assertEquals(suite.caseNames.size, suite.verify())
        assertEquals(7, suite.caseNames.size)
    }

    private suspend fun failedCases(suite: AnalyticsSinkConformance): String =
        assertFailsWith<AssertionError> { suite.verify() }.message.orEmpty()

    @Test
    fun anUnconfiguredSinkThatThrowsOrClaimsDeliveryFails() = runTest {
        val throwing = Suite(wrap = { fake ->
            object : AnalyticsSink by fake {
                override fun setUserProperty(name: String, value: String?) {
                    if (fake.availability != SinkAvailability.Ready) throw IllegalStateException("no default app")
                    fake.setUserProperty(name, value)
                }
            }
        })
        assertTrue("availability-honest" in failedCases(throwing))
        val lying = Suite(wrap = { fake ->
            object : AnalyticsSink by fake {
                override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt = fake.deliver(batch).let { SinkReceipt.Accepted }
            }
        })
        assertTrue("availability-honest" in failedCases(lying))
    }

    @Test
    fun aSinkIgnoringTheKillSwitchFails() = runTest {
        val suite = Suite(wrap = { fake -> object : AnalyticsSink by fake { override fun setCollectionEnabled(enabled: Boolean) {} } })
        val failed = failedCases(suite)
        assertTrue("collection-idempotent" in failed, failed)
    }

    @Test
    fun aSinkDroppingOrReorderingEventsFails() = runTest {
        val suite = Suite(wrap = { fake ->
            object : AnalyticsSink by fake {
                override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt = fake.deliver(batch.reversed())
            }
        })
        assertTrue("events-in-order" in failedCases(suite))
    }

    @Test
    fun aSinkWhoseResetKeepsTheUserIdFails() = runTest {
        val suite = Suite(wrap = { fake -> object : AnalyticsSink by fake { override fun reset() {} } })
        assertTrue("identity-follows-and-resets" in failedCases(suite))
    }

    @Test
    fun aSinkThatThrowsOnVendorFailureFails() = runTest {
        val suite = Suite(wrap = { fake ->
            object : AnalyticsSink by fake {
                override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt =
                    fake.deliver(batch).also { if (it is SinkReceipt.Retry) throw IllegalStateException("vendor down") }
            }
        })
        assertTrue("failure-is-retry" in failedCases(suite))
    }

    @Test
    fun recordingAnalyticsKeepsCallsInOrder() {
        val analytics = RecordingAnalytics()
        analytics.track("a")
        analytics.page("shop.home")
        analytics.identify(null)
        assertEquals(listOf("a"), analytics.tracked)
        assertEquals(3, analytics.calls.size)
    }
}
