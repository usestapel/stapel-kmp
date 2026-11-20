package stapel.analytics.firebase

import kotlinx.coroutines.test.runTest
import stapel.analytics.AnalyticsEvent
import stapel.analytics.EventKind
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt
import stapel.analytics.testing.AnalyticsSinkConformance
import stapel.analytics.testing.SinkUnderTest
import stapel.analytics.testing.VendorCall
import stapel.analytics.testing.VendorProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The Swift bridge's stand-in: records every call (what the Firebase SDK would receive). */
internal class RecordingFirebaseBridge(private val isConfigured: Boolean = true) : FirebaseAnalyticsBridge, VendorProbe {
    private val recorded = mutableListOf<VendorCall>()
    private var fail = false
    override val calls: List<VendorCall> get() = recorded.toList()

    override fun failNext() {
        fail = true
    }

    private fun maybeFail() {
        if (fail) {
            fail = false
            throw IllegalStateException("firebase down")
        }
    }

    override fun configured(): Boolean = isConfigured
    override fun setCollectionEnabled(enabled: Boolean) {
        recorded += VendorCall.Collection(enabled)
    }
    override fun logEvent(name: String, params: Map<String, String>) {
        maybeFail()
        recorded += VendorCall.Event(name, params)
    }
    override fun logScreen(name: String) {
        maybeFail()
        recorded += VendorCall.Screen(name)
    }
    override fun setUserId(id: String?) {
        recorded += VendorCall.UserId(id)
    }
    override fun setUserProperty(name: String, value: String?) {
        recorded += VendorCall.UserProperty(name, value)
    }
    override fun reset() {
        recorded += VendorCall.Reset
        recorded += VendorCall.UserId(null)
    }
}

class FirebaseSinkConformanceTest : AnalyticsSinkConformance() {
    override fun configured(): SinkUnderTest = RecordingFirebaseBridge().let { SinkUnderTest(firebaseAnalyticsSink(it, SinkAvailability.Unsupported("x"), "cfg"), it) }
    override fun unconfigured(): SinkUnderTest = RecordingFirebaseBridge(isConfigured = false).let { SinkUnderTest(firebaseAnalyticsSink(it, SinkAvailability.Unsupported("x"), "cfg"), it) }
    override fun vendorEventName(name: String): String = FirebaseNames.event(name)!!

    @Test
    fun conformance() = runTest {
        assertEquals(caseNames.size, verify())
    }
}

class FirebaseSinkTest {
    private fun event(kind: EventKind, name: String, props: Map<String, String> = emptyMap(), userHash: String? = null) =
        AnalyticsEvent("1-1", kind, name, props, userHash, "a", "s", Instant.fromEpochMilliseconds(1))

    @Test
    fun namesAreMappedIntoFirebaseRules() {
        assertEquals("flow_auth_login_CodeSent", FirebaseNames.event("flow.auth.login.CodeSent"))
        assertEquals("e_1st_open", FirebaseNames.event("1st.open"))
        assertEquals("x_first_open", FirebaseNames.event("first_open"))
        assertEquals("x_firebase_thing", FirebaseNames.event("firebase.thing"))
        assertEquals("x_ga_x", FirebaseNames.event("GA.x").let { it!!.lowercase() })
        assertEquals(40, FirebaseNames.event("a".repeat(80))!!.length)
        assertEquals(null, FirebaseNames.event("..."))
        assertEquals(24, FirebaseNames.userProperty("p".repeat(30))!!.length)
        val params = FirebaseNames.params((1..30).associate { "k$it" to "v".repeat(150) })
        assertEquals(25, params.size)
        assertTrue(params.values.all { it.length == 100 })
    }

    @Test
    fun anUnusableNameIsRejectedNotResent() = runTest {
        val bridge = RecordingFirebaseBridge()
        val receipt = firebaseAnalyticsSink(bridge, SinkAvailability.Unsupported("x"), "cfg").deliver(listOf(event(EventKind.Track, "...")))
        assertEquals(SinkReceipt.Partial(mapOf("1-1" to "firebase: event name has no usable character")), receipt)
    }

    @Test
    fun withoutABridgeTheSinkReportsWhatIsMissingAndCallsNothing() = runTest {
        val sink = firebaseAnalyticsSink(null, SinkAvailability.Unsupported("desktop"), "cfg")
        assertEquals(SinkAvailability.Unsupported("desktop"), sink.availability)
        sink.setCollectionEnabled(true)
        sink.reset()
        assertTrue(sink.deliver(listOf(event(EventKind.Track, "x"))) is SinkReceipt.Retry)
        val unconfigured = firebaseAnalyticsSink(RecordingFirebaseBridge(isConfigured = false), SinkAvailability.Unsupported("x"), "GoogleService-Info.plist")
        assertEquals(SinkAvailability.Unconfigured("GoogleService-Info.plist"), unconfigured.availability)
    }

    @Test
    fun aUserIdChangeInsideABatchIsSetBeforeTheEvent() = runTest {
        val bridge = RecordingFirebaseBridge()
        firebaseAnalyticsSink(bridge, SinkAvailability.Unsupported("x"), "cfg").deliver(
            listOf(event(EventKind.Track, "a", userHash = "h1"), event(EventKind.Track, "b", userHash = null)),
        )
        assertEquals(
            listOf(VendorCall.UserId("h1"), VendorCall.Event("a", emptyMap()), VendorCall.UserId(null), VendorCall.Event("b", emptyMap())),
            bridge.calls,
        )
    }
}
