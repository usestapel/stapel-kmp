package stapel.profiles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.compose.LocalPresenterFactory
import stapel.mvi.compose.PresenterFactory
import stapel.mvi.compose.collectState
import stapel.mvi.compose.rememberPresenter
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.ResolvedDestination
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.ScreenScope
import stapel.profiles.StapelProfiles
import stapel.profiles.presentation.ConnectionsPresenter
import stapel.profiles.presentation.ContactsPresenter
import stapel.profiles.presentation.LanguageCataloguePresenter
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.profiles.presentation.PublicProfilePresenter
import stapel.wire.FilePart
import kotlin.reflect.KClass

/**
 * Picks a picture for the avatar (a photo picker, a camera, a file chooser): the app's platform code,
 * since this library is common-only. Null from [pick] = the person cancelled. Without a picker the
 * settings screen offers no avatar change.
 */
public fun interface AvatarPicker {
    /** The picked picture, or null when nothing was picked. */
    public suspend fun pick(): FilePart?
}

/**
 * Creates this library's presenters for `rememberPresenter` from one [StapelProfiles]; other types go
 * to [next]. [PublicProfilePresenter] takes `(userId: String, signedIn: Boolean)` as its arguments.
 */
public class ProfilesPresenterFactory(private val profiles: StapelProfiles, private val next: PresenterFactory? = null) : PresenterFactory {
    /** The profiles presenter of [type], or [next]'s for any other type. */
    @Suppress("UNCHECKED_CAST")
    override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P = when (type) {
        ProfileSettingsPresenter::class -> ProfileSettingsPresenter(profiles, context)
        LanguageCataloguePresenter::class -> LanguageCataloguePresenter(profiles, context)
        ContactsPresenter::class -> ContactsPresenter(profiles, context)
        ConnectionsPresenter::class -> ConnectionsPresenter(profiles, context)
        PublicProfilePresenter::class -> PublicProfilePresenter(profiles, context, args[0] as String, args[1] as Boolean)
        else -> next?.create(type, context, args) ?: error("ProfilesPresenterFactory cannot create ${type.simpleName}")
    } as P
}

/** Provides a [ProfilesPresenterFactory] over [profiles] (chained to the factory already provided) to [content]. */
@Composable
public fun ProvideProfilesPresenters(profiles: StapelProfiles, content: @Composable () -> Unit) {
    val outer = LocalPresenterFactory.current
    val factory = remember(profiles, outer) { ProfilesPresenterFactory(profiles, outer) }
    CompositionLocalProvider(LocalPresenterFactory provides factory, content = content)
}

/** The sections of the settings container: the destinations installed under it, in navigation order. */
public fun ResolvedDestination.settingsSections(): List<SettingsSection> =
    children.map { SettingsSection(it.id, it.labelKey, it.icon) }

private fun ScreenScope.open(id: DestinationId) {
    val route = (navigator as? StapelNavigator)?.nav?.defaultRoute(id) ?: return
    navigator.navigate(route)
}

/** `profiles.settings` bound to [profiles]: its sections are the destinations installed under it. */
@Composable
public fun ScreenScope.ProfileSettings(profiles: StapelProfiles, picker: AvatarPicker?) {
    ProvideProfilesPresenters(profiles) {
        val presenter = rememberPresenter<ProfileSettingsPresenter>()
        val state by presenter.collectState()
        val scope = rememberCoroutineScope()
        val sections = remember(destination) { destination.settingsSections() }
        LocalProfilesScreens.current.Settings(
            state,
            sections,
            presenter::dispatch,
            { open(it) },
            picker?.let { p -> { scope.launch { p.pick()?.let { presenter.dispatch(ProfileSettingsIntent.SetAvatar(it)) } } } },
        )
    }
}

/** `profiles.language` bound to [profiles]. */
@Composable
public fun ProfileLanguage(profiles: StapelProfiles) {
    ProvideProfilesPresenters(profiles) {
        val presenter = rememberPresenter<ProfileSettingsPresenter>()
        val catalogue = rememberPresenter<LanguageCataloguePresenter>()
        val state by presenter.collectState()
        val languages by catalogue.collectState()
        LocalProfilesScreens.current.Language(state, languages, presenter::dispatch)
    }
}

/** `profiles.notifications` bound to [profiles]. */
@Composable
public fun ProfileNotifications(profiles: StapelProfiles) {
    ProvideProfilesPresenters(profiles) {
        val presenter = rememberPresenter<ProfileSettingsPresenter>()
        val state by presenter.collectState()
        LocalProfilesScreens.current.Notifications(state, presenter::dispatch)
    }
}

/** `profiles.contacts` bound to [profiles]. */
@Composable
public fun ProfileContacts(profiles: StapelProfiles) {
    ProvideProfilesPresenters(profiles) {
        val presenter = rememberPresenter<ContactsPresenter>()
        val state by presenter.collectState()
        LocalProfilesScreens.current.Contacts(state, presenter::dispatch)
    }
}

/** `profiles.connections` bound to [profiles]; a row opens that person's public profile. */
@Composable
public fun ScreenScope.ProfileConnections(profiles: StapelProfiles) {
    ProvideProfilesPresenters(profiles) {
        val presenter = rememberPresenter<ConnectionsPresenter>()
        val state by presenter.collectState()
        LocalProfilesScreens.current.Connections(state, presenter::dispatch) { userId -> navigator.navigate(PublicProfileRoute(userId)) }
    }
}

/** `profiles.public` bound to [profiles]; the `page.actions` slot renders other libraries' embeds. */
@Composable
public fun ScreenScope.PublicProfile(profiles: StapelProfiles, route: PublicProfileRoute) {
    ProvideProfilesPresenters(profiles) {
        val nav by navigator.state.collectAsState()
        val signedIn = nav.auth == NavAuthState.Member
        val presenter = rememberPresenter<PublicProfilePresenter>(route.userId, signedIn)
        val state by presenter.collectState()
        LocalProfilesScreens.current.PublicProfile(state, presenter::dispatch) { PageSlot(ProfilesNavIds.PageActions, Modifier) }
    }
}
