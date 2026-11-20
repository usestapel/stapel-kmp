package stapel.profiles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import stapel.navigation.DestinationId
import stapel.navigation.NavIcon
import stapel.profiles.presentation.ConnectionsIntent
import stapel.profiles.presentation.ConnectionsState
import stapel.profiles.presentation.ContactsIntent
import stapel.profiles.presentation.ContactsState
import stapel.profiles.presentation.LanguageCatalogueState
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsState
import stapel.profiles.presentation.PublicProfileIntent
import stapel.profiles.presentation.PublicProfileState

/**
 * One row of the settings container: a destination installed under `profiles.settings` (this
 * library's sections, `auth.security`, any other library's).
 *
 * @property id the destination.
 * @property labelKey its label's string key (resolved by the app's string table).
 * @property icon its neutral icon.
 */
public data class SettingsSection(val id: DestinationId, val labelKey: String, val icon: NavIcon)

/**
 * The screen table: every profiles screen as a stateless composable of its presenter's state and
 * callbacks. A project replaces one screen by delegation and keeps every flow:
 *
 * ```kotlin
 * object MyScreens : ProfilesScreens by DefaultProfilesScreens {
 *     @Composable override fun Contacts(state: ContactsState, onIntent: (ContactsIntent) -> Unit) { MyContacts(state, onIntent) }
 * }
 * ProfilesBrick(screens = MyScreens)
 * ```
 */
@Stable
public interface ProfilesScreens {
    /**
     * The settings container: avatar, display name, theme, and a row per section installed under it.
     *
     * @param onPickAvatar opens the app's picture picker; null = no avatar upload offered.
     */
    @Composable
    public fun Settings(
        state: ProfileSettingsState,
        sections: List<SettingsSection>,
        onIntent: (ProfileSettingsIntent) -> Unit,
        onOpen: (DestinationId) -> Unit,
        onPickAvatar: (() -> Unit)?,
    )

    /** App language and translation. */
    @Composable
    public fun Language(state: ProfileSettingsState, catalogue: LanguageCatalogueState, onIntent: (ProfileSettingsIntent) -> Unit)

    /** The four notification switches. */
    @Composable
    public fun Notifications(state: ProfileSettingsState, onIntent: (ProfileSettingsIntent) -> Unit)

    /** The person's phone numbers. */
    @Composable
    public fun Contacts(state: ContactsState, onIntent: (ContactsIntent) -> Unit)

    /** Followers / following / blocked. */
    @Composable
    public fun Connections(state: ConnectionsState, onIntent: (ConnectionsIntent) -> Unit, onOpenPerson: (String) -> Unit)

    /**
     * A person's public profile.
     *
     * @param pageActions renders the `page.actions` slot (other libraries' actions on this person).
     */
    @Composable
    public fun PublicProfile(state: PublicProfileState, onIntent: (PublicProfileIntent) -> Unit, pageActions: @Composable () -> Unit)
}

/** The default screens of this library. */
public object DefaultProfilesScreens : ProfilesScreens {
    @Composable
    override fun Settings(
        state: ProfileSettingsState,
        sections: List<SettingsSection>,
        onIntent: (ProfileSettingsIntent) -> Unit,
        onOpen: (DestinationId) -> Unit,
        onPickAvatar: (() -> Unit)?,
    ) {
        ProfileSettingsScreen(state, sections, onIntent, onOpen, onPickAvatar)
    }

    @Composable
    override fun Language(state: ProfileSettingsState, catalogue: LanguageCatalogueState, onIntent: (ProfileSettingsIntent) -> Unit) {
        LanguageScreen(state, catalogue, onIntent)
    }

    @Composable
    override fun Notifications(state: ProfileSettingsState, onIntent: (ProfileSettingsIntent) -> Unit) {
        NotificationsScreen(state, onIntent)
    }

    @Composable
    override fun Contacts(state: ContactsState, onIntent: (ContactsIntent) -> Unit) {
        ContactsScreen(state, onIntent)
    }

    @Composable
    override fun Connections(state: ConnectionsState, onIntent: (ConnectionsIntent) -> Unit, onOpenPerson: (String) -> Unit) {
        ConnectionsScreen(state, onIntent, onOpenPerson)
    }

    @Composable
    override fun PublicProfile(state: PublicProfileState, onIntent: (PublicProfileIntent) -> Unit, pageActions: @Composable () -> Unit) {
        PublicProfileScreen(state, onIntent, pageActions)
    }
}

/** The screen table the bindings draw through; [DefaultProfilesScreens] unless a project provides its own. */
public val LocalProfilesScreens: ProvidableCompositionLocal<ProfilesScreens> = staticCompositionLocalOf { DefaultProfilesScreens }

/** Stable test tags of the default screens (UI tests and end-to-end runs find nodes by them). */
public object ProfilesTestTags {
    /** The settings screen root. */
    public const val SETTINGS: String = "profiles.settings"

    /** The avatar on the settings screen. */
    public const val AVATAR: String = "profiles.settings.avatar"

    /** "Change avatar". */
    public const val AVATAR_CHANGE: String = "profiles.settings.avatar.change"

    /** The display name field. */
    public const val DISPLAY_NAME: String = "profiles.settings.display_name"

    /** Save the display name. */
    public const val DISPLAY_NAME_SAVE: String = "profiles.settings.display_name.save"

    /** The theme control. */
    public const val THEME: String = "profiles.settings.theme"

    /** A section row: `profiles.section.<destination id>`. */
    public const val SECTION: String = "profiles.section"

    /** An error banner of a screen. */
    public const val ERROR: String = "profiles.error"

    /** The language screen root. */
    public const val LANGUAGE: String = "profiles.language"

    /** A language choice: `profiles.language.<code>`; the device choice is `profiles.language.device`. */
    public const val LANGUAGE_CHOICE: String = "profiles.language"

    /** The auto-translate switch. */
    public const val AUTO_TRANSLATE: String = "profiles.language.auto_translate"

    /** The notifications screen root. */
    public const val NOTIFICATIONS: String = "profiles.notifications"

    /** A switch: `profiles.notifications.<category>.<channel>` (lower case). */
    public const val NOTIFICATION_SWITCH: String = "profiles.notifications"

    /** The phone numbers screen root. */
    public const val CONTACTS: String = "profiles.contacts"

    /** The new number field. */
    public const val CONTACT_NUMBER: String = "profiles.contacts.number"

    /** "Add number". */
    public const val CONTACT_ADD: String = "profiles.contacts.add"

    /** A number row: `profiles.contacts.row.<id>`. */
    public const val CONTACT_ROW: String = "profiles.contacts.row"

    /** The SMS code field. */
    public const val CONTACT_CODE: String = "profiles.contacts.code"

    /** The connections screen root. */
    public const val CONNECTIONS: String = "profiles.connections"

    /** A person row: `profiles.person.<user id>`. */
    public const val PERSON: String = "profiles.person"

    /** The public profile root. */
    public const val PUBLIC: String = "profiles.public"

    /** A relationship action button: `profiles.public.action.<action>` (lower case). */
    public const val PUBLIC_ACTION: String = "profiles.public.action"
}
