package stapel.tools.tokens

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One finding of [TokenResolver.validate] or a `resolved@1` refusal. */
public class ThemeProblem(
    /** Stable short id, the same for every implementation (e.g. `contrast`, `unknown-step`). */
    public val code: String,
    /** The teaching message, verbatim the Node bin's text (without the `✖`/`⚠` prefix). */
    public val message: String,
    /** [Severity.Error] fails the build; [Severity.Warning] is reported only. */
    public val severity: Severity,
) {
    /** How a problem affects the build. */
    public enum class Severity {
        /** Fails generation. */
        Error,

        /** Reported, does not fail (a documented contrast exception). */
        Warning,
    }

    override fun toString(): String = "${if (severity == Severity.Error) "✖" else "⚠"} [$code] $message"
}

/** A theme the `resolved@1` target cannot express (a CSS-only colour or shadow form). */
public class ThemeException(
    /** The refusal. */
    public val problem: ThemeProblem,
) : IllegalArgumentException(problem.message)

/** Package metadata stamped into `source` of `tokens.resolved.json`. */
public class TokenSource(
    /** npm package name of the canonical tokens (`@stapel/tokens`). */
    public val packageName: String,
    /** Its version at the pinned ref. */
    public val version: String,
)

/**
 * The Kotlin port of `@stapel/tokens`' `src/gen/lib.mjs` + `contrast.mjs`: merge, validate (with the
 * contrast gate) and resolve. Held equal to the Node implementation by the conformance corpus in
 * `tokens-source/conformance` (same messages, byte-equal `tokens.resolved.json`).
 */
public object TokenResolver {
    /** `stapel.tokens.resolved/1`. */
    public const val RESOLVED_SCHEMA: String = "stapel.tokens.resolved/1"

    /** The §68 dictionary in reading order; host roles follow, sorted. */
    public val CANON_ROLE_ORDER: List<String> = listOf(
        "surface", "surface-raised", "surface-sunken", "surface-overlay",
        "text", "text-muted", "text-subtle", "text-on-accent",
        "border", "border-subtle", "focus-ring",
        "brand", "brand-hover", "brand-active", "brand-subtle",
        "link", "link-hover",
        "success", "success-bg", "success-border", "success-on",
        "warning", "warning-bg", "warning-border", "warning-on",
        "error", "error-bg", "error-border", "error-on",
        "info", "info-bg", "info-border", "info-on",
    )

    /** WCAG thresholds per pair kind (`text` 4.5, `ui` and `decorative` 3). */
    public val THRESHOLDS: Map<String, Double> = mapOf("text" to 4.5, "ui" to 3.0, "decorative" to 3.0)

    private val RAMP_STEP_RE = Regex("^[a-z][a-z0-9]*\\.[a-z0-9]+$", RegexOption.IGNORE_CASE)
    private val COLORISH_RE = Regex("#|rgba?\\(|hsla?\\(", RegexOption.IGNORE_CASE)

    /** `mergeRamps`: host ramps replace standard ramps by name; names sorted, `_`-keys dropped. */
    public fun mergeRamps(standardRamps: JsonObject, themeRamps: JsonObject): JsonObject {
        val spread = LinkedHashMap<String, JsonElement>()
        JsJson.entries(standardRamps).forEach { (k, v) -> spread[k] = v }
        JsJson.entries(themeRamps).forEach { (k, v) -> spread[k] = v }
        return buildJsonObject {
            spread.entries.sortedBy { it.key }.filterNot { it.key.startsWith("_") }.forEach { (name, steps) ->
                put(name, if (steps is JsonObject) JsonObject(LinkedHashMap(steps)) else JsonObject(emptyMap()))
            }
        }
    }

