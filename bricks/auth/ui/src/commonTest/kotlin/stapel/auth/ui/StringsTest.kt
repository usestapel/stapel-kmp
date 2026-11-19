package stapel.auth.ui

import stapel.auth.api.error.AuthErrorCatalog
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.i18n.audit
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
        assertEquals(emptyList(), AuthUiBundle.audit(pluralFamilies = AuthUiPluralFamilies))
    }

    @Test
    fun auditCatchesAMissingKey() {
        val broken = stapel.i18n.I18nBundle("auth", mapOf("en" to { mapOf("auth.ui.a" to "A", "auth.ui.b" to "{x}") }, "ru" to { mapOf("auth.ui.a" to "А") }))
        assertTrue(broken.audit().any { "missing 'auth.ui.b'" in it })
    }

    @Test
    fun overrideOrderAppCopyWinsOverTheLibrary() {
        val strings = StapelStrings {
            initialLocale = "ru"
            register(CoreUiBundle)
            register(AuthUiBundle)
            override("ru", mapOf("auth.ui.login_title" to "Войти в магазин"))
        }
        assertEquals("Войти в магазин", strings.t(AuthUiStrings.LoginTitle))
        assertEquals("Отправить код", strings.t(AuthUiStrings.SendCode))
        assertEquals("Попробуйте через 3 минуты.", strings.tPlural(AuthUiStrings.LockedMinutes, 3))
        assertEquals("Попробуйте через 21 минуту.", strings.tPlural(AuthUiStrings.LockedMinutes, 21))
    }

    @Test
    fun russianErrorTextsAreKnownCodesWithTheSameParams() {
        for (code in AuthErrorTextsRu.codes) {
            val spec = AuthErrorCatalog.spec(ErrorCode(code))
            assertTrue(spec != null, "ru text for a code the contract does not have: $code")
            val ru = AuthErrorTextsRu.template(ErrorCode(code), LocaleTag("ru"))!!
            val ruParams = placeholder.findAll(ru).map { it.groupValues[1] }.toSet()
            val enParams = placeholder.findAll(spec.en).map { it.groupValues[1] }.toSet()
            assertEquals(enParams, ruParams, "params of $code")
        }
    }

    @Test
    fun errorLadderUsesTheRussianCatalogThenFallsBack() {
        val resolver = ErrorTextResolver(authErrorCatalogs())
        fun err(code: String, status: Int, vararg p: Pair<String, String>) = ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(p.toMap())))
        assertEquals(
            "Неверный код подтверждения. Осталось попыток: 2.",
            resolver.message(err("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2"), LocaleTag("ru-RU")),
        )
        assertEquals(
            "Invalid verification code. 2 attempts remaining.",
            resolver.message(err("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2"), LocaleTag("en")),
        )
        // A code the backend has not translated falls back to the contract's English text (the ladder
        // walks tag, language, then `en` before the status floor).
        val untranslated = "error.400.expected_list"
        assertTrue(AuthErrorCatalog.spec(ErrorCode(untranslated)) != null && untranslated !in AuthErrorTextsRu.codes)
        assertEquals("Expected a list of items", resolver.message(err(untranslated, 400), LocaleTag("ru")))
    }

}
