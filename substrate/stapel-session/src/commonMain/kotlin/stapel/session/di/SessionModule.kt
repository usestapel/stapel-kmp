package stapel.session.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.di.SessionScopeHandle
import stapel.di.StapelQualifiers
import stapel.di.bindSessionScope
import stapel.network.RequestAuthenticator
import stapel.runtime.Connectivity
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.StapelTelemetry
import stapel.session.LogoutHook
import stapel.session.SessionConfig
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenRefresher
import stapel.session.vault.TokenVault
import stapel.session.vault.TokenVaultConfig
import stapel.session.vault.platformTokenVault
import kotlin.time.Clock

/**
 * Binds [SessionManager] (with every contributed [LogoutHook] registered), the [RequestAuthenticator]
 * that turns bearer auth on in `networkModule()`, the platform [TokenVault] and the Koin
 * [SessionScopeHandle] (a session scope per generation, closed when the session ends). Needs
 * `runtimeModule()` and a [TokenRefresher] binding (the authenticating brick's module provides it).
 */
public fun sessionModule(config: SessionConfig = SessionConfig(), vault: TokenVaultConfig = TokenVaultConfig()): Module = module {
    single<TokenVault> { platformTokenVault(get(), get(), vault) }
    single<SessionManager> {
        SessionManager(
            SessionDependencies(
                vault = get(),
                scope = get<CoroutineScope>(StapelQualifiers.AppScope),
                clock = get<Clock>(),
                connectivity = getOrNull<Connectivity>(),
                logger = getOrNull<StapelLogger>() ?: NoopLogger,
                telemetry = getOrNull<StapelTelemetry>() ?: StapelTelemetry.Noop,
            ),
            refresher = get<TokenRefresher>(),
            config = config,
        ).also { manager -> getAll<LogoutHook>().forEach { manager.registerLogoutHook(it) } }
    }
    single<RequestAuthenticator> { get<SessionManager>().authenticator }
    single<SessionScopeHandle> {
        val generations = get<SessionManager>().state.map { state ->
            when (state) {
                is SessionState.Authenticated -> state.generation
                is SessionState.Anonymous -> state.generation
                is SessionState.Unauthenticated, SessionState.Initializing -> null
            }
        }
        getKoin().bindSessionScope(generations, get(StapelQualifiers.AppScope))
    }
}
