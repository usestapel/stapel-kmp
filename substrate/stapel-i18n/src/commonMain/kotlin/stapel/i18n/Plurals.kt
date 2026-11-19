package stapel.i18n

import kotlin.math.abs

/** CLDR plural categories. */
public enum class PluralCategory(
    /** The suffix of a plural family key (`auth.otp.resend_in.few`). */
    public val suffix: String,
) {
    /** `zero`. */
    Zero("zero"),

    /** `one`. */
    One("one"),

    /** `two`. */
    Two("two"),

    /** `few`. */
    Few("few"),

    /** `many`. */
    Many("many"),

    /** `other` (every language has it; the fallback). */
    Other("other"),
}

/** A language's cardinal plural rule for integer counts. */
public fun interface PluralRule {
    /** The category of [count]. */
    public fun category(count: Long): PluralCategory
}

/**
 * Built-in CLDR cardinal rules for integers (en, ru, es) and a registry for more. An unknown
 * language gets a rule that always answers [PluralCategory.Other].
 */
public object PluralRules {
    /** English: `one` for 1, `other` otherwise. */
    public val English: PluralRule = PluralRule { if (abs(it) == 1L) PluralCategory.One else PluralCategory.Other }

    /** Russian: `one` (1, 21, 101…), `few` (2–4, 22–24…), `many` (0, 5–20, 25–30…). */
    public val Russian: PluralRule = PluralRule { count ->
        val n = abs(count)
        val mod10 = n % 10
        val mod100 = n % 100
        when {
            mod10 == 1L && mod100 != 11L -> PluralCategory.One
            mod10 in 2L..4L && mod100 !in 12L..14L -> PluralCategory.Few
            else -> PluralCategory.Many
        }
    }

    /** Spanish: `one` for 1, `many` for non-zero multiples of a million, `other` otherwise. */
    public val Spanish: PluralRule = PluralRule { count ->
        val n = abs(count)
        when {
            n == 1L -> PluralCategory.One
            n != 0L && n % 1_000_000L == 0L -> PluralCategory.Many
            else -> PluralCategory.Other
        }
    }

    private val Fallback = PluralRule { PluralCategory.Other }

    private val rules = mutableMapOf("en" to English, "ru" to Russian, "es" to Spanish)

    /** The categories a complete plural family needs in [language] (`other` always included). */
    public fun requiredCategories(language: String): Set<PluralCategory> = when (languageOf(language)) {
        "ru" -> setOf(PluralCategory.One, PluralCategory.Few, PluralCategory.Many, PluralCategory.Other)
        "es" -> setOf(PluralCategory.One, PluralCategory.Many, PluralCategory.Other)
        "en" -> setOf(PluralCategory.One, PluralCategory.Other)
        else -> setOf(PluralCategory.Other)
    }

    /** The rule for [language] (a tag such as `ru-RU` is reduced to its language). */
    public fun forLanguage(language: String): PluralRule = rules[languageOf(language)] ?: Fallback

    /** Adds or replaces the rule of [language]. */
    public fun register(language: String, rule: PluralRule) {
        rules[languageOf(language)] = rule
    }

    internal fun languageOf(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()
}