    /** Host theme deep-merged over the default; host wins per leaf; arrays and scalars replace (§68 merge contract). */
    public fun merge(defaultTheme: JsonObject, hostTheme: JsonObject?): JsonObject {
        if (hostTheme == null || hostTheme.isEmpty()) return defaultTheme
        val out = LinkedHashMap<String, JsonElement>(defaultTheme)
        for ((key, value) in JsJson.entries(hostTheme)) {
            if (key == "ramps") {
                out["ramps"] = mergeRamps(defaultTheme["ramps"] as? JsonObject ?: JsonObject(emptyMap()), value as? JsonObject ?: JsonObject(emptyMap()))
                continue
            }
            out[key] = deepMergeLeaves(defaultTheme[key], value)
        }
        return JsonObject(out)
    }

    private fun deepMergeLeaves(base: JsonElement?, override: JsonElement): JsonElement {
        if (base !is JsonObject || override !is JsonObject) return override
        val out = LinkedHashMap<String, JsonElement>(base)
        for ((k, v) in JsJson.entries(override)) out[k] = deepMergeLeaves(base[k], v)
        return JsonObject(out)
    }

    private fun obj(e: JsonElement?): JsonObject? = e as? JsonObject

    private fun resolveRef(ramps: JsonObject, ref: JsonElement?): JsonElement? {
        if (ref !is JsonPrimitive || !ref.isString) return null
        val s = ref.content
        val dot = s.indexOf('.')
        if (dot == -1) return null
        return obj(ramps[s.substring(0, dot)])?.get(s.substring(dot + 1))
    }

    private fun isEmptyRef(ref: JsonElement?) = ref == null || ref is JsonNull || (ref is JsonPrimitive && ref.isString && ref.content.isEmpty())

