package stapel.profiles.ui

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.SlotId
import stapel.navigation.Surface
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.ScreenScope
import stapel.navigation.compose.navContribution

/** Ids of what this library contributes to navigation; equal to the web pair's manifest ids. */
public object ProfilesNavIds {
    /** The settings container (web `settings`); `auth.security` and this library's sections hang from it. */
    public val Settings: DestinationId = DestinationId("profiles.settings")

    /** Language, under settings. */
    public val Language: DestinationId = DestinationId("profiles.language")

    /** Notifications, under settings. */
    public val Notifications: DestinationId = DestinationId("profiles.notifications")

    /** Phone numbers, under settings. */
    public val Contacts: DestinationId = DestinationId("profiles.contacts")

    /** Followers / following / blocked. */
    public val Connections: DestinationId = DestinationId("profiles.connections")

    /** A person's public profile, `/u/:userId`. */
    public val Public: DestinationId = DestinationId("profiles.public")

    /** The page slot of the public profile other libraries place actions into (message, report, …). */
    public val PageActions: SlotId = SlotId("page.actions")
}

/** Route of `profiles.settings`. */
@Serializable
public data object SettingsRoute : Route

/** Route of `profiles.language`. */
@Serializable
public data object LanguageRoute : Route

/** Route of `profiles.notifications`. */
@Serializable
public data object NotificationsRoute : Route

/** Route of `profiles.contacts`. */
@Serializable
public data object ContactsRoute : Route

/** Route of `profiles.connections`. */
@Serializable
public data object ConnectionsRoute : Route

/**
 * Route of `profiles.public`.
 *
 * @property userId whose profile.
 */
@Serializable
public data class PublicProfileRoute(val userId: String) : Route

/** Maven coordinates of this library without version (the manifest's `package`). */
public const val PROFILES_UI_ARTIFACT: String = "io.github.usestapel:stapel-profiles-ui"

private val PublicCodec: PathCodec<PublicProfileRoute> = PathCodec(
    parse = { path, _ -> path["userId"]?.let(::PublicProfileRoute) },
    format = { r -> RouteAddress(path = mapOf("userId" to r.userId)) },
)

/**
 * This library's navigation contribution. Ids, paths, placement, order, visibility and audience equal
 * the web pair's `nav-manifest.json`: `profiles.settings` (`settings`, top, members, order 90) with
 * `profiles.language` (20), `profiles.notifications` (30) and `profiles.contacts` (40) under it,
 * `profiles.connections` (`connections`, top, 91) and `profiles.public` (`/u/:userId`, public, 92,
 * page slot `page.actions`). The contents are the stateful bindings the app passes in.
 *
 * @param version this library's version (the manifest's `version`).
 */
public fun profilesNavContribution(
    version: String,
    settings: @Composable ScreenScope.(SettingsRoute) -> Unit,
    language: @Composable ScreenScope.(LanguageRoute) -> Unit,
    notifications: @Composable ScreenScope.(NotificationsRoute) -> Unit,
    contacts: @Composable ScreenScope.(ContactsRoute) -> Unit,
    connections: @Composable ScreenScope.(ConnectionsRoute) -> Unit,
    publicProfile: @Composable ScreenScope.(PublicProfileRoute) -> Unit,
): NavContribution = navContribution(PROFILES_UI_ARTIFACT, version) {
    destination<SettingsRoute>(
        id = ProfilesNavIds.Settings.value,
        path = "settings",
        labelKey = ProfilesStrings.NavSettings.key,
        icon = NavIcons.User,
        codec = PathCodec.constant(SettingsRoute),
        placement = Placement.Top,
        menuVisibleDefault = true,
        requiresAuth = true,
        order = 90,
        content = settings,
    )
    destination<LanguageRoute>(
        id = ProfilesNavIds.Language.value,
        path = "language",
        labelKey = ProfilesStrings.NavLanguage.key,
        icon = NavIcons.Message,
        codec = PathCodec.constant(LanguageRoute),
        placement = Placement.Submenu(ProfilesNavIds.Settings),
        menuVisibleDefault = false,
        requiresAuth = true,
        order = 20,
        content = language,
    )
    destination<NotificationsRoute>(
        id = ProfilesNavIds.Notifications.value,
        path = "notifications",
        labelKey = ProfilesStrings.NavNotifications.key,
        icon = NavIcons.Bell,
        codec = PathCodec.constant(NotificationsRoute),
        placement = Placement.Submenu(ProfilesNavIds.Settings),
        menuVisibleDefault = false,
        requiresAuth = true,
        order = 30,
        content = notifications,
    )
    destination<ContactsRoute>(
        id = ProfilesNavIds.Contacts.value,
        path = "contacts",
        labelKey = ProfilesStrings.NavContacts.key,
        icon = NavIcons.Message,
        codec = PathCodec.constant(ContactsRoute),
        placement = Placement.Submenu(ProfilesNavIds.Settings),
        menuVisibleDefault = true,
        requiresAuth = true,
        order = 40,
        content = contacts,
    )
    destination<ConnectionsRoute>(
        id = ProfilesNavIds.Connections.value,
        path = "connections",
        labelKey = ProfilesStrings.NavConnections.key,
        icon = NavIcons.Heart,
        codec = PathCodec.constant(ConnectionsRoute),
        placement = Placement.Top,
        menuVisibleDefault = true,
        requiresAuth = true,
        order = 91,
        content = connections,
    )
    destination<PublicProfileRoute>(
        id = ProfilesNavIds.Public.value,
        path = "/u/:userId",
        labelKey = ProfilesStrings.NavPublicProfile.key,
        icon = NavIcons.Profile,
        codec = PublicCodec,
        placement = Placement.Top,
        menuVisibleDefault = false,
        requiresAuth = false,
        surface = Surface.Public,
        order = 92,
        pageSlots = setOf(ProfilesNavIds.PageActions),
        content = publicProfile,
    )
}
