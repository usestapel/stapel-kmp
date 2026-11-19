package stapel.auth.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import stapel.auth.AuthConfig
import stapel.auth.StapelAuth
import stapel.auth.api.AuthApi
import stapel.auth.api.AuthApiClient
import stapel.auth.model.SignInOutcome
import stapel.auth.platform.AuthPlatform
import stapel.auth.presentation.GuestPresenter
import stapel.auth.presentation.LoginChallengePresenter
import stapel.auth.presentation.MethodChooserPresenter
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.PasswordRegisterPresenter
import stapel.auth.presentation.PasswordSignInPresenter
import stapel.auth.presentation.SessionsPresenter
import stapel.auth.presentation.SignOutPresenter
import stapel.auth.session.AuthTokenRefresher
import stapel.auth.session.StepUpController
import stapel.cache.ManagedStapelCache
import stapel.cache.session.CacheSessionBinding
import stapel.di.StapelQualifiers
import stapel.mvi.PresenterContext
import stapel.network.VerificationHandler
import stapel.network.di.NetworkQualifiers
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.session.InstallationIdentity
import stapel.session.SessionManager
import stapel.session.TokenRefresher
import stapel.session.platformInstallationIdentity
import stapel.wire.ApiTransport

/**
 * The auth library's Koin module. Binds:
 *
 * - [AuthApi] on the authenticated [ApiTransport];
 * - [TokenRefresher] = [AuthTokenRefresher] on the BARE transport (`NetworkQualifiers.Bare`), which
 *   `sessionModule()` needs;
 * - [StepUpController], also as the [VerificationHandler] `networkModule()` picks up;
 * - [InstallationIdentity] (the `device_id`), [StapelAuth], and every presenter as a factory taking a
 *   [PresenterContext] (`LoginChallengePresenter` also takes the [SignInOutcome] to answer);
 * - when [bindCacheToSession] is true, a [CacheSessionBinding] (created at start) so the cache
 *   partition follows the session and is wiped on every sign-out. Leave it false only when the app
 *   creates that binding itself.
 *
 * Needs `runtimeModule()`, `networkModule()`, `sessionModule()` and a [ManagedStapelCache] binding.
 */
public fun authModule(
    config: AuthConfig = AuthConfig(),
    platform: AuthPlatform = AuthPlatform(),
    bindCacheToSession: Boolean = true,
): Module = module {
    single<AuthApi> { AuthApiClient(get<ApiTransport>()) }
    single<TokenRefresher> { AuthTokenRefresher(AuthApiClient(get<ApiTransport>(NetworkQualifiers.Bare))) }
    single { StepUpController { get<AuthApi>() } } bind VerificationHandler::class
    single<InstallationIdentity> {
        platformInstallationIdentity(get<PlatformContext>(), get<StapelDispatchers>(), get<IdGenerator>(), namespace = "stapel.auth")
    }
    single {
        val identity = get<InstallationIdentity>()
        StapelAuth(
            api = get(),
            sessions = get<SessionManager>(),
            cache = get<ManagedStapelCache>(),
            scope = get<CoroutineScope>(StapelQualifiers.AppScope),
            config = config,
            platform = platform,
            deviceId = { identity.deviceId() },
            stepUp = get(),
        )
    }
    if (bindCacheToSession) {
        single(createdAtStart = true) {
            CacheSessionBinding(get<ManagedStapelCache>(), get<SessionManager>(), get<CoroutineScope>(StapelQualifiers.AppScope))
        }
    }
    factory { (ctx: PresenterContext) -> MethodChooserPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> OtpSignInPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> PasswordSignInPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> PasswordRegisterPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> GuestPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> SignOutPresenter(get(), ctx) }
    factory { (ctx: PresenterContext) -> SessionsPresenter(get(), ctx) }
    factory { (ctx: PresenterContext, outcome: SignInOutcome) -> LoginChallengePresenter(get(), ctx, outcome) }
}
