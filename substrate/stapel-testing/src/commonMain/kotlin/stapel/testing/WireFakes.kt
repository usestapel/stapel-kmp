package stapel.testing

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import stapel.network.HttpTransport
import stapel.network.WireOutcome
import stapel.network.WireRequest
import stapel.result.ErrorEnvelope
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.wire.ApiTransport
import stapel.wire.BinaryBody
import stapel.wire.OperationCall
import stapel.wire.OperationSpec

/**
 * The wire as a script: each request takes the next queued answer (or [fallback]) and is
 * recorded. Feed real envelope bodies through the real pipeline — "mock the wire, not the module".
 */
public class FakeTransport : HttpTransport {
    private val script = ArrayDeque<(WireRequest) -> WireOutcome>()
    private val sent = mutableListOf<WireRequest>()

    /** Every request sent so far, oldest first. */
    public val requests: List<WireRequest> get() = sent.toList()

    /** Answers requests once the script is empty; null fails the test with the unexpected request. */
    public var fallback: ((WireRequest) -> WireOutcome)? = null

    /** Queues an answer computed from the request. */
    public fun enqueue(answer: (WireRequest) -> WireOutcome) {
        script.addLast(answer)
    }

    /** Queues a response with [status], a UTF-8 [body] and [headers]. */
    public fun enqueue(status: Int, body: String = "", headers: List<Pair<String, String>> = emptyList()) {
        enqueue { WireOutcome.Response(status, headers, body.encodeToByteArray()) }
    }

    /** Queues a JSON response. */
    public fun enqueueJson(status: Int, json: String) {
        enqueue(status, json, listOf("Content-Type" to "application/json"))
    }

    /**
     * Queues a Stapel error envelope: `{"localizable_error": code, "error": error,
     * "error_language": language, "params": {…}}` (string params; [extraJson] is spliced in as
     * further top-level members, e.g. `"verification": {…}`).
     */
    public fun enqueueEnvelope(
        status: Int,
        code: String,
        params: Map<String, String> = emptyMap(),
        error: String? = null,
        language: String? = null,
        extraJson: String? = null,
        headers: List<Pair<String, String>> = emptyList(),
    ) {
        val members = buildList {
            add("\"localizable_error\":${jsonString(code)}")
            if (error != null) add("\"error\":${jsonString(error)}")
            if (language != null) add("\"error_language\":${jsonString(language)}")
            add("\"params\":" + params.entries.joinToString(",", "{", "}") { "${jsonString(it.key)}:${jsonString(it.value)}" })
            if (extraJson != null) add(extraJson)
        }
        enqueue(status, members.joinToString(",", "{", "}"), listOf("Content-Type" to "application/json") + headers)
    }

    /** Queues a failure with no HTTP response. */
    public fun enqueueFailure(error: TransportFailure.NoResponse, requestSent: Boolean) {
        enqueue { WireOutcome.Failure(error, requestSent) }
    }

    /** Records [request] and answers it from the script. */
    override suspend fun send(request: WireRequest): WireOutcome {
        sent += request
        val answer = script.removeFirstOrNull() ?: fallback
            ?: throw AssertionError("FakeTransport: no scripted answer for request #${sent.size}: $request")
        return answer(request)
    }

    /** Nothing to release. */
    override fun close() {}

    /** How many scripted answers are still unused. */
    public val pending: Int get() = script.size
}

/** What a [FakeApiTransport] answers for one call. */
public sealed interface FakeAnswer {
    /**
     * Success with [value] (must be the operation's success type).
     *
     * @property value the decoded success value.
     */
    public class Value(public val value: Any?) : FakeAnswer

    /**
     * A coded refusal, turned into the operation's typed error by its own decoder.
     *
     * @property envelope the server's answer.
     */
    public class Refusal(public val envelope: ErrorEnvelope) : FakeAnswer

    /**
     * A transport failure, carried in the operation's error type by its own decoder.
     *
     * @property failure the failure.
     */
    public class Failure(public val failure: TransportFailure) : FakeAnswer
}

/**
 * An [ApiTransport] answering by operation id, for presenter and UI tests only — substrate and
 * brick tests use [FakeTransport] under the real pipeline. Errors are built through each
 * operation's own `ErrorDecoder`, so the result has exactly the types generated code returns.
 */
public class FakeApiTransport : ApiTransport {
    private val answers = mutableMapOf<String, (OperationCall<*>) -> FakeAnswer>()
    private val downloads = mutableMapOf<String, Pair<ByteArray, String?>>()
    private val recorded = mutableListOf<Pair<String, OperationCall<*>>>()

    /** Every call so far as (operation id, call), oldest first. */
    public val calls: List<Pair<String, OperationCall<*>>> get() = recorded.toList()

    /** Answers [operationId] with [answer]; a later registration replaces an earlier one. */
    public fun on(operationId: String, answer: (OperationCall<*>) -> FakeAnswer) {
        answers[operationId] = answer
    }

    /** Answers the download [operationId] with [bytes]. */
    public fun onDownload(operationId: String, bytes: ByteArray, contentType: String? = null) {
        downloads[operationId] = bytes to contentType
    }

    /** Answers from the registered handler; an unregistered operation fails the test. */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <B, T, E : StapelError> execute(spec: OperationSpec<B, T, E>, call: OperationCall<B>): StapelResult<T, E> {
        recorded += spec.id to call
        val answer = answers[spec.id] ?: throw AssertionError("FakeApiTransport: no answer for ${spec.id}")
        return when (val a = answer(call)) {
            is FakeAnswer.Value -> StapelResult.Ok(a.value as T)
            is FakeAnswer.Refusal -> StapelResult.Err(spec.errors.domain(a.envelope))
            is FakeAnswer.Failure -> StapelResult.Err(spec.errors.transport(a.failure))
        }
    }

    /** Hands the registered bytes to [consume]. */
    override suspend fun <B, R, E : StapelError> download(
        spec: OperationSpec<B, Unit, E>,
        call: OperationCall<B>,
        consume: suspend (BinaryBody) -> R,
    ): StapelResult<R, E> {
        recorded += spec.id to call
        val (bytes, type) = downloads[spec.id] ?: throw AssertionError("FakeApiTransport: no download for ${spec.id}")
        return StapelResult.Ok(consume(BytesBody(bytes, type)))
    }

    /** `fake://<operation id>`: no request is made either way. */
    override fun url(spec: OperationSpec<*, *, *>, call: OperationCall<*>): String = "fake://${spec.id}"
}

private class BytesBody(private val bytes: ByteArray, override val contentType: String?) : BinaryBody {
    override val contentLength: Long get() = bytes.size.toLong()

    override suspend fun readBytes(): ByteArray = bytes.copyOf()

    override suspend fun writeTo(sink: RawSink) {
        val buffer = Buffer()
        buffer.write(bytes)
        sink.write(buffer, buffer.size)
        sink.flush()
    }
}

private fun jsonString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    append('"')
}
