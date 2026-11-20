package stapel.profiles.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import stapel.profiles.model.MyProfile
import stapel.profiles.repo.MyProfileRepository
import stapel.result.state.Resource
import stapel.result.text.LocaleTag
import stapel.runtime.LocaleProvider
import stapel.session.SessionState

/**
 * Keeps the app's locale on the signed-in person's app language (`app_language` /
 * `use_device_language` of their profile): a choice in the language screen switches the app at once
 * (the `PATCH` answer reaches the cache, this follows it), and after a restart the stored profile — the
 * durable cache's first paint, then the server's — puts the app back in that language. "Follow the
 * device" hands the locale back to the device. Signed out, nothing is read and the locale is left as it is.
 *
 * The app's strings and layout direction follow [LocaleProvider] (`stapelApp` wires that); this class
 * only decides the in-app choice ([LocaleProvider.setAppLocale]).
 *
 * @param me the own profile.
 * @param session the session state (the profile is read only while signed in).
 * @param locale the app's locale provider.
 * @param scope the app scope.
 */
internal class AppLanguageSync(
    me: MyProfileRepository,
    session: kotlinx.coroutines.flow.StateFlow<SessionState>,
    private val locale: LocaleProvider,
    scope: CoroutineScope,
) : AutoCloseable {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val job: Job = scope.launch {
        session.map { it is SessionState.Authenticated }.distinctUntilChanged()
            .flatMapLatest { signedIn -> if (signedIn) me.me() else emptyFlow() }
            .collect { if (it is Resource.Ready) apply(it.data) }
    }

    private fun apply(profile: MyProfile) {
        val code = profile.appLanguage?.code?.takeIf { !profile.useDeviceLanguage && it.isNotBlank() }
        locale.setAppLocale(code?.let(::LocaleTag))
    }

    /** Stops following the profile; the current locale stays. */
    override fun close() {
        job.cancel()
    }
}
