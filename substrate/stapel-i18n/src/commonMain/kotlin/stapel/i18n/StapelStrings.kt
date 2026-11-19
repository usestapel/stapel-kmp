package stapel.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.jvm.JvmInline

/** A flat dictionary: key → template (`{param}` placeholders, plural families as `key.one`, `key.other`…). */
public typealias I18nDictionary = Map<String, String>

/**
 * A typed string key.
 *
 * @property key the dictionary key (`auth.otp.enter_code`).
 */
@JvmInline
public value class StringKey(public val key: String)

/**
 * A typed plural family key; the dictionary holds `key.one`, `key.few`, `key.many`, `key.other`….
 *
 * @property key the family's base key.
 */
@JvmInline
public value class PluralKey(public val key: String)

/**
 * One library's strings: a namespace and a lazily built dictionary per locale.
 *
 * @property namespace key prefix this bundle owns (`stapel.ui`, `auth`), for diagnostics.
 * @property locales locale tag → dictionary factory (called once, on first use of that locale).
 */
public class I18nBundle(public val namespace: String, public val locales: Map<String, () -> I18nDictionary>)

/** Loads remote copy for a locale (e.g. a translation service); applied above bundles, below app overrides. */
public fun interface LocaleLoader {
    /** The dictionary for [locale]; may be empty. */
    public suspend fun load(locale: String): I18nDictionary
}

/** The UI-string engine. */
public interface StapelStrings {
    /** The current locale (BCP 47). Changing it recomposes everything that reads strings. */
    public val locale: StateFlow<String>

    /** [key] in the current locale with `{param}`s filled. */
    public fun t(key: StringKey, vararg params: Pair<String, Any?>): String

    /** A runtime key (e.g. built from data); a missing key returns the key itself. */
    public fun t(key: String, params: Map<String, Any?> = emptyMap()): String

    /** The plural form of [key] for [count] (`{count}` is filled automatically). */
    public fun tPlural(key: PluralKey, count: Long, vararg params: Pair<String, Any?>): String

    /** The template for [key] in the current locale chain, or null when no layer defines it. */
    public fun lookup(key: String): String?

    /** Runs the [LocaleLoader] (when configured) for [locale], then switches to it. */
    public suspend fun setLocale(locale: String)
}

/** Configuration of [StapelStrings]. */
public class StapelStringsBuilder internal constructor() {
    /** The last locale of every lookup chain (`en`). */
    public var defaultLocale: String = "en"

    /** The locale to start in; null = the platform locale. */
    public var initialLocale: String? = null

    /** Remote copy loader, consulted by [StapelStrings.setLocale]. */
    public var loader: LocaleLoader? = null

    internal val bundles = mutableListOf<I18nBundle>()
    internal val overrides = mutableMapOf<String, MutableMap<String, String>>()

    /** Adds a bundle. Order is precedence: register the floor first, then bricks; a later bundle wins on the same key. */
    public fun register(bundle: I18nBundle) {
        bundles += bundle
    }

    /** The app's own copy for [locale]: always wins over every bundle and the loader. */
    public fun override(locale: String, entries: I18nDictionary) {
        overrides.getOrPut(normalizeTag(locale)) { mutableMapOf() }.putAll(entries)
    }
}

/** Builds a [StapelStrings]: `StapelStrings { register(CoreUiBundle); register(AuthUiBundle); override("ru", …) }`. */
public fun StapelStrings(configure: StapelStringsBuilder.() -> Unit): StapelStrings =
    DefaultStapelStrings(StapelStringsBuilder().apply(configure))

