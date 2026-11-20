package stapel.profiles.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import stapel.profiles.model.MyProfile
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.repo.MyProfileRepository
import stapel.result.StapelResult
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.result.text.LocaleTag
import stapel.runtime.LocalePreference
import stapel.runtime.LocaleProvider
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.warn
import stapel.session.SessionState

/**
 * Keeps the app's locale on the signed-in person's app language (`app_language` /
 * `use_device_language` of their profile): a choice in the language screen switches the app at once
 * (the `PATCH` answer reaches the cache, this follows it), and after a restart the stored profile — the
 * durable cache's first paint, then the server's — puts the app back in that language. "Follow the
 * device" hands the locale back to the device.
 *
 * Merge with the device's signed-out choice ([LocalePreference]):
 * - signed in, a language set in the profile wins (it is what the app shows);
 * - a profile without a language (`app_language` null), read from the server, is seeded once per sign-in
 *   with the signed-out choice (`PATCH app_language, use_device_language=false`), and the app stays in
 *   that language meanwhile; a refused seed is logged and the app keeps the choice for the session;
 * - signed out again after a profile language was applied, the signed-out choice applies (or the
 *   device's language when there is none). The profile never writes the signed-out choice.
 *
 * The app's strings and layout direction follow [LocaleProvider] (`stapelApp` wires that); this class
 * only decides the in-app choice ([LocaleProvider.setAppLocale]).
 *
 * @param me the own profile.
 * @param session the session state (the profile is read only while signed in).
 * @param locale the app's locale provider.
 * @param preference the device's signed-out choice, or null when the app binds none.
 * @param scope the app scope.
 * @param logger where a refused seed is reported.
 */
internal class AppLanguageSync(
    private val me: MyProfileRepository,
    session: StateFlow<SessionState>,
    private val locale: LocaleProvider,
    private val preference: LocalePreference?,
    private val scope: CoroutineScope,
    private val logger: StapelLogger = NoopLogger,
) : AutoCloseable {
    private var appliedFromProfile = false
    private var seeded = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val job: Job = scope.launch {
        session.map { it is SessionState.Authenticated }.distinctUntilChanged()
            .flatMapLatest { signedIn ->
                seeded = false
                if (signedIn) {
                    me.me()
                } else {
                    leaveAccount()
                    emptyFlow()
                }
            }
            .collect { if (it is Resource.Ready) apply(it.data, fromServer = it.freshness.origin == Origin.Network) }
    }

    private fun apply(profile: MyProfile, fromServer: Boolean) {
        val chosen = preference?.chosen?.value
        val own = profile.appLanguage?.code?.takeIf { it.isNotBlank() }
        val code: LocaleTag? = when {
            own != null -> if (profile.useDeviceLanguage) null else LocaleTag(own)
            chosen != null -> {
                if (fromServer && !seeded) seed(chosen)
                chosen
            }
            else -> null
        }
        appliedFromProfile = true
        locale.setAppLocale(code)
    }

    private fun seed(chosen: LocaleTag) {
        seeded = true
        scope.launch {
            val r = me.update(ProfileUpdate(appLanguage = chosen.value, useDeviceLanguage = false))
            if (r is StapelResult.Err) logger.warn(TAG) { "the profile's language was not seeded with ${chosen.value}: ${r.error.code.value}" }
        }
    }

    private fun leaveAccount() {
        if (!appliedFromProfile) return
        appliedFromProfile = false
        locale.setAppLocale(preference?.chosen?.value)
    }

    /** Stops following the profile; the current locale stays. */
    override fun close() {
        job.cancel()
    }

    private companion object {
        const val TAG = "stapel.profiles"
    }
}
