@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.profiles.internal.AppLanguageSync
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.result.text.LocaleTag
import stapel.session.SessionState
import stapel.testing.FakeAnswer
import stapel.testing.FixedLocaleProvider
import stapel.wire.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

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
        AppLanguageSync(f.profiles.me, session, locale, backgroundScope)
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
        AppLanguageSync(f.profiles.me, MutableStateFlow(SessionState.Authenticated(1, "u1")), locale, backgroundScope)
        runCurrent()
        assertEquals("ru", locale.locale.value.value)
    }
}