private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_.]+)\}""")

/** Fills `{name}` placeholders; an unknown placeholder stays verbatim; values are formatted with `toString()`. */
public fun interpolate(template: String, params: Map<String, Any?>): String =
    PLACEHOLDER.replace(template) { m -> if (params.containsKey(m.groupValues[1])) params[m.groupValues[1]].toString() else m.value }

internal fun normalizeTag(tag: String): String {
    val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    return (listOf(parts[0].lowercase()) + parts.drop(1).map { if (it.length == 2) it.uppercase() else it }).joinToString("-")
}

internal expect fun platformLocaleTag(): String

private class DefaultStapelStrings(config: StapelStringsBuilder) : StapelStrings {
    private val defaultLocale = normalizeTag(config.defaultLocale)
    private val bundles = config.bundles.toList()
    private val overrides: Map<String, Map<String, String>> = config.overrides.mapValues { it.value.toMap() }
    private val loader = config.loader
    private val remote = mutableMapOf<String, I18nDictionary>()
    private val merged = mutableMapOf<String, Map<String, String>>()
    private val state = MutableStateFlow(normalizeTag(config.initialLocale ?: platformLocaleTag()).ifEmpty { defaultLocale })

    override val locale: StateFlow<String> = state.asStateFlow()

    private fun layer(locale: String): Map<String, String> = merged.getOrPut(locale) {
        buildMap {
            bundles.forEach { b -> b.locales[locale]?.invoke()?.let(::putAll) }
            remote[locale]?.let(::putAll)
            overrides[locale]?.let(::putAll)
        }
    }

    private fun chain(): List<String> {
        val tag = state.value
        return listOf(tag, PluralRules.languageOf(tag), defaultLocale).distinct()
    }

    override fun lookup(key: String): String? = chain().firstNotNullOfOrNull { layer(it)[key] }

    override fun t(key: StringKey, vararg params: Pair<String, Any?>): String = t(key.key, params.toMap())

    override fun t(key: String, params: Map<String, Any?>): String = lookup(key)?.let { interpolate(it, params) } ?: key

    override fun tPlural(key: PluralKey, count: Long, vararg params: Pair<String, Any?>): String {
        val category = PluralRules.forLanguage(state.value).category(count)
        val template = lookup("${key.key}.${category.suffix}") ?: lookup("${key.key}.other") ?: lookup(key.key) ?: return key.key
        return interpolate(template, mapOf<String, Any?>("count" to count) + params.toMap())
    }

    override suspend fun setLocale(locale: String) {
        val tag = normalizeTag(locale)
        loader?.let { l ->
            val dict = l.load(tag)
            remote[tag] = dict
            merged.remove(tag)
        }
        state.value = tag
    }
}

/**
 * Completeness of a bundle (the i18n gates): every locale has every key of [referenceLocale], the
 * `{param}` sets of a key are equal across locales, and every plural family has the categories its
 * language requires. Returns one line per problem; empty = complete.
 *
 * @param pluralFamilies base keys that are plural families (their `.<category>` keys are checked per language).
 */
public fun I18nBundle.audit(referenceLocale: String = "en", pluralFamilies: Set<String> = emptySet()): List<String> {
    val dicts = locales.mapValues { it.value() }
    val reference = dicts[referenceLocale] ?: return listOf("$namespace: no '$referenceLocale' dictionary")
    fun params(s: String) = PLACEHOLDER.findAll(s).map { it.groupValues[1] }.toSet()
    fun isPluralKey(k: String) = pluralFamilies.any { k.startsWith("$it.") }
    val problems = mutableListOf<String>()
    for ((locale, dict) in dicts) {
        for ((key, text) in reference) {
            if (isPluralKey(key)) continue
            val other = dict[key]
            if (other == null) problems += "$namespace/$locale: missing '$key'"
            else if (params(other) != params(text)) problems += "$namespace/$locale: '$key' params ${params(other)} != ${params(text)}"
        }
        for (family in pluralFamilies) {
            for (cat in PluralRules.requiredCategories(locale)) {
                if (dict["$family.${cat.suffix}"] == null) problems += "$namespace/$locale: plural '$family' lacks '${cat.suffix}'"
            }
        }
    }
    return problems
}
