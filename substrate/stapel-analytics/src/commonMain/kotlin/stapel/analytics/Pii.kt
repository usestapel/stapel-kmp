package stapel.analytics

/** What [PiiGuard.Strip] puts in place of a PII-like value (the web's marker). */
public const val PII_REDACTED: String = "[redacted]"

/**
 * True when [value] looks like an e-mail address or a phone number — the same verdict as
 * `looksLikePii` of `@stapel/analytics` on every case of the shared `pii-vectors.json`.
 *
 * The web's regular expressions are spelled out with JavaScript's character classes (its `\s` and
 * `trim` whitespace set, ASCII `\d`), so no platform regex dialect changes a verdict.
 */
public fun looksLikePii(value: String): Boolean {
    if (EMAIL.containsMatchIn(value)) return true
    val trimmed = value.trimJs()
    if (!PHONE_SHAPE.matches(trimmed)) return false
    return trimmed.count { it in '0'..'9' } >= 7
}

/** JavaScript's `\s` (WhiteSpace + LineTerminator). */
private const val JS_SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

private val EMAIL = Regex("[^${JS_SPACE}@]+@[^${JS_SPACE}@]+\\.[a-zA-Z]{2,}")
private val PHONE_SHAPE = Regex("\\+?[0-9${JS_SPACE}\\-().]{7,}")

private fun Char.isJsSpace(): Boolean = this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' ||
    this == '\r' || this == ' ' || this == ' ' || this == ' ' || this in ' '..' ' ||
    this == ' ' || this == ' ' || this == ' ' || this == ' ' || this == '　' || this == '﻿'

private fun String.trimJs(): String {
    var start = 0
    var end = length
    while (start < end && this[start].isJsSpace()) start++
    while (end > start && this[end - 1].isJsSpace()) end--
    return substring(start, end)
}

/** Outcome of guarding one event's properties. */
internal sealed interface PiiOutcome {
    /** Deliver with [props]; [hit] when a value looked like PII. */
    data class Keep(val props: Map<String, String>, val hit: Boolean) : PiiOutcome

    /** Drop the event ([PiiGuard.Reject]). */
    data object Reject : PiiOutcome
}

/** Applies [mode] to [props]. Only values are judged, never keys (as on the web). */
internal fun guardPii(props: Map<String, String>, mode: PiiGuard): PiiOutcome {
    if (mode == PiiGuard.Off || props.isEmpty()) return PiiOutcome.Keep(props, false)
    val hits = props.filterValues(::looksLikePii).keys
    if (hits.isEmpty()) return PiiOutcome.Keep(props, false)
    return when (mode) {
        PiiGuard.Reject -> PiiOutcome.Reject
        PiiGuard.Strip -> PiiOutcome.Keep(props.mapValues { (k, v) -> if (k in hits) PII_REDACTED else v }, true)
        PiiGuard.Warn, PiiGuard.Off -> PiiOutcome.Keep(props, true)
    }
}
