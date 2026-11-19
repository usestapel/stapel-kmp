package stapel.tools.tokens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * JSON with JavaScript semantics, so the Kotlin port produces the same bytes as the Node resolver:
 * property order of a JS object (integer-like keys first, ascending; then insertion order),
 * `JSON.stringify` escaping and number formatting, `String(x)` for template interpolation.
 */
public object JsJson {
    private val parser = Json { isLenient = false }

    /** Parses JSON text into a [JsonElement] (objects keep insertion order). */
    public fun parse(text: String): JsonElement = parser.parseToJsonElement(text)

    /** `JSON.stringify(value)` (indent = 0) or `JSON.stringify(value, null, indent)`. */
    public fun stringify(value: JsonElement, indent: Int = 0): String =
        StringBuilder().also { write(it, value, indent, "") }.toString()

    /** Hex SHA-256 of `JSON.stringify(value)` (UTF-8), as the bin computes it. */
    public fun sha256(value: JsonElement): String =
        MessageDigest.getInstance("SHA-256").digest(stringify(value).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** The keys of [obj] in JavaScript property order. */
    public fun keys(obj: JsonObject): List<String> {
        val (ints, rest) = obj.keys.partition(::isArrayIndex)
        return ints.sortedBy { it.toLong() } + rest
    }

    /** The entries of [obj] in JavaScript property order. */
    public fun entries(obj: JsonObject): List<Pair<String, JsonElement>> = keys(obj).map { it to obj.getValue(it) }

    /** `Object.entries(record).sort(by key)` — UTF-16 code unit order, as `a < b` compares strings. */
    public fun sortedEntries(obj: JsonObject?): List<Pair<String, JsonElement>> =
        obj?.entries?.map { it.key to it.value }?.sortedBy { it.first }.orEmpty()

    private fun isArrayIndex(key: String): Boolean =
        key.isNotEmpty() && key.length <= 10 && key.all { it in '0'..'9' } &&
            (key == "0" || key[0] != '0') && key.toLong() < 4294967295L

    /** JavaScript `String(value)` of a JSON value (used where Node interpolates into a message). */
    public fun jsString(value: JsonElement?): String = when (value) {
        null -> "undefined"
        is JsonNull -> "null"
        is JsonPrimitive -> if (value.isString) value.content else formatNumberOrLiteral(value)
        is JsonArray -> value.joinToString(",") { if (it is JsonNull) "" else jsString(it) }
        is JsonObject -> "[object Object]"
    }

    /** JavaScript truthiness of a JSON value (absent = `undefined` = falsy). */
    public fun truthy(value: JsonElement?): Boolean = when (value) {
        null, is JsonNull -> false
        is JsonPrimitive -> when {
            value.isString -> value.content.isNotEmpty()
            value.content == "true" -> true
            value.content == "false" -> false
            else -> value.content.toDouble().let { it != 0.0 && !it.isNaN() }
        }
        else -> true
    }

    private fun formatNumberOrLiteral(p: JsonPrimitive): String =
        if (p.content == "true" || p.content == "false") p.content else formatNumber(p.content.toDouble())

    /** `Number.prototype.toString()` for a finite double. */
    public fun formatNumber(d: Double): String {
        if (d == 0.0) return "0"
        val abs = kotlin.math.abs(d)
        if (d == kotlin.math.floor(d) && abs < 1e21) return BigDecimal(d).toPlainString()
        val shortest = BigDecimal(d.toString()) // JDK 19+: shortest round-tripping digits
        if (abs >= 1e-6 && abs < 1e21) return shortest.stripTrailingZeros().toPlainString()
        val unscaled = shortest.unscaledValue().abs().toString().trimEnd('0').ifEmpty { "0" }
        val exponent = shortest.precision() - shortest.scale() - 1
        val mantissa = if (unscaled.length == 1) unscaled else unscaled[0] + "." + unscaled.substring(1)
        return (if (d < 0) "-" else "") + mantissa + "e" + (if (exponent >= 0) "+" else "") + exponent
    }

    /** `x.toFixed(digits)` (exact binary value, ties away from zero like the spec's "larger n"). */
    public fun toFixed(d: Double, digits: Int): String =
        BigDecimal(d).setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString()

    /** `JSON.stringify` of one string. */
    public fun quote(s: String): String = StringBuilder().also { writeString(it, s) }.toString()

    private fun write(out: StringBuilder, v: JsonElement, indent: Int, current: String) {
        when (v) {
            is JsonNull -> out.append("null")
            is JsonPrimitive -> when {
                v.isString -> writeString(out, v.content)
                else -> out.append(formatNumberOrLiteral(v))
            }
            is JsonArray -> {
                if (v.isEmpty()) {
                    out.append("[]"); return
                }
                val inner = current + " ".repeat(indent)
                out.append('[')
                v.forEachIndexed { i, e ->
                    if (i > 0) out.append(',')
                    if (indent > 0) out.append('\n').append(inner)
                    write(out, e, indent, inner)
                }
                if (indent > 0) out.append('\n').append(current)
                out.append(']')
            }
            is JsonObject -> {
                if (v.isEmpty()) {
                    out.append("{}"); return
                }
                val inner = current + " ".repeat(indent)
                out.append('{')
                entries(v).forEachIndexed { i, (k, e) ->
                    if (i > 0) out.append(',')
                    if (indent > 0) out.append('\n').append(inner)
                    writeString(out, k)
                    out.append(if (indent > 0) ": " else ":")
                    write(out, e, indent, inner)
                }
                if (indent > 0) out.append('\n').append(current)
                out.append('}')
            }
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    out.append(c).append(s[i + 1]); i++
                }
                c.isSurrogate() -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }
}