    /**
     * `validateTheme` + `checkContrastPairs`. Errors fail the build; a failing contrast pair with a
     * documented `contrastExceptions` entry is a warning.
     *
     * @param thresholds WCAG thresholds per pair kind; only a test passes anything but [THRESHOLDS].
     */
    public fun validate(
        theme: JsonObject,
        ramps: JsonObject,
        thresholds: Map<String, Double> = THRESHOLDS,
    ): List<ThemeProblem> {
        val errors = mutableListOf<ThemeProblem>()
        val warnings = mutableListOf<ThemeProblem>()
        fun error(code: String, message: String) = errors.add(ThemeProblem(code, message, ThemeProblem.Severity.Error))
        val core = obj(theme["core"]) ?: JsonObject(emptyMap())

        for ((name, def) in JsJson.sortedEntries(core)) {
            if (name.startsWith("_")) continue
            for (mode in listOf("light", "dark")) {
                val ref = obj(def)?.get(mode)
                if (isEmptyRef(ref)) {
                    error("missing-mode", "tokens: role \"$name\" — missing value for \"$mode\" (a role must be a {light,dark} pair)")
                    continue
                }
                val refText = JsJson.jsString(ref)
                if (COLORISH_RE.containsMatchIn(refText)) {
                    error(
                        "raw-color",
                        "tokens: role \"$name\".$mode → \"$refText\" — refs must be <ramp>.<step>; raw hex is forbidden in " +
                            "the core section (add a step to the ramp instead)",
                    )
                    continue
                }
                if (!RAMP_STEP_RE.matches(refText)) {
                    error("bad-ref", "tokens: role \"$name\".$mode → \"$refText\" — expected a <ramp>.<step> reference")
                    continue
                }
                val dot = refText.indexOf('.')
                val ramp = refText.substring(0, dot)
                val step = refText.substring(dot + 1)
                val rampObj = obj(ramps[ramp])
                if (ramps[ramp] == null || !JsJson.truthy(ramps[ramp])) {
                    error("unknown-ramp", "tokens: role \"$name\".$mode → \"$refText\" — no such ramp \"$ramp\"")
                    continue
                }
                if (rampObj?.get(step) == null) {
                    val available = rampObj?.let { JsJson.keys(it).joinToString(", ") }.orEmpty()
                    error(
                        "unknown-step",
                        "tokens: role \"$name\".$mode → \"$refText\" — no such step in ramp \"$ramp\" (available: $available)",
                    )
                }
            }
        }

        val resolvedForContrast = LinkedHashMap<String, Pair<JsonElement?, JsonElement?>>()
        for ((name, def) in JsJson.sortedEntries(core)) {
            if (name.startsWith("_")) continue
            resolvedForContrast[name] = resolveRef(ramps, obj(def)?.get("light")) to resolveRef(ramps, obj(def)?.get("dark"))
        }

        val exceptions = theme["contrastExceptions"] as? JsonArray ?: JsonArray(emptyList())
        val exceptionByKey = LinkedHashMap<String, String>()
        for (exc in exceptions) {
            val o = obj(exc)
            val fg = o?.get("fg")
            val bg = o?.get("bg")
            val mode = o?.get("mode")
            if (!JsJson.truthy(fg) || !JsJson.truthy(bg) || !JsJson.truthy(mode)) {
                error("exception-malformed", "tokens: contrastExceptions entry ${JsJson.stringify(exc)} — requires \"fg\", \"bg\", \"mode\"")
                continue
            }
            val key = "${JsJson.jsString(fg)}:${JsJson.jsString(bg)}:${JsJson.jsString(mode)}"
            val reason = o!!["reason"]
            if (reason !is JsonPrimitive || !reason.isString || reason.content.trim().isEmpty()) {
                error(
                    "exception-no-reason",
                    "tokens: contrastExceptions \"$key\" — requires a non-empty \"reason\" field (a documented exception, not a silent one)",
                )
                continue
            }
            exceptionByKey[key] = reason.content
        }

        val used = mutableSetOf<String>()
        for (failure in Contrast.check(resolvedForContrast, thresholds)) {
            val reason = exceptionByKey[failure.key]
            if (reason != null) {
                used.add(failure.key)
                warnings.add(ThemeProblem("contrast-exception", "${failure.message} — documented exception: $reason", ThemeProblem.Severity.Warning))
            } else {
                error("contrast", failure.message)
            }
        }
        for ((key, reason) in exceptionByKey) {
            if (key !in used) {
                error(
                    "stale-exception",
                    "tokens: contrastExceptions \"$key\" (reason: \"$reason\") — this pair now passes contrast; the exception is stale (remove it)",
                )
            }
        }

        val scales = obj(theme["scales"]) ?: JsonObject(emptyMap())
        val breakpoints = obj(scales["breakpoints"])
        val spacing = obj(scales["spacing"])
        for ((role, byBreakpoint) in JsJson.sortedEntries(obj(scales["responsive"]))) {
            if (role.startsWith("_")) continue
            for ((breakpoint, step) in JsJson.sortedEntries(obj(byBreakpoint))) {
                if (breakpoints?.get(breakpoint) == null) {
                    error(
                        "unknown-breakpoint",
                        "tokens: responsive role \"$role\" names breakpoint \"$breakpoint\", which scales.breakpoints does not define",
                    )
                }
                if (spacing?.get(JsJson.jsString(step)) == null) {
                    error(
                        "non-spacing-step",
                        "tokens: responsive role \"$role\".$breakpoint → \"${JsJson.jsString(step)}\" — a responsive role's value is a " +
                            "SPACING STEP name, so the role stays on the scale a theme retunes in one place",
                    )
                }
            }
        }
        return errors + warnings
    }

