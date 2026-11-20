package stapel.profiles.ui.di

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.koin.core.module.Module
import stapel.app.di.BrickContext
import stapel.app.di.StapelBrick
import stapel.i18n.I18nBundle
import stapel.mvi.compose.PresenterFactory
import stapel.navigation.compose.NavContribution
import stapel.profiles.StapelProfiles
import stapel.profiles.di.AvatarUpload
import stapel.profiles.di.profilesModule
import stapel.profiles.ui.AvatarPicker
import stapel.profiles.ui.DefaultProfilesScreens
import stapel.profiles.ui.LocalProfilesScreens
import stapel.profiles.ui.ProfileConnections
import stapel.profiles.ui.ProfileContacts
import stapel.profiles.ui.ProfileLanguage
import stapel.profiles.ui.ProfileNotifications
import stapel.profiles.ui.ProfileSettings
import stapel.profiles.ui.ProfilesPresenterFactory
import stapel.profiles.ui.ProfilesScreens
import stapel.profiles.ui.ProfilesUiBundle
import stapel.profiles.ui.PublicProfile
import stapel.profiles.ui.generated.StapelBuildInfo
import stapel.profiles.ui.profilesErrorCatalogs
import stapel.profiles.ui.profilesNavContribution
import stapel.result.text.ErrorCatalog

/**
 * The profiles brick, for `stapelApp { bricks(AuthBrick(), ProfilesBrick()) }`: [profilesModule] (the
 * cache is bound to the session by the app builder, never here), the six destinations bound to the
 * app's [StapelProfiles] (the settings container lists every destination installed under it, so
 * `auth.security` appears there when the auth brick is installed), the profiles strings and error
 * texts, and the profiles presenters. Needs no capability from the app. It owns no session: install
 * it with a sign-in owner (the auth brick), which binds the session's token refresher.
 *
 * @param avatarUpload which uploader the domain binds: by default the app's `MediaUploader` (install
 *   stapel-cdn's with `modules(cdnModule())`); without one, no avatar change is offered.
 * @param picker the app's picture picker — the one wiring point for avatar upload; null = no avatar
 *   change is offered.
 * @param screens the screen table (replace single screens by delegation to [DefaultProfilesScreens]).
 */
public class ProfilesBrick(
    private val avatarUpload: AvatarUpload = AvatarUpload.Media,
    private val picker: AvatarPicker? = null,
    private val screens: ProfilesScreens = DefaultProfilesScreens,
) : StapelBrick {
    /** `profiles`, the backend library key. */
    override val key: String = "profiles"

    /** [ProfilesUiBundle] (en, ru). */
    override val strings: List<I18nBundle> = listOf(ProfilesUiBundle)

    /** The contract's English texts and the Russian catalog ([profilesErrorCatalogs]). */
    override val errorCatalogs: List<ErrorCatalog> = profilesErrorCatalogs()

    /** `profilesModule(avatarUpload)`. */
    override fun modules(): List<Module> = listOf(profilesModule(avatarUpload))

    /** The six destinations over the app's [StapelProfiles], drawn through [screens]. */
    override fun navigation(context: BrickContext): NavContribution {
        fun profiles(): StapelProfiles = context.koin.get()
        return profilesNavContribution(
            version = StapelBuildInfo.VERSION,
            settings = { CompositionLocalProvider(LocalProfilesScreens provides screens) { ProfileSettings(remember(context) { profiles() }, picker) } },
            language = { CompositionLocalProvider(LocalProfilesScreens provides screens) { ProfileLanguage(remember(context) { profiles() }) } },
            notifications = { CompositionLocalProvider(LocalProfilesScreens provides screens) { ProfileNotifications(remember(context) { profiles() }) } },
            contacts = { CompositionLocalProvider(LocalProfilesScreens provides screens) { ProfileContacts(remember(context) { profiles() }) } },
            connections = { CompositionLocalProvider(LocalProfilesScreens provides screens) { ProfileConnections(remember(context) { profiles() }) } },
            publicProfile = { route -> CompositionLocalProvider(LocalProfilesScreens provides screens) { PublicProfile(remember(context) { profiles() }, route) } },
        )
    }

    /** [ProfilesPresenterFactory] over the app's [StapelProfiles], chained before [next]. */
    override fun presenters(context: BrickContext, next: PresenterFactory): PresenterFactory =
        ProfilesPresenterFactory(context.koin.get<StapelProfiles>(), next)
}
