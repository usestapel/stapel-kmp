package stapel.i18n

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StapelStringsTest {
    private val floor = I18nBundle("floor", mapOf("en" to { mapOf("k" to "floor", "only.floor" to "F") }, "ru" to { mapOf("k" to "пол") }))
    private val brick = I18nBundle("brick", mapOf("en" to { mapOf("k" to "brick") }, "ru" to { mapOf("k" to "кирпич") }))

    @Test
    fun overrideOrderIsFloorThenBrickThenApp() {
        val s = StapelStrings {
            initialLocale = "en"
            register(floor)
            register(brick)
            override("en", mapOf("k" to "app"))
        }
        assertEquals("app", s.t("k"))
        assertEquals("F", s.t("only.floor"))
        assertEquals("brick", StapelStrings { initialLocale = "en"; register(floor); register(brick) }.t("k"))
        // Inversion: registration order is the precedence, so reversing it changes the winner.
        assertEquals("floor", StapelStrings { initialLocale = "en"; register(brick); register(floor) }.t("k"))
    }

    @Test
    fun regionFallsBackToLanguageThenDefault() {
        val s = StapelStrings {
            initialLocale = "ru_ru"
            register(floor)
            register(brick)
        }
        assertEquals("ru-RU", s.locale.value)
        assertEquals("кирпич", s.t("k"))
        assertEquals("F", s.t("only.floor"))
        assertEquals("missing.key", s.t("missing.key"))
    }

    @Test
    fun appOverrideOfOneLocaleDoesNotLeakIntoAnother() {
        val s = StapelStrings {
            initialLocale = "ru"
            register(floor)
            register(brick)
            override("en", mapOf("k" to "app"))
        }
        assertEquals("кирпич", s.t("k"))
    }

    @Test
    fun runtimeLocaleSwitchAppliesLoaderBelowOverrides() = runTest {
        val loaded = mutableListOf<String>()
        val s = StapelStrings {
            initialLocale = "en"
            register(floor)
            register(brick)
            override("es", mapOf("k" to "app-es"))
            loader = LocaleLoader { tag -> loaded += tag; mapOf("k" to "remote-$tag", "only.floor" to "R") }
        }
        assertEquals("brick", s.t("k"))
        s.setLocale("ru")
        assertEquals("ru", s.locale.value)
        assertEquals("remote-ru", s.t("k"))
        assertEquals("R", s.t("only.floor"))
        s.setLocale("es")
        assertEquals("app-es", s.t("k"))
        assertEquals(listOf("ru", "es"), loaded)
        s.setLocale("en")
        assertEquals("remote-en", s.t("k"))
    }

    @Test
    fun paramsAndUnknownPlaceholders() {
        val s = StapelStrings {
            initialLocale = "en"
            override("en", mapOf("greet" to "Hi {name}, {unknown}"))
        }
        assertEquals("Hi Ann, {unknown}", s.t(StringKey("greet"), "name" to "Ann"))
        assertEquals("Hi null, {unknown}", s.t("greet", mapOf("name" to null)))
    }

    @Test
    fun pluralFormsFollowTheLanguage() = runTest {
        val s = StapelStrings {
            initialLocale = "en"
            register(CoreUiBundle)
        }
        assertEquals("1 character left", s.tPlural(CoreUiStrings.CharactersLeft, 1))
        assertEquals("5 characters left", s.tPlural(CoreUiStrings.CharactersLeft, 5))
        s.setLocale("ru")
        assertEquals("Остался 21 символ", s.tPlural(CoreUiStrings.CharactersLeft, 21))
        assertEquals("Осталось 3 символа", s.tPlural(CoreUiStrings.CharactersLeft, 3))
        assertEquals("Осталось 11 символов", s.tPlural(CoreUiStrings.CharactersLeft, 11))
        s.setLocale("es")
        assertEquals("Quedan 1000000 de caracteres", s.tPlural(CoreUiStrings.CharactersLeft, 1_000_000))
    }

    @Test
    fun pluralFallsBackToOtherThenFlatKey() {
        val s = StapelStrings {
            initialLocale = "ru"
            override("ru", mapOf("a.other" to "other {count}", "b" to "flat {count}"))
        }
        assertEquals("other 2", s.tPlural(PluralKey("a"), 2))
        assertEquals("flat 2", s.tPlural(PluralKey("b"), 2))
        assertEquals("c", s.tPlural(PluralKey("c"), 2))
    }

    @Test
    fun coreBundleIsComplete() {
        assertEquals(emptyList(), CoreUiBundle.audit(pluralFamilies = setOf("stapel.ui.characters_left")))
    }

    @Test
    fun auditCatchesEveryIncompleteness() {
        val broken = I18nBundle(
            "x",
            mapOf(
                "en" to { mapOf("a" to "A {n}", "b" to "B", "p.one" to "1", "p.other" to "n") },
                "ru" to { mapOf("a" to "А {m}", "p.one" to "1", "p.few" to "2", "p.other" to "n") },
            ),
        )
        val problems = broken.audit(pluralFamilies = setOf("p"))
        assertTrue(problems.any { "missing 'b'" in it }, problems.toString())
        assertTrue(problems.any { "'a' params" in it }, problems.toString())
        assertTrue(problems.any { "plural 'p' lacks 'many'" in it }, problems.toString())
        assertEquals(3, problems.size, problems.toString())
    }
}

class PluralRulesTest {
    private val samples = listOf(0L, 1L, 2L, 5L, 11L, 21L, 101L, 1_000_000L)

    private fun cats(lang: String) = samples.map { PluralRules.forLanguage(lang).category(it) }

    @Test
    fun englishCldrVectors() {
        val o = PluralCategory.Other
        assertEquals(listOf(o, PluralCategory.One, o, o, o, o, o, o), cats("en"))
    }

    @Test
    fun russianCldrVectors() {
        val one = PluralCategory.One
        val few = PluralCategory.Few
        val many = PluralCategory.Many
        assertEquals(listOf(many, one, few, many, many, one, one, many), cats("ru-RU"))
        assertEquals(listOf(few, few, many, many, one), listOf(22L, 104L, 112L, 14L, 1001L).map { PluralRules.Russian.category(it) })
    }

    @Test
    fun spanishCldrVectors() {
        val o = PluralCategory.Other
        assertEquals(listOf(o, PluralCategory.One, o, o, o, o, o, PluralCategory.Many), cats("es"))
    }

    @Test
    fun unknownLanguageIsOtherAndRegistrationReplacesIt() {
        assertEquals(PluralCategory.Other, PluralRules.forLanguage("xx").category(1))
        PluralRules.register("xx", PluralRule { if (it == 1L) PluralCategory.One else PluralCategory.Other })
        assertEquals(PluralCategory.One, PluralRules.forLanguage("xx-YY").category(1))
    }
}
