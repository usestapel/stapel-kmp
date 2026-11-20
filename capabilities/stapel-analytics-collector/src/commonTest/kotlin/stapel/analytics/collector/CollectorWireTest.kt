package stapel.analytics.collector

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer
import stapel.analytics.AnalyticsEvent
import stapel.analytics.EventKind
import stapel.analytics.SinkReceipt
import stapel.analytics.collector.api.model.Ingest
import stapel.analytics.collector.api.model.IngestContext
import stapel.analytics.collector.api.model.IngestEvent
import stapel.analytics.testing.AnalyticsSinkConformance
import stapel.analytics.testing.SinkUnderTest
import stapel.analytics.testing.VendorCall
import stapel.analytics.testing.VendorProbe
import stapel.network.NetworkConfig
import stapel.network.NetworkDependencies
import stapel.network.RetryPolicy
import stapel.network.StapelTransport
import stapel.network.WireBody
import stapel.network.WireRequest
import stapel.result.TransportFailure
import stapel.testing.FakeTransport
import stapel.testing.SequentialIdGenerator
import stapel.testing.TestClock
import stapel.testing.testEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun sinkOn(wire: FakeTransport, writeKey: String? = "wk-android") = CollectorSink(
    StapelTransport(
        NetworkDependencies(testEnvironment(), TestClock(), SequentialIdGenerator()),
        config = NetworkConfig(retry = RetryPolicy.None),
        transport = wire,
    ),
    CollectorOptions(source = "android", writeKey = writeKey, appVersion = "1.2.3", osVersion = "15", locale = { "en-US" }),
)

private fun event(n: Int, kind: EventKind = EventKind.Track, name: String = "e$n", userHash: String? = null) = AnalyticsEvent(
    "1767225600000-$n", kind, name, mapOf("step" to "$n"), userHash, "anon-1", "session-1",
    Instant.fromEpochMilliseconds(1_767_225_600_000L + n),
)

private fun WireRequest.json(): JsonObject = Json.parseToJsonElement((body as WireBody.Bytes).text()).jsonObject

/** The collector through the real pipeline on a scripted wire: bodies captured, every answer class mapped. */
class CollectorWireTest {
    @Test
    fun aBatchIsOnePostWithEpochMillisecondsAndClientContextOnTheBareClient() = runTest {
        val wire = FakeTransport().apply { enqueueJson(202, """{"accepted":2,"rejected":[]}""") }
        val receipt = sinkOn(wire).deliver(listOf(event(1, userHash = "h"), event(2, kind = EventKind.Page, name = "shop.home")))
        assertEquals(SinkReceipt.Accepted, receipt)
        val request = wire.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://api.test/analytics/api/v1/events", request.url)
        assertNull(request.header("Authorization"), "the collector uses the bare client")
        val body = request.json()
        assertEquals("wk-android", body.getValue("write_key").jsonPrimitive.content)
        assertEquals("anon-1", body.getValue("anon_id").jsonPrimitive.content)
        val context = body.getValue("context").jsonObject
        assertEquals(listOf("android", "1.2.3", "15", "en-US", "stapel-kmp"), listOf("platform", "app_version", "os_version", "locale", "sdk").map { context.getValue(it).jsonPrimitive.content })
        val events = body.getValue("events").jsonArray.map { it.jsonObject }
        assertEquals(1_767_225_600_001L, events[0].getValue("ts").jsonPrimitive.long, "epoch milliseconds do not fit an Int")
        assertEquals("track", events[0].getValue("kind").jsonPrimitive.content)
        assertEquals("h", events[0].getValue("userHash").jsonPrimitive.content)
        assertEquals("page", events[1].getValue("kind").jsonPrimitive.content)
        assertEquals("shop.home", events[1].getValue("name").jsonPrimitive.content)
        assertEquals("2", events[1].getValue("props").jsonObject.getValue("step").jsonPrimitive.content)
    }

    @Test
    fun rejectedEntriesBecomeAPartialReceiptKeyedByEventId() = runTest {
        val wire = FakeTransport().apply {
            enqueueJson(202, """{"accepted":1,"rejected":[{"index":1,"name":"e2","reason":"analytics_event_unregistered","detail":"x"}]}""")
        }
        val receipt = sinkOn(wire).deliver(listOf(event(1), event(2)))
        assertEquals(SinkReceipt.Partial(mapOf("1767225600000-2" to "analytics_event_unregistered")), receipt)
    }

