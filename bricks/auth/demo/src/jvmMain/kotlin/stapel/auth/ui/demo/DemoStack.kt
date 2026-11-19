package stapel.auth.ui.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import stapel.auth.AuthConfig
import stapel.auth.GuestPolicy
import stapel.auth.StapelAuth
import stapel.auth.api.AuthApi
import stapel.auth.api.AuthApiClient
import stapel.auth.session.AuthTokenRefresher
import stapel.auth.session.StepUpController
import stapel.auth.ui.AuthNavBridge
import stapel.cache.ManagedStapelCache
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.cache.session.CacheSessionBinding
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.network.NetworkDependencies
import stapel.network.StapelTransport
import stapel.runtime.randomIdGenerator
import stapel.session.SessionConfig
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.vault.InMemoryTokenVault
import stapel.wire.WireJson
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The real client stack against a stapel-auth backend at [baseUrl] (prototype posture: cleartext to a
 * local host is allowed): the transport pipeline, the session manager with an in-memory vault (the
 * demo forgets its session on exit — a desktop app wires a file vault), the cache bound to the
 * session, and the auth façade. One instance is one app installation.
 */
class DemoStack(baseUrl: String, val name: String = "demo", proactiveWindow: Duration = 1.seconds) : AutoCloseable {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault = InMemoryTokenVault()
    private val environment = StapelEnvironment.single(
        name = "local",
        baseUrl = baseUrl,
        posture = Posture.Prototype,
        client = ClientInfo(
            appId = "stapel.auth.demo",
            appVersion = "0.0.0",
            build = "demo",
            platform = ClientPlatform.Desktop,
            osName = System.getProperty("os.name"),
            osVersion = System.getProperty("os.version"),
            deviceModel = name,
            sdkVersion = "demo",
        ),
    )
    private val deps = NetworkDependencies(environment, Clock.System, randomIdGenerator())
    private val bareApi: AuthApi = AuthApiClient(StapelTransport(deps))
    val sessions: SessionManager = SessionManager(
        SessionDependencies(vault, scope, Clock.System),
        AuthTokenRefresher(bareApi),
        SessionConfig(proactiveRefreshWindow = proactiveWindow),
    )
    private var authedApi: AuthApi? = null
    private val stepUp = StepUpController { checkNotNull(authedApi) }
    val api: AuthApi = AuthApiClient(StapelTransport(deps, authenticator = sessions.authenticator, verification = stepUp)).also { authedApi = it }
    val cache: ManagedStapelCache = StapelCache(StapelCacheConfig(json = WireJson, scope = scope, clock = Clock.System))
    private val binding = CacheSessionBinding(cache, sessions, scope)
    private val deviceId = "demo-device-" + UUID.randomUUID()
    val auth = StapelAuth(api, sessions, cache, scope, AuthConfig(guest = GuestPolicy.OnDemand), deviceId = { deviceId }, stepUp = stepUp)
    val navBridge = AuthNavBridge(auth, scope)

    override fun close() {
        binding.close()
        scope.cancel()
    }
}
