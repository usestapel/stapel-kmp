package stapel.profiles.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.profiles.error.ProfilesError
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.presentation.ConnectionsState
import stapel.profiles.presentation.ContactsState
import stapel.profiles.presentation.LanguageCatalogueState
import stapel.profiles.presentation.PendingVerification
import stapel.profiles.presentation.ProfileSettingsState
import stapel.profiles.presentation.PublicProfileState
import stapel.profiles.testing.ProfilesSamples
import stapel.profiles.ui.ConnectionsScreen
import stapel.profiles.ui.ContactsScreen
import stapel.profiles.ui.LanguageScreen
import stapel.profiles.ui.NotificationsScreen
import stapel.profiles.ui.ProfileSettingsScreen
import stapel.profiles.ui.ProfilesUiBundle
import stapel.profiles.ui.PublicProfileScreen
import stapel.profiles.ui.SettingsSection
import stapel.profiles.ui.profilesErrorCatalogs
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.ServerRefusal
import stapel.result.TransportFailure
import stapel.result.text.ErrorTextResolver
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.StoryEnvironment
import stapel.showcase.StoryKind
import stapel.showcase.StoryScope
import stapel.showcase.StoryState
import stapel.showcase.StoryTarget
import stapel.ui.LocalStapelErrorText

private fun profiles(id: String, title: String, group: String, screen: String, states: List<StoryState> = emptyList(), content: @Composable StoryScope.() -> Unit): Story =
    Story(id, title, group, StoryKind.Screen, states = states, covers = setOf(StoryTarget.Screen(screen)), content = content)

private val ru = listOf(StoryState.Default, StoryState("ru", "Russian", locale = "ru"))

private fun refusal(code: String, status: Int, vararg params: Pair<String, String>) =
    ProfilesError.of(ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params.toMap()))))

private val sections = listOf(
    SettingsSection(DestinationId("profiles.language"), "profiles.nav.language", NavIcons.Message),
    SettingsSection(DestinationId("profiles.notifications"), "profiles.nav.notifications", NavIcons.Bell),
    SettingsSection(DestinationId("profiles.contacts"), "profiles.nav.contacts", NavIcons.Message),
)

private val me = ProfilesSamples.me
private val ready = ProfileSettingsState(profile = me, loading = false, canUploadAvatar = true)