    @Test
    fun aRefusedBatchIsDroppedNotRetried() = runTest {
        for ((status, code) in listOf(400 to "error.400.analytics_batch_too_large", 401 to "error.401.analytics_write_key", 413 to "error.413.analytics_body_too_large")) {
            val wire = FakeTransport().apply { enqueueEnvelope(status, code) }
            val receipt = sinkOn(wire).deliver(listOf(event(1)))
            assertIs<SinkReceipt.Partial>(receipt, "$status must not be retried")
            assertEquals(setOf("1767225600000-1"), receipt.rejected.keys)
        }
    }

    @Test
    fun serverFaultsAndNoResponseAreRetried() = runTest {
        val wire = FakeTransport().apply {
            enqueueEnvelope(503, "error.503.unavailable")
            enqueueEnvelope(500, "error.500.internal")
            enqueueFailure(TransportFailure.ConnectionFailed("refused"), requestSent = false)
        }
        val sink = sinkOn(wire)
        repeat(3) { assertIs<SinkReceipt.Retry>(sink.deliver(listOf(event(1)))) }
    }

    @Test
    fun theBodyCarriesEveryFieldOfTheGeneratedIngestModels() {
        fun names(d: kotlinx.serialization.descriptors.SerialDescriptor) = (0 until d.elementsCount).map { d.getElementName(it) }.toSet()
        assertEquals(names(serializer<Ingest>().descriptor), names(CollectorBatch.serializer().descriptor))
        assertEquals(names(serializer<IngestEvent>().descriptor), names(CollectorEvent.serializer().descriptor))
        assertEquals(names(serializer<IngestContext>().descriptor), names(CollectorContext.serializer().descriptor))
    }
}

/** The collector passes the provider conformance suite against a scripted wire. */
class CollectorConformanceTest : AnalyticsSinkConformance() {
    /** The wire as the vendor side: posted events, a scripted failure. */
    private class WireProbe(val wire: FakeTransport) : VendorProbe {
        private var fail = false

        init {
            wire.fallback = { request ->
                if (fail) {
                    fail = false
                    stapel.network.WireOutcome.Response(503, listOf("Content-Type" to "application/json"), """{"localizable_error":"error.503.unavailable","params":{}}""".encodeToByteArray())
                } else {
                    stapel.network.WireOutcome.Response(202, listOf("Content-Type" to "application/json"), """{"accepted":1}""".encodeToByteArray())
                }
            }
        }

        override val calls: List<VendorCall>
            get() = wire.requests.flatMap { r ->
                val body = r.json()
                body.getValue("events").jsonArray.map { it.jsonObject }.map { e ->
                    val name = e.getValue("name").jsonPrimitive.content
                    when (e.getValue("kind").jsonPrimitive.content) {
                        "page" -> VendorCall.Screen(name)
                        "identify" -> VendorCall.UserId(e["userHash"]?.jsonPrimitive?.content)
                        else -> VendorCall.Event(name, e.getValue("props").jsonObject.mapValues { it.value.jsonPrimitive.content })
                    }
                }
            } + listOfNotNull(VendorCall.Reset.takeIf { resets > 0 }, VendorCall.UserId(null).takeIf { resets > 0 }) +
                properties.map { VendorCall.UserProperty(it.first, it.second) } + collection.map { VendorCall.Collection(it) }

        var resets = 0
        val properties = mutableListOf<Pair<String, String?>>()
        val collection = mutableListOf<Boolean>()

        override fun failNext() {
            fail = true
        }
    }

    // The first-party collector has no vendor-side identity, properties or kill switch: those members
    // are no-ops, so the probe records the hub-facing call itself for the cases that check them.
    private fun subject(): SinkUnderTest {
        val probe = WireProbe(FakeTransport())
        val sink = sinkOn(probe.wire)
        val observed = object : stapel.analytics.AnalyticsSink by sink {
            override fun reset() {
                sink.reset()
                probe.resets++
            }
            override fun setUserProperty(name: String, value: String?) {
                sink.setUserProperty(name, value)
                probe.properties += name to value
            }
            override fun setCollectionEnabled(enabled: Boolean) {
                sink.setCollectionEnabled(enabled)
                probe.collection += enabled
            }
        }
        return SinkUnderTest(observed, probe)
    }

    override fun configured(): SinkUnderTest = subject()

    override fun unconfigured(): SinkUnderTest? = null

    @Test
    fun conformance() = runTest {
        assertEquals(caseNames.size, verify())
        assertTrue(caseNames.isNotEmpty())
    }
}
