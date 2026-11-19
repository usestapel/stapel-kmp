package stapel.tools.tokens

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToLong

/** The CSS-text parsers of the `resolved@1` target (colour literals, one shadow, a font stack). */
public object ColorText {
    private val HEX_ANY_RE = Regex("^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$", RegexOption.IGNORE_CASE)
    private val RGB_FN_RE =
        Regex("^rgba?\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)$", RegexOption.IGNORE_CASE)
    private val LENGTH_RE = Regex("^(-?[0-9]*\\.?[0-9]+)(px)?$")
    private val QUOTED_RE = Regex("^(['\"])(.*)\\1$")
    private val COLOR_START_RE = Regex("#|rgba?\\(", RegexOption.IGNORE_CASE)

    private fun refuse(code: String, message: String): Nothing =
        throw ThemeException(ThemeProblem(code, message, ThemeProblem.Severity.Error))

    /** JS `Math.round` (ties toward +infinity). */
    private fun jsRound(d: Double): Long = kotlin.math.floor(d + 0.5).roundToLong()

    private fun hex2(n: Double): String = jsRound(n).toString(16).padStart(2, '0')

    /** `#rrggbb` or `#rrggbbaa` (lower case) from `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`. */
    public fun normalise(value: JsonElement?, where: String): String {
        val s = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (s != null && HEX_ANY_RE.matches(s)) {
            var h = s.substring(1).lowercase()
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            if (h.length == 8 && h.endsWith("ff")) h = h.substring(0, 6)
            return "#$h"
        }
        val m = s?.let { RGB_FN_RE.matchEntire(it.trim()) }
        if (m != null) {
            val (r, g, b) = (1..3).map { m.groupValues[it].toDouble() }
            val a = m.groups[4]?.value?.toDouble() ?: 1.0
            val alpha = jsRound(a * 255)
            return "#${hex2(r)}${hex2(g)}${hex2(b)}${if (alpha == 255L) "" else hex2(alpha.toDouble())}"
        }
        val shown = value?.let { JsJson.stringify(it) } ?: "undefined"
        refuse("resolved-color", "tokens: $where → $shown — resolved@1 accepts #rgb, #rrggbb, #rrggbbaa, rgb() or rgba() only")
    }

    private fun lengthPx(token: String, where: String): Double {
        val m = LENGTH_RE.matchEntire(token)
        if (m == null || (m.groups[2] == null && m.groupValues[1].toDouble() != 0.0)) {
            refuse("resolved-shadow", "tokens: $where — \"$token\" is not a px length")
        }
        return m.groupValues[1].toDouble()
    }

    private fun num(d: Double): JsonPrimitive = JsonPrimitive(d)

    /** `none` → [JsonNull]; `<x> <y> <blur> [<spread>] <color>` → `{raw, x, y, blur, spread, color}`. */
    public fun parseShadow(raw: JsonElement?, where: String): JsonElement {
        val s = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (s == "none") return JsonNull
        val shown = raw?.let { JsJson.stringify(it) } ?: "undefined"
        if (s == null || s.contains("inset")) refuse("resolved-shadow", "tokens: $where → $shown — resolved@1 accepts \"none\" or one non-inset shadow")
        val colorStart = COLOR_START_RE.find(s)?.range?.first ?: refuse("resolved-shadow", "tokens: $where → $shown — no shadow colour")
        val colorPart = s.substring(colorStart).trim()
        if (colorPart.contains(")") && colorPart.indexOf(')') != colorPart.length - 1) {
            refuse("resolved-shadow", "tokens: $where → $shown — resolved@1 accepts one shadow, not a list")
        }
        val lengths = s.substring(0, colorStart).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (lengths.size < 3 || lengths.size > 4) {
            refuse("resolved-shadow", "tokens: $where → $shown — expected <x> <y> <blur> [<spread>] <color>")
        }
        val spread = lengths.getOrElse(3) { "0" }
        return buildJsonObject {
            put("raw", JsonPrimitive(s))
            put("x", num(lengthPx(lengths[0], where)))
            put("y", num(lengthPx(lengths[1], where)))
            put("blur", num(lengthPx(lengths[2], where)))
            put("spread", num(lengthPx(spread, where)))
            put("color", JsonPrimitive(normalise(JsonPrimitive(colorPart), where)))
        }
    }

    /** A CSS font stack as an ordered list of family names (quotes removed). */
    public fun parseFontStack(stack: String): List<String> =
        stack.split(",").map { QUOTED_RE.replace(it.trim(), "$2") }.filter { it.isNotEmpty() }
}