private val profilesStories: List<Story> = listOf(
    profiles("settings.ready", "Settings", "Settings", "Settings", ru) {
        ProfileSettingsScreen(ready, sections, { event(it.toString()) }, { event("open $it") }, { event("pick avatar") })
    },
    profiles("settings.loading", "Settings, loading", "Settings", "Settings") {
        ProfileSettingsScreen(ProfileSettingsState(), sections, { event(it.toString()) }, { event("open $it") }, null)
    },
    profiles("settings.uploading", "Settings, avatar uploading", "Settings", "Settings") {
        ProfileSettingsScreen(ready.copy(uploading = true), sections, { event(it.toString()) }, { event("open $it") }, { event("pick avatar") })
    },
    profiles("settings.refused", "Settings, name refused", "Settings", "Settings") {
        ProfileSettingsScreen(ready.copy(saveError = refusal("error.400.display_name_emoji", 400)), sections, { event(it.toString()) }, { event("open $it") }, null)
    },
    profiles("settings.offline", "Settings, offline", "Settings", "Settings") {
        ProfileSettingsScreen(ProfileSettingsState(loading = false, loadError = ProfilesError.of(TransportFailure.Offline("demo"))), sections, { event(it.toString()) }, { event("open $it") }, null)
    },
    profiles("language.ready", "Language", "Settings", "Language", ru) {
        LanguageScreen(ready, LanguageCatalogueState(ProfilesSamples.languages, loading = false), { event(it.toString()) })
    },
    profiles("language.empty_catalogue", "Language, empty catalogue", "Settings", "Language") {
        LanguageScreen(ready.copy(profile = me.copy(useDeviceLanguage = true, appLanguage = null)), LanguageCatalogueState(emptyList(), loading = false), { event(it.toString()) })
    },
    profiles("notifications.ready", "Notifications", "Settings", "Notifications", ru) {
        NotificationsScreen(ready, { event(it.toString()) })
    },
    profiles("contacts.list", "Phone numbers", "Settings", "Contacts", ru) {
        ContactsScreen(ContactsState(contacts = ProfilesSamples.contacts, loading = false), { event(it.toString()) })
    },
    profiles("contacts.empty", "Phone numbers, none yet", "Settings", "Contacts") {
        ContactsScreen(ContactsState(contacts = ProfilesSamples.contacts.copy(contacts = emptyList()), loading = false), { event(it.toString()) })
    },
    profiles("contacts.verifying", "Phone numbers, SMS code", "Settings", "Contacts") {
        ContactsScreen(
            ContactsState(
                contacts = ProfilesSamples.contacts,
                loading = false,
                verification = PendingVerification(2, 300),
                error = refusal("error.400.contacts_invalid_code", 400, "attempts_remaining" to "2"),
            ),
            { event(it.toString()) },
        )
    },
    profiles("contacts.guest", "Phone numbers, guest account", "Settings", "Contacts") {
        ContactsScreen(ContactsState(loading = false, loadError = refusal("error.403.contacts_registration_required", 403)), { event(it.toString()) })
    },
    profiles("connections.followers", "Connections, followers", "People", "Connections", ru) {
        ConnectionsScreen(ConnectionsState(people = ProfilesSamples.people, loading = false), { event(it.toString()) }, { event("open $it") })
    },
    profiles("connections.blocked_empty", "Connections, nobody blocked", "People", "Connections") {
        ConnectionsScreen(ConnectionsState(kind = ConnectionKind.Blocked, people = emptyList(), loading = false), { event(it.toString()) }, { event("open $it") })
    },
    profiles("public.stranger", "Public profile, a stranger", "People", "PublicProfile", ru) {
        PublicProfileScreen(PublicProfileState(ProfilesSamples.person().userId, ProfilesSamples.person(), loading = false, signedIn = true), { event(it.toString()) }, pageActions = {})
    },
    profiles("public.following", "Public profile, followed", "People", "PublicProfile") {
        val p = ProfilesSamples.person(relationship = RelationshipStatus.Following)
        PublicProfileScreen(PublicProfileState(p.userId, p, loading = false, signedIn = true), { event(it.toString()) }, pageActions = {})
    },
    profiles("public.self", "Public profile, oneself", "People", "PublicProfile") {
        val p = ProfilesSamples.person(relationship = RelationshipStatus.Self)
        PublicProfileScreen(PublicProfileState(p.userId, p, loading = false, signedIn = true), { event(it.toString()) }, pageActions = {})
    },
    profiles("public.anonymous", "Public profile, signed out", "People", "PublicProfile") {
        val p = ProfilesSamples.person(relationship = RelationshipStatus.Unknown(null))
        PublicProfileScreen(PublicProfileState(p.userId, p, loading = false, signedIn = false), { event(it.toString()) }, pageActions = {})
    },
    profiles("public.not_found", "Public profile, no profile", "People", "PublicProfile") {
        PublicProfileScreen(PublicProfileState("ghost", loading = false, loadError = refusal("error.404.profile_not_found", 404)), { event(it.toString()) }, pageActions = {})
    },
)

/** What every profiles story needs around it: the profiles strings and error texts, en and ru. */
val profilesStoryEnvironment: StoryEnvironment = StoryEnvironment(
    bundles = listOf(ProfilesUiBundle),
    locales = listOf("en", "ru"),
    provide = { content -> CompositionLocalProvider(LocalStapelErrorText provides ErrorTextResolver(profilesErrorCatalogs())) { content() } },
)

/** The profiles catalogue of the showcase gallery. */
val storyCatalog: StoryCatalog = StoryCatalog("profiles", "Profiles", profilesStories, profilesStoryEnvironment)
