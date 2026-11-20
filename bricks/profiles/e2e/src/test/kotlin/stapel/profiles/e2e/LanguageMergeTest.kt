package stapel.profiles.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.auth.session.AuthState
import stapel.profiles.model.ProfileUpdate
import stapel.result.StapelResult
import stapel.result.text.LocaleTag
import stapel.runtime.InMemoryLocalePreferenceStorage
import stapel.runtime.LocalePreference
import stapel.runtime.LocaleProvider
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The app language before and after sign-in against the stand: a language chosen signed out
 * (`LocalePreference`, stored on the device) applies from the start, seeds an account whose profile has
 * none, loses to an account whose profile has one, and comes back after sign-out.
 */
class LanguageMergeTest {
    private val Device.locale: LocaleProvider get() = app.koin.get()
    private val Device.preference: LocalePreference get() = app.koin.get()

    private suspend fun Device.awaitLocale(code: String) {
        locale.locale.await { it.value == code }
    }

    private suspend fun Device.serverLanguage(): String? =
        (Stand.get("/profiles/api/v1/me", bearer())["app_language"]?.takeIf { it !is JsonNull })?.jsonObject?.get("code")?.jsonPrimitive?.content

    @Test
    fun `L-1 a language chosen signed out applies at once, survives a relaunch and seeds a new account's profile`(): Unit = runBlocking {
        val storage = InMemoryLocalePreferenceStorage()
        Device("l1a", localeStorage = storage).use { d ->
            d.preference.choose(LocaleTag("ru"))
            assertEquals("ru", d.locale.locale.value.value, "signed out, the choice applies at once")
        }
        Device("l1b", localeStorage = storage).use { d ->
            assertEquals("ru", d.locale.locale.value.value, "a relaunch starts in the stored choice, before any sign-in")
            d.signIn()
            waitFor("the profile seeded") { d.serverLanguage() == "ru" }
            assertEquals(false, Stand.get("/profiles/api/v1/me", d.bearer())["use_device_language"]!!.jsonPrimitive.boolean)
            d.awaitLocale("ru")

            d.auth.session.logout()
            d.auth.session.state.await { it is AuthState.SignedOut }
            assertEquals("ru", d.locale.locale.value.value)
        }
    }

    @Test
    fun `L-2 a language set in the profile wins after sign-in and the device's choice returns after sign-out`(): Unit = runBlocking {
        val email = Stand.email()
        Device("l2a").use { d ->
            d.signIn(email)
            assertIs<StapelResult.Ok<*>>(d.profiles.me.update(ProfileUpdate(appLanguage = "en", useDeviceLanguage = false)))
            d.auth.session.logout()
        }
        Device("l2b").use { d ->
            d.preference.choose(LocaleTag("ru"))
            d.signIn(email)
            d.awaitLocale("en")
            assertEquals("en", d.serverLanguage(), "a set profile language is never overwritten by the device's choice")
            assertEquals(LocaleTag("ru"), d.preference.chosen.value, "the profile never writes the device's choice")

            d.auth.session.logout()
            d.awaitLocale("ru")
        }
    }

    private suspend fun waitFor(what: String, condition: suspend () -> Boolean) {
        repeat(100) {
            if (condition()) return
            kotlinx.coroutines.delay(100)
        }
        assertTrue(condition(), "timed out: $what")
    }
}
