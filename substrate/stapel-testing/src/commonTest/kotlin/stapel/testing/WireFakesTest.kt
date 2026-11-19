package stapel.testing

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import stapel.network.NetworkConfig
import stapel.network.NetworkDependencies
import stapel.network.RetryPolicy
import stapel.network.StapelTransport
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.Remediation
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportCarrier
import stapel.result.TransportFailure
import stapel.result.DomainError
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.ErrorDecoder
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A generated-style operation error. */
private sealed interface PingError : StapelError {
    data class TooSoon(override val envelope: ErrorEnvelope) : PingError, DomainError {
        override val remediation: Remediation get() = Remediation.WaitAndRetry
    }
    data class Other(override val envelope: ErrorEnvelope) : PingError, DomainError {
        override val remediation: Remediation get() = Remediation.Unknown
    }
    data class Transport(override val failure: TransportFailure) : PingError, TransportCarrier

    companion object : ErrorDecoder<PingError> {
        override fun domain(envelope: ErrorEnvelope): PingError =
            if (envelope.code == ErrorCode("error.400.too_soon")) TooSoon(envelope) else Other(envelope)
        override fun transport(failure: TransportFailure): PingError = Transport(failure)
    }
}

private val ping = OperationSpec<Unit, String, PingError>(
    id = "test_ping", module = "test", method = "GET", path = "/test/api/v1/ping/",
    kind = OperationKind.Query, auth = AuthRequirement.None, bodyEncoding = BodyEncoding.None, body = null,
    responseKind = ResponseKind.Json, success = mapOf(200 to String.serializer()), errors = PingError,
)

class WireFakesTest {
    private fun pipeline(wire: FakeTransport) = StapelTransport(
        deps = NetworkDependencies(environment = testEnvironment(), clock = TestClock(), ids = SequentialIdGenerator()),
        config = NetworkConfig(retry = RetryPolicy.None),
        transport = wire,
    )

    @Test
    fun scriptedEnvelopesRunThroughTheRealPipeline() = runTest {
        val wire = FakeTransport()
        wire.enqueueJson(200, "\"pong\"")
        wire.enqueueEnvelope(400, "error.400.too_soon", mapOf("retry_after" to "3"), error = "Слишком \"рано\"", language = "ru")
        wire.enqueue(503, "<html>down</html>")
        val api = pipeline(wire)
        assertEquals(StapelResult.Ok("pong"), api.execute(ping, OperationCall()))
        val refused = (api.execute(ping, OperationCall()) as StapelResult.Err).error
        assertIs<PingError.TooSoon>(refused)
        assertEquals("Слишком \"рано\"", refused.envelope.serverText, "the envelope survives JSON escaping")
        assertEquals("3", refused.params["retry_after"])
        val down = (api.execute(ping, OperationCall()) as StapelResult.Err).error
        assertIs<PingError.Transport>(down)
        assertIs<TransportFailure.Unavailable>(down.failure)
        assertEquals(3, wire.requests.size)
        assertEquals("https://api.test/test/api/v1/ping/", wire.requests.first().url)
        assertEquals(0, wire.pending)
    }

    @Test
    fun anUnscriptedRequestFailsLoudly() = runTest {
        val wire = FakeTransport()
        assertFailsWith<AssertionError> { pipeline(wire).execute(ping, OperationCall()) }
    }

    @Test
    fun noResponseFailuresAreScriptable() = runTest {
        val wire = FakeTransport()
        wire.enqueueFailure(TransportFailure.Timeout(stapel.result.TimeoutPhase.Socket), requestSent = true)
        val e = (pipeline(wire).execute(ping, OperationCall()) as StapelResult.Err).error
        assertIs<PingError.Transport>(e)
        assertIs<TransportFailure.Timeout>(e.failure)
    }

    @Test
    fun fakeApiTransportBuildsTypedErrorsThroughTheOperationDecoder() = runTest {
        val api = FakeApiTransport()
        api.on("test_ping") { FakeAnswer.Value("pong") }
        assertEquals(StapelResult.Ok("pong"), api.execute(ping, OperationCall()))
        api.on("test_ping") { FakeAnswer.Refusal(ErrorEnvelope(ErrorCode("error.400.too_soon"), 400)) }
        assertIs<PingError.TooSoon>((api.execute(ping, OperationCall()) as StapelResult.Err).error)
        api.on("test_ping") { FakeAnswer.Failure(TransportFailure.Offline()) }
        assertIs<PingError.Transport>((api.execute(ping, OperationCall()) as StapelResult.Err).error)
        assertEquals(3, api.calls.size)
        assertTrue(api.url(ping, OperationCall<Unit>()).startsWith("fake://"))
        assertFailsWith<AssertionError> { FakeApiTransport().execute(ping, OperationCall()) }
    }
}
