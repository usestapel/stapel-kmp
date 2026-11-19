package stapel.network

import stapel.config.StapelEnvironment
import stapel.network.internal.Pipeline
import stapel.network.internal.PipelineEnvironment
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.StapelTelemetry
import stapel.runtime.warn
import stapel.wire.ApiTransport
import stapel.wire.WireJson
import kotlin.random.Random
import kotlin.time.Clock

/** What the transport reads from the app: where modules answer, who the client is, time, ids. */
public class NetworkDependencies(
    /** Endpoints per module, posture and client identity. */
    public val environment: StapelEnvironment,
    /** Time source (durations in telemetry). */
    public val clock: Clock,
    /** Source of `X-Request-ID` values. */
    public val ids: IdGenerator,
    /** The effective app locale, sent as `Accept-Language`; null sends none. */
    public val locale: LocaleProvider? = null,
    /** When present: `Offline` short-circuits calls, and every outcome reports reachability back. */
    public val connectivity: Connectivity? = null,
    /** Diagnostics; never receives header values or bodies. */
    public val logger: StapelLogger = NoopLogger,
    /** Per-call telemetry. */
    public val telemetry: StapelTelemetry = StapelTelemetry.Noop,
    /** Jitter source for retry backoff. */
    public val random: Random = Random,
)

/**
 * The [ApiTransport] generated clients call: one recover-once pipeline above the platform engine.
 *
 * Per logical call: default headers (`Accept`, `Accept-Language`, `User-Agent`, client hints,
 * `X-Request-ID` — the same id on every attempt), the bearer from [authenticator] (never a cookie),
 * plug-in decoration, one send; then on failure, in order and each at most once: refresh on a 401
 * for a request that carried a token, step-up through [verification] on a 403 challenge, each of
 * [plugins]; last, the transport [NetworkConfig.retry] policy. A 401 after the refresh-and-retry is
 * returned as `Unauthenticated`; a refresh without verdict fails the call as a transient
 * `ConnectionFailed` and never as "signed out".
 *
 * Pass [RequestAuthenticator.None] (the default) for the bare client a refresh call must use.
 */
public fun StapelTransport(
    deps: NetworkDependencies,
    config: NetworkConfig = NetworkConfig(),
    authenticator: RequestAuthenticator = RequestAuthenticator.None,
    verification: VerificationHandler? = null,
    plugins: List<NetworkPlugin> = emptyList(),
    transport: HttpTransport = platformTransport(config.pins),
): ApiTransport {
    val environment = deps.environment
    if (environment.posture == stapel.config.Posture.Live && config.pins.isEmpty) {
        deps.logger.warn("stapel.network") { "live posture without certificate pins (allowed; an operational choice)" }
    }
    val client = environment.client
    val pipelineEnvironment = PipelineEnvironment(
        resolve = environment::url,
        defaultHeaders = {
            buildList {
                add("User-Agent" to client.userAgent)
                deps.locale?.locale?.value?.let { add("Accept-Language" to acceptLanguage(it.value)) }
                client.clientHints.forEach { (name, value) -> add(name to value) }
            }
        },
        ids = deps.ids,
        clock = deps.clock,
        connectivity = deps.connectivity,
        telemetry = deps.telemetry,
        random = deps.random,
    )
    return Pipeline(transport, pipelineEnvironment, config, authenticator, verification, plugins, WireJson)
}

/** `ru-RU` → `ru-RU,ru;q=0.9`; a bare language is sent as is. The backend reads the primary subtag. */
internal fun acceptLanguage(tag: String): String {
    val language = tag.substringBefore('-').substringBefore('_')
    return if (language.isEmpty() || language == tag) tag else "$tag,$language;q=0.9"
}
