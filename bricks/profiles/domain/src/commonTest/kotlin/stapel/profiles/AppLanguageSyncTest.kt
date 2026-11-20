@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.profiles.internal.AppLanguageSync
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.result.text.LocaleTag
import stapel.runtime.InMemoryLocalePreferenceStorage
import stapel.runtime.StoredLocalePreference
import stapel.runtime.storedLocaleTag
import stapel.session.SessionState
import stapel.testing.FakeAnswer
import stapel.testing.FixedLocaleProvider
import stapel.wire.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app's locale follows the signed-in person's app language, and goes back to the device on request. */
class AppLanguageSyncTest {
    @Test
    fun aChosenLanguageSwitchesTheAppAndFollowTheDeviceHandsItBack() = runTest {
        val f = Fixture(this)
        val session = MutableStateFlow<SessionState>(SessionState.Unauthenticated(null))
        val locale = FixedLocaleProvider(LocaleTag("en"))
        var stored = profile()
        f.transport.on(Ops.ME) { FakeAnswer.Value(stored) }
        f.transport.on(Ops.UPDATE) { call ->
            val body = call.body as stapel.profiles.api.model.PatchedProfileUpdateRequest
            stored = if (body.useDeviceLanguage.getOrNull() == true) profile() else profile(language = body.appLanguage.getOrNull())
            FakeAnswer.Value(stored)
        }
        AppLanguageSync(f.profiles.me, session, locale, null, backgroundScope)
        runCurrent()
        assertEquals(0, f.count(Ops.ME), "signed out: the profile is not read")

        f.signIn()
        session.value = SessionState.Authenticated(1, "u1")
        runCurrent()
        assertEquals("en", locale.locale.value.value)

        val settings = ProfileSettingsPresenter(f.profiles, f.context)
        runCurrent()
        settings.dispatch(ProfileSettingsIntent.SetLanguage("ar"))
        runCurrent()
        assertEquals("ar", locale.locale.value.value, "the choice switched the app's locale")

        settings.dispatch(ProfileSettingsIntent.SetLanguage(null))
        runCurrent()
        assertEquals("en", locale.locale.value.value, "follow the device hands the locale back")
    }

    @Test
    fun aRestartPutsTheAppBackInTheStoredLanguage() = runTest {
        val f = Fixture(this)
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile(language = "ru")) }
        val locale = FixedLocaleProvider(LocaleTag("en"))
        f.signIn()
        AppLanguageSync(f.profiles.me, MutableStateFlow(SessionState.Authenticated(1, "u1")), locale, null, backgroundScope)
        runCurrent()
        assertEquals("ru", locale.locale.value.value)
    }

    /** The device's signed-out choice over a [FixedLocaleProvider], as `runtimeModule()` wires it. */
    private fun preference(locale: FixedLocaleProvider, initial: String?): StoredLocalePreference {
        val storage = InMemoryLocalePreferenceStorage(initial)
        storedLocaleTag(storage)?.let(locale::setAppLocale)
        return StoredLocalePreference(storage, locale)
    }

    @Test
    fun aLanguageChosenSignedOutSeedsAProfileWithoutOneAndStaysAfterSignOut() = runTest {
        val f = Fixture(this)
        val session = MutableStateFlow<SessionState>(SessionState.Unauthenticated(null))
        val locale = FixedLocaleProvider(LocaleTag("en"))
        val chosen = preference(locale, initial = null)
        var stored = profile()
        f.transport.on(Ops.ME) { FakeAnswer.Value(stored) }
        f.transport.on(Ops.UPDATE) { call ->
            val body = call.body as stapel.profiles.api.model.PatchedProfileUpdateRequest
            stored = profile(language = body.appLanguage.getOrNull())
            FakeAnswer.Value(stored)
        }
        AppLanguageSync(f.profiles.me, session, locale, chosen, backgroundScope)
        runCurrent()
        chosen.choose(LocaleTag("de"))
        assertEquals("de", locale.locale.value.value, "signed out, the choice applies at once")

        f.signIn()
        session.value = SessionState.Authenticated(1, "u1")
        runCurrent()
        assertEquals(1, f.count(Ops.UPDATE), "the profile without a language was seeded once")
        assertTrue("\"app_language\":\"de\"" in f.patchBody() && "\"use_device_language\":false" in f.patchBody(), f.patchBody())
        assertEquals("de", locale.locale.value.value)

        session.value = SessionState.Unauthenticated(null)
        runCurrent()
        assertEquals("de", locale.locale.value.value, "signed out again: the device's choice")
        assertEquals(LocaleTag("de"), chosen.chosen.value)
    }

    @Test
    fun aLanguageSetInTheProfileWinsWhileSignedInAndTheDevicesChoiceReturnsAfter() = runTest {
        val f = Fixture(this)
        val session = MutableStateFlow<SessionState>(SessionState.Unauthenticated(null))
        val locale = FixedLocaleProvider(LocaleTag("en"))
        val chosen = preference(locale, initial = "de")
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile(language = "ru")) }
        AppLanguageSync(f.profiles.me, session, locale, chosen, backgroundScope)
        runCurrent()
        assertEquals("de", locale.locale.value.value, "the stored choice applies before sign-in")

        f.signIn()
        session.value = SessionState.Authenticated(1, "u1")
        runCurrent()
        assertEquals("ru", locale.locale.value.value, "the profile's language wins")
        assertEquals(0, f.count(Ops.UPDATE), "a set profile language is never overwritten")

        session.value = SessionState.Unauthenticated(null)
        runCurrent()
        assertEquals("de", locale.locale.value.value, "signed out: the device's choice again")
        assertEquals(LocaleTag("de"), chosen.chosen.value, "the profile never writes the device's choice")
    }

    @Test
    fun withoutAChoiceAProfileWithoutALanguageIsLeftAlone() = runTest {
        val f = Fixture(this)
        val locale = FixedLocaleProvider(LocaleTag("en"))
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile()) }
        f.signIn()
        AppLanguageSync(f.profiles.me, MutableStateFlow(SessionState.Authenticated(1, "u1")), locale, preference(locale, null), backgroundScope)
        runCurrent()
        assertEquals(0, f.count(Ops.UPDATE))
        assertEquals("en", locale.locale.value.value)
    }
}
