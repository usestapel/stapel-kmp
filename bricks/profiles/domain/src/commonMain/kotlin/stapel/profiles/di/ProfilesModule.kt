package stapel.profiles.di

import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cache.ManagedStapelCache
import stapel.di.StapelQualifiers
import stapel.mvi.PresenterContext
import stapel.profiles.StapelProfiles
import stapel.profiles.api.ProfilesApi
import stapel.profiles.api.ProfilesApiClient
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.avatar.MediaAvatarUploader
import stapel.profiles.internal.AppLanguageSync
import stapel.profiles.presentation.ConnectionsPresenter
import stapel.profiles.presentation.ContactsPresenter
import stapel.profiles.presentation.LanguageCataloguePresenter
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.profiles.presentation.PublicProfilePresenter
import stapel.runtime.LocaleProvider
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.wire.ApiTransport
import stapel.wire.MediaUploader

/** Which avatar uploader [profilesModule] binds. */
public enum class AvatarUpload {
    /** No upload: the settings screen offers none. */
    Off,

    /**
     * Through the app's [MediaUploader] ([MediaAvatarUploader]); stapel-cdn's when the app installs
     * `cdnModule()`. Without a [MediaUploader] in the graph this is [Off].
     */
    Media,
}

/**
 * The profiles library's Koin module. Binds [ProfilesApi] on the authenticated [ApiTransport], the
 * [AvatarUploader] chosen by [avatarUpload], [StapelProfiles] (its viewer read from the
 * [SessionManager]) and every presenter as a factory taking a [PresenterContext]
 * ([PublicProfilePresenter] also takes the user id and whether a viewer is signed in).
 *
 * Starts with the app: the app's locale follows the signed-in person's app language
 * (`LocaleProvider.setAppLocale`; "follow the device" hands it back), so a choice in the language
 * screen switches the app's strings and layout direction at once and survives a restart.
 *
 * Never binds the cache to the session: the app (or the auth brick) does that once.
 * Needs `runtimeModule()`, `networkModule()`, `sessionModule()` and a [ManagedStapelCache] binding.
 */
public fun profilesModule(avatarUpload: AvatarUpload = AvatarUpload.Media): Module = module {
    single<ProfilesApi> { ProfilesApiClient(get<ApiTransport>()) }
    single<AvatarUploader> {
        when (avatarUpload) {
            AvatarUpload.Off -> AvatarUploader.None
            AvatarUpload.Media -> getOrNull<MediaUploader>()?.let(::MediaAvatarUploader) ?: AvatarUploader.None
        }
    }
    single {
        val sessions: StateFlow<SessionState> = get<SessionManager>().state
        StapelProfiles(
            api = get(),
            cache = get<ManagedStapelCache>(),
            avatarUploader = get(),
            viewer = { sessions.value.accountId() },
        )
    }
    // The app follows the person's app language (and back to the device): from app start, eagerly.
    single(createdAtStart = true) {
        AppLanguageSync(get<StapelProfiles>().me, get<SessionManager>().state, get<LocaleProvider>(), get(StapelQualifiers.AppScope))
    }
    factory { (ctx: PresenterContext) -> ProfileSettingsPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> LanguageCataloguePresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> ContactsPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> ConnectionsPresenter(get(), ctx) }
    factory { (ctx: PresenterContext, userId: String, signedIn: Boolean) -> PublicProfilePresenter(get(), ctx, userId, signedIn) }
}

private fun SessionState.accountId(): String? = when (this) {
    is SessionState.Authenticated -> accountId
    is SessionState.Anonymous -> accountId
    else -> null
}
