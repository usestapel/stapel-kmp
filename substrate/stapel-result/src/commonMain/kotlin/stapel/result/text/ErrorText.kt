package stapel.result.text

import stapel.result.CoreErrorCodes
import stapel.result.ErrorCode
import stapel.result.Remediation
import stapel.result.StapelError
import kotlin.jvm.JvmInline

/**
 * A BCP 47 locale tag (`ru-RU`, `en`).
 *
 * @property value the tag text.
 */
@JvmInline
public value class LocaleTag(public val value: String) {
    /** The primary language subtag, lower-cased (`ru` for `ru-RU`). */
    public val language: String get() = value.substringBefore('-').substringBefore('_').lowercase()

    /** The tag text. */
    override fun toString(): String = value
}

/**
 * Display text for error codes, one per owner: one generated per brick, one for the backend core's
 * own codes, and [ClientErrorCatalog] for the codes the client synthesizes.
 */
public interface ErrorCatalog {
    /** Owner key, e.g. `auth`; `core` for the backend core's codes, `client` for [ClientErrorCatalog]. */
    public val owner: String

    /** The contract entry for [code], or null when this catalog does not own it. */
    public fun spec(code: ErrorCode): ErrorSpec?

    /**
     * The template for [code] in exactly [locale] (no fallback: the resolver walks the tag, its
     * language, then `en`), or null.
     */
    public fun template(code: ErrorCode, locale: LocaleTag): String?
}

/**
 * One entry of an `errors.json` contract.
 *
 * @property code the error code.
 * @property status its HTTP status.
 * @property params the names of its `{param}` placeholders.
 * @property remediation the declared (or heuristic) remediation.
 * @property en the canonical English template.
 */
public data class ErrorSpec(
    public val code: ErrorCode,
    public val status: Int,
    public val params: List<String>,
    public val remediation: Remediation,
    public val en: String,
)

/**
 * What an error surface shows.
 *
 * @property message the sentence a person reads; complete on its own and never carries a protocol
 *   number.
 * @property detail a secondary technical line (`HTTP 503`, plus the request id when known) for
 *   generic sentences only; null when there is nothing worth quoting.
 */
public data class ErrorText(public val message: String, public val detail: String?)

private val PLACEHOLDER = Regex("""\{([\w.]+)\}""")

/** Fills `{name}` placeholders from [params]; an unknown placeholder is left as written. */
public fun interpolate(template: String, params: Map<String, String>): String =
    PLACEHOLDER.replace(template) { match -> params[match.groupValues[1]] ?: match.value }

/**
 * Turns any [StapelError] into display text, following the web's ladder exactly
 * (`describeFlowError`):
 *
 * 1. the catalog template for the exact code, `{param}`s filled (`{status}` added, a backend
 *    param of the same name wins); 1b. for client-synthesized `stapel.http.<status>` only, the
 *    class entry `stapel.http.Nxx` — a backend code is never widened;
 * 2. the server's own text, only when its language equals the display language;
 * 3. for `error.<status>.*` codes nobody translated, the status floor `stapel.http.<status>`, then
 *    `stapel.http.Nxx`;
 * 4. the raw code.
 *
 * Templates are looked up for the display tag, then its language, then `en`; within one locale
 * step, [catalogs] are consulted in order and [floor] last, so any catalog overrides the floor.
 *
 * The detail line is produced only for client-synthesized codes or a status-floor hit, and only
 * when a response status exists.
 *
 * Deliberate difference from the web: step 2 compares languages (`ru` = `ru-RU`), because a
 * device locale carries a region the backend's `error_language` never does.
 */
public class ErrorTextResolver(
    catalogs: List<ErrorCatalog>,
    floor: ErrorCatalog = ClientErrorCatalog,
) {
    private val chain: List<ErrorCatalog> = catalogs + floor

    /** The sentence and the detail line for [error] in [locale]. */
    public fun describe(error: StapelError, locale: LocaleTag): ErrorText {
        val status = error.status
        val requestId = error.envelope?.requestId
        val params = buildMap {
            if (status != null) put("status", status.toString())
            if (requestId != null) put("request_id", requestId)
            putAll(error.params.asMap())
        }
        val code = error.code
        var fromFloor = false
        val message = run {
            for (key in keyCandidates(code)) {
                template(key, locale)?.let { return@run interpolate(it, params) }
            }
            val envelope = error.envelope
            val serverText = envelope?.serverText
            val serverLanguage = envelope?.serverLanguage
            if (serverText != null && serverLanguage != null && LocaleTag(serverLanguage).language == locale.language) {
                return@run serverText
            }
            for (key in statusFloorKeys(code)) {
                template(key, locale)?.let {
                    fromFloor = true
                    return@run interpolate(it, params)
                }
            }
            code.value
        }
        val quotable = status != null && status > 0 && (code.isClientSynthesized || fromFloor)
        val detail = if (!quotable) {
            null
        } else if (requestId != null) {
            interpolate(template(CoreErrorCodes.DetailWithRequestId, locale) ?: DETAIL_WITH_REQUEST_FALLBACK, params)
        } else {
            interpolate(template(CoreErrorCodes.Detail, locale) ?: DETAIL_FALLBACK, params)
        }
        return ErrorText(message, detail)
    }

    /** Just the sentence of [describe]. */
    public fun message(error: StapelError, locale: LocaleTag): String = describe(error, locale).message

    /**
     * The remediation of [error]: its own, unless that is [Remediation.Unknown] and a catalog
     * declares one for the code.
     */
    public fun remediation(error: StapelError): Remediation =
        if (error.remediation != Remediation.Unknown) error.remediation else spec(error.code)?.remediation ?: Remediation.Unknown

    /** The first catalog's contract entry for [code]. */
    public fun spec(code: ErrorCode): ErrorSpec? = chain.firstNotNullOfOrNull { it.spec(code) }

    /** The template for [code] in [locale] (tag, then language, then `en`), or null. */
    public fun template(code: ErrorCode, locale: LocaleTag): String? {
        for (step in localeSteps(locale)) {
            chain.firstNotNullOfOrNull { it.template(code, step) }?.let { return it }
        }
        return null
    }

    private companion object {
        const val DETAIL_FALLBACK = "HTTP {status}"
        const val DETAIL_WITH_REQUEST_FALLBACK = "HTTP {status} · {request_id}"
        val HTTP_CODE = Regex("""^stapel\.http\.(\d)\d{2}$""")
        val BACKEND_STATUS_CODE = Regex("""^error\.(\d)(\d{2})(?:\.|$)""")

        fun localeSteps(locale: LocaleTag): List<LocaleTag> =
            listOf(locale, LocaleTag(locale.language), LocaleTag("en")).distinctBy { it.value.lowercase() }

        fun keyCandidates(code: ErrorCode): List<ErrorCode> {
            val match = HTTP_CODE.find(code.value) ?: return listOf(code)
            return listOf(code, ErrorCode("stapel.http.${match.groupValues[1]}xx"))
        }

        fun statusFloorKeys(code: ErrorCode): List<ErrorCode> {
            val match = BACKEND_STATUS_CODE.find(code.value) ?: return emptyList()
            val (cls, rest) = match.destructured
            return listOf(ErrorCode("stapel.http.$cls$rest"), ErrorCode("stapel.http.${cls}xx"))
        }
    }
}
