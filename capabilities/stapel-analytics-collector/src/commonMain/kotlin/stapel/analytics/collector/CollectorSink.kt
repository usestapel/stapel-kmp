package stapel.analytics.collector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import stapel.analytics.AnalyticsEvent
import stapel.analytics.AnalyticsSink
import stapel.analytics.ConsentPurpose
import stapel.analytics.SinkAvailability
import stapel.analytics.SinkReceipt
import stapel.analytics.collector.api.AnalyticsOperations
import stapel.analytics.collector.api.model.IngestReceipt
import stapel.result.StapelResult
import stapel.wire.ApiTransport
import stapel.wire.OperationCall
import stapel.wire.OperationSpec

/**
 * Options of [CollectorSink].
 *
 * @property source each event's `source` and the batch's `context.platform`: `ios`, `android` or `desktop`.
 * @property writeKey the project's write key for this platform (an attribution label, not a
 *   secret), or null when the backend does not require one.
 * @property appVersion the app's version for `context.app_version`, or null.
 * @property osVersion the OS version for `context.os_version`, or null.
 * @property locale the BCP 47 locale for `context.locale`, or null.
 */
public class CollectorOptions(
    public val source: String,
    public val writeKey: String? = null,
    public val appVersion: String? = null,
    public val osVersion: String? = null,
    public val locale: (() -> String?)? = null,
)

/**
 * The first-party [AnalyticsSink] (`id = "stapel"`): delivers each hub batch to
 * `POST /analytics/api/v1/events` of the project's own `stapel-analytics` brick, over the **bare**
 * transport (the endpoint authenticates nothing; the identity is the hashed user id in the payload).
 * Works on every platform, desktop included.
 *
 * Receipt mapping: a 202 with no rejections is accepted; rejected entries (`{index, name, reason}`)
 * become [SinkReceipt.Partial] and are never re-sent; a refused batch (400 shape / too large, 401
 * write key, 413 body too large) is dropped as rejected with the error code, because re-sending the
 * same bytes cannot succeed; no response, a 5xx, a 429 or anything unexpected is [SinkReceipt.Retry].
 *
 * The operation is the generated `analytics_api_v1_events_create` (path, method, auth, success and
 * error decoding all come from [AnalyticsOperations.events]) with one difference: the body is
 * [CollectorBatch], whose `ts` is a 64-bit integer. The pinned contract types `IngestEvent.ts` as
 * `integer` without `format: int64`, which the generator maps to `Int`, too small for epoch
 * milliseconds (backend follow-up: declare `format: int64`). `CollectorWireTest` fails when the
 * generated `Ingest` / `IngestEvent` / `IngestContext` gain or lose a field this body does not carry.
 *
 * @param transport the bare transport (`get<ApiTransport>(NetworkQualifiers.Bare)`).
 * @param options source, write key, client context.
 */
public class CollectorSink(private val transport: ApiTransport, private val options: CollectorOptions) : AnalyticsSink {
    /** `stapel`. */
    override val id: String get() = "stapel"

    /** Always ready: the endpoint is the app's own backend. */
    override val availability: SinkAvailability get() = SinkAvailability.Ready

    /** Analytics only. */
    override val purposes: Set<ConsentPurpose> get() = setOf(ConsentPurpose.Analytics)

    /** No-op: the first-party collector collects nothing on its own. */
    override fun setCollectionEnabled(enabled: Boolean) {}

    /** Sends [batch] as one ingest request; see the class description for the receipt mapping. */
    override suspend fun deliver(batch: List<AnalyticsEvent>): SinkReceipt {
        if (batch.isEmpty()) return SinkReceipt.Accepted
        val body = CollectorBatch(
            events = batch.map { e ->
                CollectorEvent(
                    id = e.id, kind = e.kind.wire, name = e.name,
                    props = JsonObject(e.props.mapValues { JsonPrimitive(it.value) }),
                    userHash = e.userHash, ts = e.timestamp.toEpochMilliseconds(),
                    anonId = e.anonId, sessionId = e.sessionId, source = options.source,
                )
            },
            writeKey = options.writeKey,
            anonId = batch.first().anonId,
            sessionId = batch.first().sessionId,
            context = CollectorContext(options.source, options.appVersion, options.osVersion, options.locale?.invoke(), SDK),
        )
        return when (val result = transport.execute(INGEST, OperationCall(body = body))) {
            is StapelResult.Ok -> receiptOf(result.value, batch)
            is StapelResult.Err -> {
                val error = result.error
                val status = error.status
                // 401 arrives as the transport family's Unauthenticated: on this anonymous endpoint it
                // can only mean the write key, which re-sending does not fix.
                if (status != null && status in REFUSED) {
                    SinkReceipt.Partial(batch.associate { it.id to error.code.value })
                } else {
                    SinkReceipt.Retry(error.code.value)
                }
            }
        }
    }

    /** No-op: the ingest contract has no user properties. */
    override fun setUserProperty(name: String, value: String?) {}

    /** No-op: the collector keeps no identity; the hub stops attaching the hash. */
    override fun reset() {}

    internal companion object {
        const val SDK: String = "stapel-kmp"
        val REFUSED: Set<Int> = setOf(400, 401, 413)

        val INGEST: OperationSpec<CollectorBatch, IngestReceipt, *> = AnalyticsOperations.events.let { g ->
            OperationSpec(
                id = g.id, module = g.module, method = g.method, path = g.path, kind = g.kind, auth = g.auth,
                bodyEncoding = g.bodyEncoding, body = CollectorBatch.serializer(), responseKind = g.responseKind,
                success = g.success, errors = g.errors, verificationScope = g.verificationScope, captcha = g.captcha,
                capability = g.capability,
            )
        }

        fun receiptOf(receipt: IngestReceipt, batch: List<AnalyticsEvent>): SinkReceipt {
            val rejected = receipt.rejected.orEmpty()
            if (rejected.isEmpty()) return SinkReceipt.Accepted
            val map = LinkedHashMap<String, String>()
            rejected.forEachIndexed { i, r ->
                val index = (r["index"] as? JsonPrimitive)?.intOrNull
                val id = index?.let { batch.getOrNull(it)?.id } ?: "unindexed-$i"
                map[id] = r.text("reason") ?: r.text("detail") ?: "rejected"
            }
            return SinkReceipt.Partial(map)
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
    }
}

/** The ingest body: the generated `Ingest` with a 64-bit `ts` per event (see [CollectorSink]). */
@Serializable
internal class CollectorBatch(
    @SerialName("events") val events: List<CollectorEvent>,
    @SerialName("write_key") val writeKey: String? = null,
    @SerialName("anon_id") val anonId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("context") val context: CollectorContext? = null,
)

/** One event of [CollectorBatch]: the generated `IngestEvent` with `ts` as epoch milliseconds in a `Long`. */
@Serializable
internal class CollectorEvent(
    @SerialName("id") val id: String,
    @SerialName("kind") val kind: String,
    @SerialName("name") val name: String,
    @SerialName("props") val props: JsonObject,
    @SerialName("userHash") val userHash: String? = null,
    @SerialName("ts") val ts: Long,
    @SerialName("anon_id") val anonId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("source") val source: String? = null,
)

/** The batch's client context: the generated `IngestContext`. */
@Serializable
internal class CollectorContext(
    @SerialName("platform") val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("sdk") val sdk: String? = null,
)