    /**
     * `resolveTheme` + `renderResolvedJson`: the theme resolved into `stapel.tokens.resolved/1`.
     * Call [validate] first; this throws [ThemeException] only for forms `resolved@1` cannot express.
     */
    public fun resolve(theme: JsonObject, ramps: JsonObject, source: TokenSource): ResolvedTokens {
        val core = obj(theme["core"]) ?: JsonObject(emptyMap())
        val roles = JsJson.sortedEntries(core).filterNot { it.first.startsWith("_") }
        val colors = buildJsonObject {
            for ((name, def) in roles) {
                val d = obj(def)
                put(name, buildJsonObject {
                    put("light", ColorText.normalise(resolveRef(ramps, d?.get("light")), "role \"$name\".light"))
                    put("dark", ColorText.normalise(resolveRef(ramps, d?.get("dark")), "role \"$name\".dark"))
                    d?.get("light")?.let { put("lightRef", it) }
                    d?.get("dark")?.let { put("darkRef", it) }
                })
            }
        }
        val roleNames = roles.map { it.first }
        val roleOrder = CANON_ROLE_ORDER.filter { it in roleNames } + roleNames.filter { it !in CANON_ROLE_ORDER }.sorted()
        val elevation = buildJsonObject {
            for ((name, def) in JsJson.sortedEntries(obj(theme["elevation"]))) {
                if (name.startsWith("_")) continue
                put(name, buildJsonObject {
                    put("light", ColorText.parseShadow(obj(def)?.get("light"), "elevation \"$name\".light"))
                    put("dark", ColorText.parseShadow(obj(def)?.get("dark"), "elevation \"$name\".dark"))
                })
            }
        }
        val scales = obj(theme["scales"]) ?: JsonObject(emptyMap())
        fun plain(e: JsonElement?) = buildJsonObject {
            JsJson.sortedEntries(obj(e)).filterNot { it.first.startsWith("_") }.forEach { (k, v) -> put(k, v) }
        }
        val spacing = obj(scales["spacing"])
        val responsive = buildJsonObject {
            for ((role, byBreakpoint) in JsJson.sortedEntries(obj(scales["responsive"]))) {
                if (role.startsWith("_")) continue
                put(role, buildJsonObject {
                    for ((bp, step) in JsJson.sortedEntries(obj(byBreakpoint))) {
                        spacing?.get(JsJson.jsString(step))?.let { put(bp, it) }
                    }
                })
            }
        }
        val exceptions = (theme["contrastExceptions"] as? JsonArray).orEmpty().map { exc ->
            buildJsonObject { listOf("fg", "bg", "mode", "reason").forEach { k -> obj(exc)?.get(k)?.let { put(k, it) } } }
        }
        val json = buildJsonObject {
            put("schema", RESOLVED_SCHEMA)
            put("source", buildJsonObject {
                put("package", source.packageName)
                put("version", source.version)
                put("themeSha256", JsJson.sha256(theme))
                put("rampsSha256", JsJson.sha256(ramps))
            })
            put("colors", colors)
            put("roleOrder", buildJsonArray { roleOrder.forEach { add(JsonPrimitive(it)) } })
            put("elevation", elevation)
            put("scales", buildJsonObject {
                put("fontFamily", buildJsonObject {
                    JsJson.sortedEntries(obj(scales["fontFamily"])).forEach { (k, v) ->
                        put(k, buildJsonArray { ColorText.parseFontStack(JsJson.jsString(v)).forEach { add(JsonPrimitive(it)) } })
                    }
                })
                listOf("fontSize", "fontWeight", "spacing", "radii", "controls", "breakpoints").forEach { put(it, plain(scales[it])) }
            })
            put("responsive", responsive)
            put("contrast", buildJsonObject {
                put("pairs", buildJsonArray {
                    Contrast.PAIRS.forEach { p -> add(buildJsonArray { p.toList().forEach { add(JsonPrimitive(it)) } }) }
                })
                put("exceptions", JsonArray(exceptions))
            })
        }
        return ResolvedTokens.fromResolvedJson(json)
    }

    /** Canonical `tokens.resolved.json` text: `JSON.stringify(…, null, 2)` + newline, byte-comparable with the golden. */
    public fun toResolvedJson(resolved: ResolvedTokens): String = JsJson.stringify(resolved.json, 2) + "\n"
}
