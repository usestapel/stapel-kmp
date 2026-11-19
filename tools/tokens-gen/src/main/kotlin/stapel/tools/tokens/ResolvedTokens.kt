package stapel.tools.tokens

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A colour role resolved for both modes: `#rrggbb[aa]` literals and the ramp refs they came from. */
public class ResolvedColor(
    /** Light-mode literal. */
    public val light: String,
    /** Dark-mode literal. */
    public val dark: String,
    /** Light-mode `<ramp>.<step>`. */
    public val lightRef: String,
    /** Dark-mode `<ramp>.<step>`. */
    public val darkRef: String,
)

/** One parsed shadow (px lengths, `#rrggbb[aa]` colour). */
public class ResolvedShadow(
    /** Horizontal offset. */
    public val x: Double,
    /** Vertical offset. */
    public val y: Double,
    /** Blur radius. */
    public val blur: Double,
    /** Spread. */
    public val spread: Double,
    /** Colour literal. */
    public val color: String,
)

/** A font size step (px) with its line height (px). */
public class ResolvedTypeStep(
    /** Font size. */
    public val fontSize: Double,
    /** Line height. */
    public val lineHeight: Double,
)

/**
 * `stapel.tokens.resolved/1` with a typed view over it. [json] is the canonical document
 * ([TokenResolver.toResolvedJson] writes it); the properties are what an emitter reads.
 */
public class ResolvedTokens private constructor(
    /** The resolved document. */
    public val json: JsonObject,
) {
    private val source = json.getValue("source").jsonObject
    private val scales = json.getValue("scales").jsonObject

    /** `<package>@<version>+<first 8 hex of themeSha256>`. */
    public val sourceId: String =
        "${source.str("package")}@${source.str("version")}+${source.str("themeSha256").take(8)}"

    /** Every role by name. */
    public val colors: Map<String, ResolvedColor> = json.getValue("colors").jsonObject.mapValues { (_, v) ->
        val o = v.jsonObject
        ResolvedColor(o.str("light"), o.str("dark"), o.str("lightRef"), o.str("darkRef"))
    }

    /** Canon roles in dictionary order, then host roles sorted. */
    public val roleOrder: List<String> = json.getValue("roleOrder").jsonArray.map { it.jsonPrimitive.content }

    /** Elevation steps: `(light, dark)`, null = no shadow. */
    public val elevation: Map<String, Pair<ResolvedShadow?, ResolvedShadow?>> =
        json.getValue("elevation").jsonObject.mapValues { (_, v) -> shadow(v.jsonObject["light"]) to shadow(v.jsonObject["dark"]) }

    /** Font stacks, family names in order. */
    public val fontFamily: Map<String, List<String>> =
        scales.getValue("fontFamily").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }

    /** Type scale. */
    public val fontSize: Map<String, ResolvedTypeStep> = scales.getValue("fontSize").jsonObject.mapValues { (_, v) ->
        ResolvedTypeStep(v.jsonObject.getValue("fontSize").jsonPrimitive.double, v.jsonObject.getValue("lineHeight").jsonPrimitive.double)
    }

    /** Font weights. */
    public val fontWeight: Map<String, Int> = scales.getValue("fontWeight").jsonObject.mapValues { it.value.jsonPrimitive.int }

    /** Spacing steps (px). */
    public val spacing: Map<String, Double> = numbers(scales["spacing"])

    /** Radii (px). */
    public val radii: Map<String, Double> = numbers(scales["radii"])

    /** Control heights (px). */
    public val controls: Map<String, Double> = numbers(scales["controls"])

    /** Breakpoints (px, min width). */
    public val breakpoints: Map<String, Double> = numbers(scales["breakpoints"])

    /** Responsive roles: breakpoint → px. */
    public val responsive: Map<String, Map<String, Double>> =
        json.getValue("responsive").jsonObject.mapValues { numbers(it.value) }

    /** The contrast pairs the theme was gated with. */
    public val contrastPairs: List<Triple<String, String, String>> =
        json.getValue("contrast").jsonObject.getValue("pairs").jsonArray.map { p ->
            val a = p.jsonArray.map { it.jsonPrimitive.content }
            Triple(a[0], a[1], a[2])
        }

    /** Companion: parsing. */
    public companion object {
        /** Reads a `stapel.tokens.resolved/1` document (refuses another schema). */
        public fun fromResolvedJson(json: JsonObject): ResolvedTokens {
            val schema = (json["schema"] as? JsonPrimitive)?.content
            require(schema == TokenResolver.RESOLVED_SCHEMA) { "not a ${TokenResolver.RESOLVED_SCHEMA} document (schema: $schema)" }
            return ResolvedTokens(json)
        }

        private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

        private fun numbers(e: JsonElement?): Map<String, Double> =
            (e as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.double }

        private fun shadow(e: JsonElement?): ResolvedShadow? {
            val o = e as? JsonObject ?: return null
            fun d(k: String) = o.getValue(k).jsonPrimitive.double
            return ResolvedShadow(d("x"), d("y"), d("blur"), d("spread"), o.str("color"))
        }
    }
}
