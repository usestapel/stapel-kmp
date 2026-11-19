package stapel.tools.tokens

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.pow

/** The WCAG contrast arithmetic and the pair contract of `@stapel/tokens`' `contrast.mjs`. */
public object Contrast {
    /** Fills body text renders over. */
    public val TEXT_FILL_ROLES: List<String> = listOf(
        "surface", "surface-raised", "surface-sunken", "surface-overlay",
        "brand-subtle", "success-bg", "warning-bg", "error-bg", "info-bg",
    )

    /** Surfaces component chrome is drawn on. */
    public val UI_FILL_ROLES: List<String> = listOf("surface", "surface-raised", "surface-sunken", "surface-overlay")

    /** The neutral text family (4.5:1 on every text fill). */
    public val TEXT_ROLES: List<String> = listOf("text", "text-muted", "text-subtle", "link", "link-hover")

    /** Chrome that must be perceivable (3:1). */
    public val UI_ROLES: List<String> = listOf("focus-ring")

    /** Decorative chrome (3:1, exceptions allowed with a reason). */
    public val DECORATIVE_ROLES: List<String> = listOf("border", "border-subtle")

    /** `CONTRAST_PAIRS`: `(fg, bg, kind)` in the Node order. */
    public val PAIRS: List<Triple<String, String, String>> =
        TEXT_ROLES.flatMap { fg -> TEXT_FILL_ROLES.map { Triple(fg, it, "text") } } +
            UI_ROLES.flatMap { fg -> UI_FILL_ROLES.map { Triple(fg, it, "ui") } } +
            DECORATIVE_ROLES.flatMap { fg -> UI_FILL_ROLES.map { Triple(fg, it, "decorative") } } +
            listOf(
                Triple("success", "success-bg", "text"),
                Triple("error", "error-bg", "text"),
                Triple("warning", "warning-bg", "text"),
                Triple("info", "info-bg", "text"),
                Triple("text-on-accent", "brand", "text"),
                Triple("success-on", "success", "text"),
                Triple("warning-on", "warning", "text"),
                Triple("error-on", "error", "text"),
                Triple("info-on", "info", "text"),
            )

    private val HEX_RE = Regex("^#([0-9a-f]{3}|[0-9a-f]{6})$", RegexOption.IGNORE_CASE)

    /** `[r, g, b]` of `#rgb`/`#rrggbb`; null for anything else (skipped, not failed). */
    public fun hexToRgb(hex: String?): IntArray? {
        if (hex == null || !HEX_RE.matches(hex)) return null
        var h = hex.substring(1)
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        val n = h.toInt(16)
        return intArrayOf((n shr 16) and 255, (n shr 8) and 255, n and 255)
    }

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance (0..1). */
    public fun relativeLuminance(rgb: IntArray): Double =
        0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])

    /** WCAG contrast ratio (1..21), or null when a side is not a plain hex literal. */
    public fun ratio(a: String?, b: String?): Double? {
        val ra = hexToRgb(a) ?: return null
        val rb = hexToRgb(b) ?: return null
        val la = relativeLuminance(ra)
        val lb = relativeLuminance(rb)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** One failing pair in one mode. */
    public class Failure(
        /** Foreground role. */
        public val fg: String,
        /** Background role. */
        public val bg: String,
        /** `light` or `dark`. */
        public val mode: String,
        /** `text`, `ui` or `decorative`. */
        public val kind: String,
        /** The measured ratio. */
        public val ratio: Double,
        /** The threshold it fell below. */
        public val threshold: Double,
    ) {
        /** `fg:bg:mode`, the `contrastExceptions` lookup key. */
        public val key: String get() = "$fg:$bg:$mode"

        /** The Node message, verbatim. */
        public val message: String
            get() = "contrast: $fg on $bg ($mode) = ${JsJson.toFixed(ratio, 1)}:1 < ${JsJson.formatNumber(threshold)} " +
                if (kind == "decorative") "(WCAG 1.4.11, decorative)" else "(WCAG AA)"
    }

    /** `checkContrastPairs` over resolved `(light, dark)` raw values per role. */
    public fun check(
        resolved: Map<String, Pair<JsonElement?, JsonElement?>>,
        thresholds: Map<String, Double> = TokenResolver.THRESHOLDS,
    ): List<Failure> {
        fun str(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
        val out = mutableListOf<Failure>()
        for ((fg, bg, kind) in PAIRS) {
            val f = resolved[fg] ?: continue
            val b = resolved[bg] ?: continue
            val threshold = thresholds.getValue(kind)
            for ((mode, pick) in listOf<Pair<String, (Pair<JsonElement?, JsonElement?>) -> JsonElement?>>("light" to { it.first }, "dark" to { it.second })) {
                val r = ratio(str(pick(f)), str(pick(b))) ?: continue
                if (r < threshold) out.add(Failure(fg, bg, mode, kind, r, threshold))
            }
        }
        return out
    }
}
