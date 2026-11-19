package stapel.network.di

import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module
import org.koin.dsl.onClose
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.network.HttpTransport
import stapel.network.NetworkConfig
import stapel.network.NetworkDependencies
import stapel.network.NetworkPlugin
import stapel.network.RequestAuthenticator
import stapel.network.StapelTransport
import stapel.network.VerificationHandler
import stapel.network.platformTransport
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.StapelTelemetry
import stapel.wire.ApiTransport
import kotlin.time.Clock

/** Qualifiers of `stapel-network` bindings. */
public object NetworkQualifiers {
    /** The bare [ApiTransport]: no authenticator, no verification, no plug-ins. What a token refresher uses. */
    public val Bare: Qualifier = StapelQualifiers.module("network.bare")
}

/**
 * Binds [ApiTransport] (authenticated: picks up a [RequestAuthenticator], a [VerificationHandler] and
 * every contributed [NetworkPlugin] when other modules provide them — adding `sessionModule()` is what
 * turns bearer auth on, adding `captchaModule()` turns captcha on) and the bare transport under
 * [NetworkQualifiers.Bare]. Needs `runtimeModule()`.
 */
public fun networkModule(environment: StapelEnvironment, config: NetworkConfig = NetworkConfig()): Module = module {
    single<StapelEnvironment> { environment }
    single<HttpTransport> { platformTransport(config.pins) } onClose { it?.close() }
    single<NetworkDependencies> {
        NetworkDependencies(
            environment = get(),
            clock = get<Clock>(),
            ids = get<IdGenerator>(),
            locale = getOrNull<LocaleProvider>(),
            connectivity = getOrNull<Connectivity>(),
            logger = getOrNull<StapelLogger>() ?: NoopLogger,
            telemetry = getOrNull<StapelTelemetry>() ?: StapelTelemetry.Noop,
        )
    }
    single<ApiTransport> {
        StapelTransport(
            deps = get(),
            config = config,
            authenticator = getOrNull<RequestAuthenticator>() ?: RequestAuthenticator.None,
            verification = getOrNull<VerificationHandler>(),
            plugins = getAll<NetworkPlugin>(),
            transport = get(),
        )
    }
    single<ApiTransport>(NetworkQualifiers.Bare) { StapelTransport(deps = get(), config = config, transport = get()) }
}
