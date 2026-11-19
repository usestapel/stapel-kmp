package stapel.navigation

/**
 * Path grammar of the web manifest: `/`-separated segments, `:name` segments are parameters, a
 * leading `/` makes the path absolute (`"/l/:id"`), otherwise it is relative to its parent or to the
 * member area (`"settings"`).
 *
 * @property raw the pattern as declared.
 */
public class PathPattern(public val raw: String) {
    private val segments: List<String> = raw.split('/').filter { it.isNotEmpty() }

    /** True when [raw] starts with `/`. */
    public val isAbsolute: Boolean = raw.startsWith("/")

    /** Parameter names in order of appearance. */
    public val params: List<String> = segments.filter { it.startsWith(":") }.map { it.substring(1) }

    /** Number of literal (non-parameter) segments; more literals = a more specific pattern. */
    public val literalCount: Int = segments.count { !it.startsWith(":") }

    /**
     * Matches already-decoded path [segments] and returns the parameter values, or null when the
     * segment count or a literal differs.
     */
    public fun match(segments: List<String>): Map<String, String>? {
        if (segments.size != this.segments.size) return null
        val out = LinkedHashMap<String, String>()
        for (i in segments.indices) {
            val p = this.segments[i]
            if (p.startsWith(":")) {
                if (segments[i].isEmpty()) return null
                out[p.substring(1)] = segments[i]
            } else if (p != segments[i]) {
                return null
            }
        }
        return out
    }

    /**
     * Formats the pattern with [params] (percent-encoded). Keeps the leading `/` of an absolute
     * pattern. Throws [IllegalArgumentException] when a parameter is missing.
     */
    public fun format(params: Map<String, String>): String {
        val body = segments.joinToString("/") { s ->
            if (s.startsWith(":")) {
                val name = s.substring(1)
                val v = requireNotNull(params[name]) { "path '$raw' needs parameter '$name'" }
                percentEncode(v)
            } else {
                s
            }
        }
        return if (isAbsolute) "/$body" else body
    }

    /** Equal when the raw patterns are equal. */
    override fun equals(other: Any?): Boolean = other is PathPattern && other.raw == raw

    /** Hash of [raw]. */
    override fun hashCode(): Int = raw.hashCode()

    /** The raw pattern. */
    override fun toString(): String = raw
}

/**
 * Path and query parameters of one route instance.
 *
 * @property path path parameters by name (decoded).
 * @property query query parameters by name, in order (decoded).
 */
public class RouteAddress(
    public val path: Map<String, String> = emptyMap(),
    public val query: Map<String, List<String>> = emptyMap(),
)

/**
 * Converts between a route and its path/query parameters. Explicit lambdas: no reflection and no
 * experimental decoder, so it works the same on every target.
 *
 * @property parse builds the route from decoded parameters, or returns null when they do not fit.
 * @property format the parameters of a route; the inverse of [parse].
 */
public class PathCodec<R : Route>(
    public val parse: (path: Map<String, String>, query: Map<String, List<String>>) -> R?,
    public val format: (R) -> RouteAddress,
) {
    /** Factories. */
    public companion object {
        /** Codec of a parameterless route (a `data object`). */
        public fun <R : Route> constant(route: R): PathCodec<R> =
            PathCodec(parse = { _, _ -> route }, format = { RouteAddress() })
    }
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val HEX = "0123456789ABCDEF"

internal fun percentEncode(value: String): String = buildString {
    for (b in value.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        if (c < 0x80 && UNRESERVED.indexOf(c.toChar()) >= 0) {
            append(c.toChar())
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }
}

/** Decodes `%XX` sequences (and `+` as space when [plusIsSpace]); malformed escapes are kept as is. */
internal fun percentDecode(value: String, plusIsSpace: Boolean = false): String {
    if ('%' !in value && !(plusIsSpace && '+' in value)) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        val hi = if (ch == '%' && i + 2 < value.length) HEX.indexOf(value[i + 1].uppercaseChar()) else -1
        val lo = if (hi >= 0) HEX.indexOf(value[i + 2].uppercaseChar()) else -1
        when {
            hi >= 0 && lo >= 0 -> {
                bytes += ((hi shl 4) or lo).toByte()
                i += 3
            }
            plusIsSpace && ch == '+' -> {
                bytes += ' '.code.toByte()
                i++
            }
            else -> {
                ch.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
