package stapel.result

import kotlin.jvm.JvmInline

/**
 * An error code: the wire's `localizable_error` (`error.400.invalid_code`) or a code the client
 * synthesizes itself (`stapel.http.503`, `stapel.transport.failed`; see [CoreErrorCodes]).
 *
 * @property value the code text.
 */
@JvmInline
public value class ErrorCode(public val value: String) {
    /**
     * The HTTP status the code names by convention — `error.<status>.<slug>` or
     * `stapel.http.<status>` — or null when it names none.
     */
    public val embeddedStatus: Int?
        get() {
            val digits = when {
                value.startsWith("error.") -> value.removePrefix("error.").substringBefore('.')
                value.startsWith(CoreErrorCodes.HTTP_PREFIX) -> value.removePrefix(CoreErrorCodes.HTTP_PREFIX)
                else -> return null
            }
            if (digits.length != 3 || digits.any { it !in '0'..'9' }) return null
            return digits.toInt()
        }

    /** True for codes the client mints itself (`stapel.*`), never sent by a backend. */
    public val isClientSynthesized: Boolean get() = value.startsWith("stapel.")

    /** The code text. */
    override fun toString(): String = value
}

/** Codes the client synthesizes. Backend codes come from the generated catalogs. */
public object CoreErrorCodes {
    /** Prefix of the per-status codes minted for a failed response without an error envelope. */
    public const val HTTP_PREFIX: String = "stapel.http."

    /** No HTTP response was received (offline, timeout, connection or TLS failure). */
    public val TransportFailed: ErrorCode = ErrorCode("stapel.transport.failed")

    /** A failure nothing more specific describes. */
    public val Unknown: ErrorCode = ErrorCode("stapel.error.unknown")

    /** A 2xx body that does not match the generated model. */
    public val ContractDecode: ErrorCode = ErrorCode("stapel.contract.decode")

    /** Catalog key of the technical-detail line (`HTTP {status}`), not an error itself. */
    public val Detail: ErrorCode = ErrorCode("stapel.error.detail")

    /** Catalog key of the technical-detail line when a request id is known. */
    public val DetailWithRequestId: ErrorCode = ErrorCode("stapel.error.detail_with_request")

    /** An action is blocked because what it needs is still loading. */
    public val ActionBlockedLoading: ErrorCode = ErrorCode("stapel.action.blocked.loading")

    /** An action is blocked because what it needs failed to load. */
    public val ActionBlockedLoadFailed: ErrorCode = ErrorCode("stapel.action.blocked.load_failed")

    /** `stapel.http.<status>`: the code of a failed response that carried no error envelope. */
    public fun http(status: Int): ErrorCode = ErrorCode("$HTTP_PREFIX$status")
}

/**
 * The envelope's `params`, flattened to text. Nested JSON values are kept as compact JSON text.
 */
public class ErrorParams(values: Map<String, String>) {
    private val values: Map<String, String> = values.toMap()

    /** The raw text of [name], or null when absent. */
    public operator fun get(name: String): String? = values[name]

    /** [name] parsed as a whole number, or null when absent or not a number. */
    public fun long(name: String): Long? = values[name]?.trim()?.let { text ->
        text.toLongOrNull() ?: text.toDoubleOrNull()?.takeIf { it.isFinite() && it == kotlin.math.floor(it) }?.toLong()
    }

    /** [name] parsed as an `Int`, or null when absent, not a number or out of range. */
    public fun int(name: String): Int? = long(name)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** The parameter names, in no particular order. */
    public val names: Set<String> get() = values.keys

    /** True when there are no parameters. */
    public fun isEmpty(): Boolean = values.isEmpty()

    /** A copy of all parameters. */
    public fun asMap(): Map<String, String> = values.toMap()

    /** Equal when both hold the same names and values. */
    override fun equals(other: Any?): Boolean = other is ErrorParams && other.values == values

    /** Consistent with [equals]. */
    override fun hashCode(): Int = values.hashCode()

    /** Debug form listing every parameter. */
    override fun toString(): String = "ErrorParams($values)"

    /** Holder of [Empty]. */
    public companion object {
        /** No parameters. */
        public val Empty: ErrorParams = ErrorParams(emptyMap())
    }
}

/**
 * What the server answered for a failed request, or what the client synthesized in its place
 * when the response carried no error envelope (then [code] is `stapel.http.<status>` and
 * [serverText] is null).
 *
 * @property code `localizable_error`, or the synthesized `stapel.http.<status>`.
 * @property status HTTP status of the response.
 * @property params the envelope's `params`.
 * @property serverText the envelope's `error` text; a debug fallback, shown only through the
 *   error-text ladder (and only when [serverLanguage] matches the display locale).
 * @property serverLanguage the locale [serverText] is written in (`error_language`).
 * @property requestId the echoed `X-Request-ID`, or the id the client sent.
 */
public data class ErrorEnvelope(
    public val code: ErrorCode,
    public val status: Int,
    public val params: ErrorParams = ErrorParams.Empty,
    public val serverText: String? = null,
    public val serverLanguage: String? = null,
    public val requestId: String? = null,
) {
    /** True when the body carried no envelope and [code] was synthesized by the client. */
    public val isSynthesized: Boolean get() = code.isClientSynthesized

    /** Holder of [synthesized]. */
    public companion object {
        /** The envelope of a failed response with no (parsable) error body. */
        public fun synthesized(status: Int, requestId: String? = null): ErrorEnvelope =
            ErrorEnvelope(code = CoreErrorCodes.http(status), status = status, requestId = requestId)
    }
}
