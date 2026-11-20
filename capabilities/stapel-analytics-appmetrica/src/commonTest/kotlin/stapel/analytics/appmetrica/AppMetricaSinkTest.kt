package stapel.analytics.appmetrica

import kotlinx.coroutines.test.runTest
import stapel.analytics.SinkAvailability
import stapel.analytics.testing.AnalyticsSinkConformance
import stapel.analytics.testing.SinkUnderTest
import stapel.analytics.testing.VendorCall
import stapel.analytics.testing.VendorProbe
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Swift bridge's stand-in: records what the AppMetrica SDK would receive. */
internal class RecordingAppMetricaBridge(private val isConfigured: Boolean = true) : AppMetricaAnalyticsBridge, VendorProbe {
    private val recorded = mutableListOf<VendorCall>()
    private var fail = false
    override val calls: List<VendorCall> get() = recorded.toList()

    override fun failNext() {
        fail = true
    }

    override fun configured(): Boolean = isConfigured
    override fun setDataSendingEnabled(enabled: Boolean) {
        recorded += VendorCall.Collection(enabled)
    }
    override fun reportEvent(name: String, params: Map<String, String>) {
        if (fail) {
            fail = false
            throw IllegalStateException("appmetrica down")
        }
        recorded += VendorCall.Event(name, params)
    }
    override fun setUserProfileId(id: String?) {
        recorded += VendorCall.UserId(id)
    }
    override fun setProfileAttribute(name: String, value: String?) {
        recorded += VendorCall.UserProperty(name, value)
    }
}

class AppMetricaSinkConformanceTest : AnalyticsSinkConformance() {
    private fun subject(configured: Boolean) = RecordingAppMetricaBridge(configured).let {
        SinkUnderTest(appMetricaSink(it, SinkAvailability.Unsupported("x")), it)
    }
    override fun configured(): SinkUnderTest = subject(true)
    override fun unconfigured(): SinkUnderTest = subject(false)
    override fun vendorPage(name: String): VendorCall = VendorCall.Event(APPMETRICA_SCREEN_EVENT, mapOf(APPMETRICA_SCREEN_PARAM to name))

    @Test
    fun conformance() = runTest {
        assertEquals(caseNames.size, verify())
    }

    @Test
    fun desktopIsAnHonestUnsupportedSink() {
        assertEquals(SinkAvailability.Unsupported("desktop"), appMetricaSink(null, SinkAvailability.Unsupported("desktop")).availability)
        assertEquals(SinkAvailability.Unconfigured("AppMetrica API key"), appMetricaSink(RecordingAppMetricaBridge(false), SinkAvailability.Unsupported("x")).availability)
    }
}
