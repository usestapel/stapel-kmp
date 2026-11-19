package stapel.network.internal

import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.network.CallEvent
import stapel.network.HttpTransport
import stapel.network.NetworkConfig
import stapel.network.NetworkPlugin
import stapel.network.Recovery
import stapel.network.RefreshVerdict
import stapel.network.RequestAuthenticator
import stapel.network.RequestContext
import stapel.network.VerificationHandler
import stapel.network.VerificationOutcome
import stapel.network.WireBody
import stapel.network.WireOutcome
import stapel.network.WirePart
import stapel.network.WireRequest
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.classifyFailure
import stapel.runtime.Connectivity
import stapel.runtime.HttpCallEvent
import stapel.runtime.IdGenerator
import stapel.runtime.NetworkStatus
import stapel.runtime.StapelTelemetry
import stapel.wire.ApiTransport
import stapel.wire.AuthRequirement
import stapel.wire.BinaryBody
import stapel.wire.BodyEncoding
import stapel.wire.FormPart
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val VERIFICATION_TOKEN_HEADER = "X-Verification-Token"
internal const val REQUEST_ID_HEADER = "X-Request-ID"
internal const val AUTH_RECOVERY = "stapel.auth"
internal const val VERIFICATION_RECOVERY = "stapel.verification"

/** Everything the pipeline reads from its environment, as plain values (no config types). */
internal class PipelineEnvironment(
    /** Absolute URL of a canonical path on the endpoint answering a module (mount prefix rewritten). */
    val resolve: (module: String, path: String) -> String,
    /** Headers sent on every request: User-Agent, Accept-Language, client hints. Evaluated per attempt. */
    val defaultHeaders: () -> List<Pair<String, String>>,
    val ids: IdGenerator,
    val clock: Clock,
    val connectivity: Connectivity?,
    val telemetry: StapelTelemetry,
    val random: Random,
)

/**
 * The one loop above the engine: decorate, send, and on failure let each recovery (refresh,
 * step-up, plug-ins) turn it into at most one retry; then the transport retry policy. Nothing
 * below retries on its own.
 */
