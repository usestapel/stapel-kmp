package stapel.i18n

/** The direction a locale's script is written in; a UI lays out in the same direction. */
public enum class WritingDirection {
    /** Left to right (Latin, Cyrillic, Greek, CJK, …). */
    Ltr,

    /** Right to left (Arabic, Hebrew, Thaana, Syriac, N'Ko, Adlam, Hanifi Rohingya). */
    Rtl,
}

/** ISO 15924 scripts written right to left. */
private val RTL_SCRIPTS = setOf("arab", "hebr", "thaa", "syrc", "nkoo", "adlm", "rohg", "mand", "samr")

/** Languages whose default script is right to left (CLDR likely subtags). */
private val RTL_LANGUAGES = setOf(
    "ar", "arc", "ckb", "dv", "fa", "he", "iw", "ks", "lrc", "mzn", "nqo", "pnb", "ps", "sd", "syr", "ug", "ur", "yi",
)

/**
 * The writing direction of [locale] (a BCP 47 tag, `_` accepted): an explicit script subtag decides
 * (`az-Arab` → [WritingDirection.Rtl], `ku-Latn` → [WritingDirection.Ltr]); otherwise the language's
 * default script does (`ar`, `he`, `fa`, `ur` … → [WritingDirection.Rtl]). Anything unknown or empty is
 * [WritingDirection.Ltr].
 */
public fun writingDirectionOf(locale: String): WritingDirection {
    val parts = locale.replace('_', '-').split('-').filter { it.isNotEmpty() }.map { it.lowercase() }
    val language = parts.firstOrNull() ?: return WritingDirection.Ltr
    val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }
    val rtl = if (script != null) script in RTL_SCRIPTS else language in RTL_LANGUAGES
    return if (rtl) WritingDirection.Rtl else WritingDirection.Ltr
}
