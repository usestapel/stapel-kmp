package stapel.profiles.di

import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cache.ManagedStapelCache
import stapel.mvi.PresenterContext
import stapel.profiles.StapelProfiles
import stapel.profiles.api.ProfilesApi
import stapel.profiles.api.ProfilesApiClient
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.avatar.CdnAvatarUploader
import stapel.profiles.presentation.ConnectionsPresenter
import stapel.profiles.presentation.ContactsPresenter
import stapel.profiles.presentation.LanguageCataloguePresenter
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.profiles.presentation.PublicProfilePresenter
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.wire.ApiTransport

/** Which avatar uploader [profilesModule] binds. */
public enum class AvatarUpload {
    /** No upload: the settings screen offers none. */
    Off,

    /** stapel-cdn's avatar upload on the authenticated transport ([CdnAvatarUploader]). */
    Cdn,
}

/**
 * The profiles library's Koin module. Binds [ProfilesApi] on the authenticated [ApiTransport], the
 * [AvatarUploader] chosen by [avatarUpload], [StapelProfiles] (its viewer read from the
 * [SessionManager]) and every presenter as a factory taking a [PresenterContext]
 * ([PublicProfilePresenter] also takes the user id and whether a viewer is signed in).
 *
 * Never binds the cache to the session: the app (or the auth brick) does that once.
 * Needs `networkModule()`, `sessionModule()` and a [ManagedStapelCache] binding.
 */
public fun profilesModule(avatarUpload: AvatarUpload = AvatarUpload.Cdn): Module = module {
    single<ProfilesApi> { ProfilesApiClient(get<ApiTransport>()) }
    single<AvatarUploader> {
        when (avatarUpload) {
            AvatarUpload.Off -> AvatarUploader.None
            AvatarUpload.Cdn -> CdnAvatarUploader(get<ApiTransport>())
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
