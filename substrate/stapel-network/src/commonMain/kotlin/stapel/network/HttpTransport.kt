package stapel.network

import stapel.result.TransportFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What "the wire" means to the pipeline: one HTTP exchange, no retries, no redirects, no cookies,
 * no authentication. The platform implementation ([platformTransport]) wraps a Ktor engine; tests pass
 * a fake. Every policy decision (auth, refresh, retry, captcha) is made above this interface.
 */
public interface HttpTransport : AutoCloseable {
    /** Sends [request] once. Never throws for an I/O fault: a fault is a [WireOutcome.Failure]. */
    public suspend fun send(request: WireRequest): WireOutcome
}

/** One request as it goes on the wire. [headers] are final: the transport adds none of its own. */
public class WireRequest(
    /** HTTP method, upper case (`GET`, `POST`, …). */
    public val method: String,
    /** Absolute URL. */
    public val url: String,
    /** Header lines in order; a repeated name is sent repeatedly. */
    public val headers: List<Pair<String, String>>,
    /** The body. */
    public val body: WireBody = WireBody.Empty,
    /** Time limits for this exchange. */
    public val timeouts: TimeoutPolicy = TimeoutPolicy(),
) {
    /** First value of header [name] (case-insensitive), or null. */
    public fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    /** Method and URL only: headers carry credentials. */
    override fun toString(): String = "WireRequest($method $url)"
}

/** A request body. */
public sealed interface WireBody {
    /** No body. */
    public data object Empty : WireBody

    /** Bytes with their media type. */
    public class Bytes(
        /** The payload. */
        public val bytes: ByteArray,
        /** `Content-Type` of the payload. */
        public val contentType: String,
    ) : WireBody {
        /** The payload decoded as UTF-8 (for JSON bodies and diagnostics). */
        public fun text(): String = bytes.decodeToString()
    }

    /** `multipart/form-data`, encoded by the transport. */
    public class Multipart(
        /** Parts in order. */
        public val parts: List<WirePart>,
    ) : WireBody
}

/** One part of a [WireBody.Multipart]. */
public sealed interface WirePart {
    /** The form field name. */
    public val name: String

    /** A text field. */
    public class Field(override val name: String, /** The value. */ public val value: String) : WirePart

    /** A file. */
    public class File(
        override val name: String,
        /** File name sent in `Content-Disposition`. */
        public val fileName: String,
        /** Media type of the file, or null for `application/octet-stream`. */
        public val contentType: String?,
        /** The content. */
        public val bytes: ByteArray,
    ) : WirePart
}

/** Outcome of [HttpTransport.send]. */
public sealed interface WireOutcome {
    /** The server answered (any status). `Set-Cookie` is never present: it is dropped by construction. */
    public class Response(
        /** HTTP status code. */
        public val status: Int,
        /** Response header lines (names as received). */
        public val headers: List<Pair<String, String>>,
        /** Whole response body. */
        public val body: ByteArray,
    ) : WireOutcome {
        /** First value of header [name] (case-insensitive), or null. */
        public fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

        /** Status and size only. */
        override fun toString(): String = "Response($status, ${body.size} bytes)"
    }

    /** No HTTP response. */
    public class Failure(
        /** What went wrong. */
        public val error: TransportFailure.NoResponse,
        /**
         * Whether the request may have reached the server. `false` only when it provably did not
         * (connect refused, DNS, TLS handshake not completed); unknown counts as sent.
         */
        public val requestSent: Boolean,
    ) : WireOutcome {
        /** The failure and whether the request may have been sent. */
        override fun toString(): String = "Failure($error, sent=$requestSent)"
    }
}

/** Time limits for one exchange. */
public data class TimeoutPolicy(
    /** Limit for establishing the connection. */
    val connect: Duration = 10.seconds,
    /** Limit between two received bytes. */
    val socket: Duration = 30.seconds,
    /** Limit for the whole exchange. */
    val request: Duration = 60.seconds,
)