internal class Pipeline(
    private val transport: HttpTransport,
    private val env: PipelineEnvironment,
    private val config: NetworkConfig,
    private val authenticator: RequestAuthenticator,
    private val verification: VerificationHandler?,
    plugins: List<NetworkPlugin>,
    private val json: Json,
) : ApiTransport {
    private val plugins = plugins.sortedBy { it.order }

    init {
        val ids = plugins.map { it.id }
        require(ids.size == ids.toSet().size) { "duplicate network plug-in id in $ids" }
    }

    override suspend fun <B, T, E : StapelError> execute(spec: OperationSpec<B, T, E>, call: OperationCall<B>): StapelResult<T, E> =
        when (val outcome = run(spec, call)) {
            is Final.Failed -> StapelResult.Err(outcome.toError(spec))
            is Final.Succeeded -> decodeSuccess(spec, outcome.response)
        }

    override suspend fun <B, R, E : StapelError> download(
        spec: OperationSpec<B, Unit, E>,
        call: OperationCall<B>,
        consume: suspend (BinaryBody) -> R,
    ): StapelResult<R, E> = when (val outcome = run(spec, call)) {
        is Final.Failed -> StapelResult.Err(outcome.toError(spec))
        is Final.Succeeded -> StapelResult.Ok(consume(BufferedBinaryBody(outcome.response)))
    }

    override fun url(spec: OperationSpec<*, *, *>, call: OperationCall<*>): String =
        buildUrl(env.resolve(spec.module, spec.path), call.pathParams, call.query)

    private sealed interface Final {
        class Succeeded(val response: WireOutcome.Response) : Final

        /** [failure] is null when the server's refusal is decoded as the operation's domain error. */
        class Failed(val failure: TransportFailure?, val envelope: ErrorEnvelope?) : Final {
            fun <E : StapelError> toError(spec: OperationSpec<*, *, E>): E =
                if (failure != null) spec.errors.transport(failure) else spec.errors.domain(envelope!!)

            val code: String? get() = failure?.code?.value ?: envelope?.code?.value
        }
    }

    /** One failed attempt, classified once. */
    private class AttemptFailure(
        val failure: TransportFailure?,
        val envelope: ErrorEnvelope?,
        val requestSent: Boolean,
    ) {
        /** What recoveries see: the transport failure, or the server refusal as such. */
        val error: StapelError get() = failure ?: ServerRefusal(envelope!!)

        fun final() = Final.Failed(failure, envelope)
    }

    private suspend fun run(spec: OperationSpec<*, *, *>, call: OperationCall<*>): Final {
        val callId = env.ids.uuid()
        val started = env.clock.now()
        val context = CallContext(spec, callId, url(spec, call), call.headers, encodeBody(spec, call))
        val used = mutableSetOf<String>()
        var transportAttempts = 0
        var final: Final

        attempts@ while (true) {
            context.attempt++
            if (env.connectivity?.status?.value == NetworkStatus.Offline) {
                final = Final.Failed(TransportFailure.Offline("device offline"), null)
                break
            }
            val sentToken = if (spec.auth == AuthRequirement.None) null else authenticator.accessToken()
            if (sentToken == null && spec.auth == AuthRequirement.Required) {
                // A protected call with no session fails locally: no request is sent.
                final = Final.Failed(TransportFailure.Unauthenticated(null), null)
                break
            }
            for (plugin in plugins) plugin.decorate(context)
            transportAttempts++
            val attemptStarted = env.clock.now()
            val outcome = transport.send(context.wireRequest(sentToken, config, env.defaultHeaders()))
            val failed = classify(outcome, callId)
            reportReachability(outcome)
            observe(
                CallEvent.Attempt(
                    spec.id, callId, context.attempt,
                    (outcome as? WireOutcome.Response)?.status,
                    failed?.let { it.failure?.code?.value ?: it.envelope?.code?.value },
                    env.clock.now() - attemptStarted,
                ),
            )
            if (failed == null) {
                final = Final.Succeeded(outcome as WireOutcome.Response)
                break
            }

            // Recovery 1: refresh on a 401 for a request that carried a token.
            if (failed.envelope?.status == 401 && spec.auth != AuthRequirement.None) {
                if (sentToken == null || AUTH_RECOVERY in used) {
                    final = Final.Failed(TransportFailure.Unauthenticated(failed.envelope), failed.envelope)
                    break
                }
                used += AUTH_RECOVERY
                when (authenticator.refresh(sentToken)) {
                    is RefreshVerdict.Refreshed -> {
                        transportAttempts-- // a recovery does not consume a transport attempt
                        continue@attempts
                    }
                    RefreshVerdict.SessionEnded, RefreshVerdict.NoSession -> {
                        final = Final.Failed(TransportFailure.Unauthenticated(failed.envelope), failed.envelope)
                        break@attempts
                    }
                    RefreshVerdict.Unavailable -> {
                        // No verdict on the session: the call failed for a transient reason, not
                        // because the user must sign in again.
                        final = Final.Failed(TransportFailure.ConnectionFailed("session refresh reached no verdict"), null)
                        break@attempts
                    }
                }
            }

            // Recovery 2: step-up verification.
            val challenge = failed.failure as? TransportFailure.VerificationRequired
            if (challenge != null && verification != null && VERIFICATION_RECOVERY !in used) {
                used += VERIFICATION_RECOVERY
                when (val verified = verification.resolve(challenge.challenge, spec.id)) {
                    is VerificationOutcome.Verified -> {
                        verified.token?.let { context.setHeader(VERIFICATION_TOKEN_HEADER, it) }
                        transportAttempts--
                        continue@attempts
                    }
                    VerificationOutcome.Declined -> {
                        final = failed.final()
                        break@attempts
                    }
                }
            }

            // Recovery 3: plug-ins, each at most once per logical call.
            for (plugin in plugins) {
                if (plugin.id in used) continue
                when (val recovery = plugin.recover(context, failed.error)) {
                    Recovery.Pass -> Unit
                    is Recovery.Retry -> {
                        used += plugin.id
                        recovery.mutate(context)
                        transportAttempts--
                        continue@attempts
                    }
                    is Recovery.Fail -> {
                        final = Final.Failed(recovery.failure, recovery.failure.envelope)
                        break@attempts
                    }
                }
            }

            // Last: the transport retry policy.
            val wait = retryDelay(spec.kind, failed, transportAttempts)
            if (wait != null) {
                delay(wait)
                continue@attempts
            }
            final = failed.final()
            break
        }

        val duration = env.clock.now() - started
        val code = (final as? Final.Failed)?.code
        observe(CallEvent.Finished(spec.id, callId, context.attempt, code, duration))
        env.telemetry.httpCall(
            HttpCallEvent(spec.id, spec.method, (final as? Final.Succeeded)?.response?.status, code, duration, context.attempt),
        )
        return final
    }

    private fun classify(outcome: WireOutcome, callId: String): AttemptFailure? = when (outcome) {
        is WireOutcome.Failure -> AttemptFailure(outcome.error, null, outcome.requestSent)
        is WireOutcome.Response -> if (outcome.status in 200..299) {
            null
        } else {
            val requestId = outcome.header(REQUEST_ID_HEADER) ?: callId
            val decoded = decodeError(outcome.status, outcome.body, requestId)
            val failure = classifyFailure(decoded.envelope, decoded.challenge, parseRetryAfter(outcome.header("Retry-After")))
            AttemptFailure(failure, decoded.envelope, requestSent = true)
        }
    }

    private fun retryDelay(kind: OperationKind, failed: AttemptFailure, sent: Int): Duration? {
        val policy = config.retry
        if (sent >= policy.maxAttempts) return null
        val query = kind == OperationKind.Query
        val failure = failed.failure
        val backoff = { backoff(sent) }
        return when {
            failure is TransportFailure.TlsRejected -> null
            failure is TransportFailure.NoResponse && !failed.requestSent -> backoff()
            failure is TransportFailure.NoResponse && query -> backoff()
            failure is TransportFailure.Unavailable && query -> backoff()
            failure is TransportFailure.RateLimited && query -> failure.retryAfter?.takeIf { it <= policy.maxHonouredRetryAfter }
            else -> null
        }
    }

    /** Full jitter: uniform in [0, min(maxDelay, baseDelay · 2^(retry-1))]. */
    private fun backoff(retry: Int): Duration {
        val policy = config.retry
        val ceiling = (policy.baseDelay * (1 shl (retry - 1).coerceAtMost(16))).coerceAtMost(policy.maxDelay)
        val millis = ceiling.inWholeMilliseconds
        return if (millis <= 0) Duration.ZERO else env.random.nextLong(0, millis + 1).milliseconds
    }

    private fun reportReachability(outcome: WireOutcome) {
        val connectivity = env.connectivity ?: return
        when {
            outcome is WireOutcome.Response -> connectivity.reportReachable()
            outcome is WireOutcome.Failure && outcome.error !is TransportFailure.TlsRejected -> connectivity.reportUnreachable()
        }
    }

    private fun observe(event: CallEvent) {
        for (plugin in plugins) plugin.observe(event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, E : StapelError> decodeSuccess(spec: OperationSpec<*, T, E>, response: WireOutcome.Response): StapelResult<T, E> {
        val value: Any? = try {
            when (spec.responseKind) {
                ResponseKind.Empty -> Unit
                ResponseKind.Text -> response.body.decodeToString()
                ResponseKind.Binary -> response.body
                ResponseKind.Json -> {
                    val serializer = if (response.status in spec.success) {
                        spec.success[response.status]
                    } else {
                        spec.success.values.firstOrNull { it != null }
                    }
                    if (serializer == null) {
                        Unit
                    } else {
                        json.decodeFromString(serializer, response.body.decodeToString())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            return StapelResult.Err(spec.errors.transport(TransportFailure.ContractViolation(spec.id, e.message ?: "decode failed", response.status)))
        } catch (e: IllegalArgumentException) {
            return StapelResult.Err(spec.errors.transport(TransportFailure.ContractViolation(spec.id, e.message ?: "decode failed", response.status)))
        }
        return StapelResult.Ok(value as T)
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeBody(spec: OperationSpec<*, *, *>, call: OperationCall<*>): BodyState {
        val serializer = spec.body as KSerializer<Any?>?
        val payload: JsonElement? = if (serializer != null && call.body != null) json.encodeToJsonElement(serializer, call.body) else null
        return when (spec.bodyEncoding) {
            BodyEncoding.None -> BodyState.None
            BodyEncoding.Json -> if (payload == null) BodyState.None else BodyState.Json(payload)
            BodyEncoding.Form -> BodyState.Form(fieldsOf(payload).toMutableList())
            BodyEncoding.Multipart -> BodyState.Multipart(
                fieldsOf(payload).map { WirePart.Field(it.first, it.second) as WirePart }.toMutableList().apply {
                    for (part in call.parts) {
                        add(
                            when (part) {
                                is FormPart.Text -> WirePart.Field(part.name, part.value)
                                is FormPart.File -> WirePart.File(
                                    part.name,
                                    part.file.fileName,
                                    part.file.contentType,
                                    part.file.open().buffered().use { it.readByteArray() },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun fieldsOf(payload: JsonElement?): List<Pair<String, String>> {
        val obj = payload as? JsonObject ?: return emptyList()
        return obj.flatMap { (name, value) ->
            when (value) {
                JsonNull -> emptyList()
                is JsonPrimitive -> listOf(name to value.content)
                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.let { p -> name to p.content } }
                is JsonObject -> listOf(name to value.toString())
            }
        }
    }

    /** The mutable state of one logical call, shared by its attempts. */
    private inner class CallContext(
        override val operation: OperationSpec<*, *, *>,
        override val callId: String,
        override val url: String,
        private val callHeaders: List<Pair<String, String?>>,
        var body: BodyState,
    ) : RequestContext {
        override var attempt: Int = 0
        private val headers = linkedMapOf<String, Pair<String, String>>()

        override fun header(name: String): String? = headers[name.lowercase()]?.second

        override fun setHeader(name: String, value: String) {
            headers[name.lowercase()] = name to value
        }

        override fun removeHeader(name: String) {
            headers.remove(name.lowercase())
        }

        override fun setBodyField(name: String, value: String): Boolean = when (val current = body) {
            is BodyState.Json -> {
                val obj = current.value as? JsonObject
                if (obj == null) {
                    false
                } else {
                    body = BodyState.Json(JsonObject(obj + (name to JsonPrimitive(value))))
                    true
                }
            }
            is BodyState.Form -> {
                current.fields.removeAll { it.first == name }
                current.fields += name to value
                true
            }
            is BodyState.Multipart -> {
                current.parts.removeAll { it is WirePart.Field && it.name == name }
                current.parts += WirePart.Field(name, value)
                true
            }
            BodyState.None -> false
        }

        fun wireRequest(token: String?, config: NetworkConfig, defaults: List<Pair<String, String>>): WireRequest {
            val lines = ArrayList<Pair<String, String>>()
            val taken = mutableSetOf<String>()
            fun add(name: String, value: String) {
                if (taken.add(name.lowercase())) lines += name to value
            }
            // Explicit per-call state wins over defaults; Authorization is the pipeline's alone.
            for ((name, value) in headers.values) if (!name.equals("Authorization", ignoreCase = true)) add(name, value)
            if (token != null) add("Authorization", "Bearer $token")
            add("Accept", "application/json")
            add(REQUEST_ID_HEADER, callId)
            for ((name, value) in defaults) add(name, value)
            for ((name, value) in callHeaders) value?.let { add(name, it) }
            return WireRequest(operation.method.uppercase(), url, lines, body.toWire(json), config.timeouts)
        }
    }
}

/** The body of a call, kept structured so a recovery can add a field before the retry. */
internal sealed interface BodyState {
    data object None : BodyState

    class Json(val value: JsonElement) : BodyState

    class Form(val fields: MutableList<Pair<String, String>>) : BodyState

    class Multipart(val parts: MutableList<WirePart>) : BodyState

    fun toWire(json: kotlinx.serialization.json.Json): WireBody = when (this) {
        None -> WireBody.Empty
        is Json -> WireBody.Bytes(json.encodeToString(JsonElement.serializer(), value).encodeToByteArray(), "application/json")
        is Form -> WireBody.Bytes(
            fields.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }.encodeToByteArray(),
            "application/x-www-form-urlencoded",
        )
        is Multipart -> WireBody.Multipart(parts.toList())
    }
}

/** Fills `{placeholders}` of [template] in order with percent-encoded [pathParams] and appends [query] (null values dropped). */
internal fun buildUrl(template: String, pathParams: List<String>, query: List<Pair<String, String?>>): String {
    var index = 0
    val resolved = PLACEHOLDER.replace(template) {
        require(index < pathParams.size) { "missing path parameter ${it.value} in $template" }
        pathParams[index++].encodeURLPathPart().replace("/", "%2F")
    }
    require(index == pathParams.size) { "too many path parameters for $template" }
    val pairs = query.mapNotNull { (k, v) -> v?.let { "${k.encodeURLParameter()}=${it.encodeURLParameter()}" } }
    return resolved + if (pairs.isEmpty()) "" else "?" + pairs.joinToString("&")
}

private val PLACEHOLDER = Regex("\\{[^}/]+\\}")

private class BufferedBinaryBody(private val response: WireOutcome.Response) : BinaryBody {
    override val contentType: String? get() = response.header("Content-Type")
    override val contentLength: Long get() = response.body.size.toLong()

    override suspend fun readBytes(): ByteArray = response.body

    override suspend fun writeTo(sink: RawSink) {
        val buffer = Buffer()
        buffer.write(response.body)
        sink.write(buffer, buffer.size)
        sink.flush()
    }
}
