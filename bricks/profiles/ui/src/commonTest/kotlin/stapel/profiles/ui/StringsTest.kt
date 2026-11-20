package stapel.profiles.ui

import stapel.i18n.CoreUiBundle
import stapel.i18n.I18nBundle
import stapel.i18n.StapelStrings
import stapel.i18n.audit
import stapel.profiles.api.error.ProfilesErrorCatalog
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.ServerRefusal
import stapel.result.text.ErrorTextResolver
import stapel.result.text.LocaleTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    private val placeholder = Regex("""\{([\w.]+)\}""")

    @Test
    fun bundleIsCompleteInEveryLocale() {
        assertEquals(emptyList(), ProfilesUiBundle.audit(pluralFamilies = ProfilesPluralFamilies))
    }

    @Test
    fun theAuditCatchesAMissingKeyAndADriftedPlaceholder() {
        val broken = I18nBundle(
            "profiles",
            mapOf("en" to { mapOf("profiles.a" to "A", "profiles.b" to "{x}") }, "ru" to { mapOf("profiles.b" to "{y}") }),
        )
        val problems = broken.audit()
        assertTrue(problems.any { "missing 'profiles.a'" in it } && problems.any { "'profiles.b' params" in it }, problems.toString())
    }

    @Test
    fun russianPluralsFollowTheRussianRules() {
        val strings = StapelStrings { initialLocale = "ru"; register(CoreUiBundle); register(ProfilesUiBundle) }
        assertEquals("1 подписчик", strings.tPlural(ProfilesStrings.ListCountFollowers, 1, "count" to 1))
        assertEquals("3 подписчика", strings.tPlural(ProfilesStrings.ListCountFollowers, 3, "count" to 3))
        assertEquals("11 подписчиков", strings.tPlural(ProfilesStrings.ListCountFollowers, 11, "count" to 11))
        assertEquals("Настройки", strings.t(ProfilesStrings.NavSettings))
    }

    @Test
    fun russianErrorTextsAreKnownCodesWithTheSameParams() {
        assertTrue(ProfilesErrorTextsRu.codes.isNotEmpty())
        for (code in ProfilesErrorTextsRu.codes) {
            val spec = ProfilesErrorCatalog.spec(ErrorCode(code))
            assertTrue(spec != null, "ru text for a code the contract does not have: $code")
            val ru = ProfilesErrorTextsRu.template(ErrorCode(code), LocaleTag("ru"))!!
            assertEquals(placeholder.findAll(spec.en).map { it.groupValues[1] }.toSet(), placeholder.findAll(ru).map { it.groupValues[1] }.toSet(), code)
        }
    }

    @Test
    fun errorLadderUsesTheRussianCatalogThenTheContractsEnglish() {
        val resolver = ErrorTextResolver(profilesErrorCatalogs())
        val ruCode = ProfilesErrorTextsRu.codes.first()
        val err = ServerRefusal(ErrorEnvelope(ErrorCode(ruCode), 400, ErrorParams(emptyMap())))
        val ru = resolver.message(err, LocaleTag("ru"))
        assertTrue(ru.any { it in 'а'..'я' || it in 'А'..'Я' }, ru)
        val en = resolver.message(ServerRefusal(ErrorEnvelope(ErrorCode("error.409.contacts_duplicate"), 409, ErrorParams(emptyMap()))), LocaleTag("en"))
        assertEquals(ProfilesErrorCatalog.spec(ErrorCode("error.409.contacts_duplicate"))!!.en, en)
    }
}
