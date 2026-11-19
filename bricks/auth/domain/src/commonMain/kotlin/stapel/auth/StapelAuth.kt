package stapel.auth

import kotlinx.coroutines.CoroutineScope
import stapel.auth.api.AuthApi
import stapel.auth.api.model.SignupAttribution
import stapel.auth.model.AuthMethodId
import stapel.auth.platform.AuthPlatform
import stapel.auth.repo.AccountRepository
import stapel.auth.repo.CapabilitiesRepository
import stapel.auth.repo.SessionsRepository
import stapel.auth.session.AuthSession
import stapel.auth.session.DefaultAuthSession
import stapel.auth.session.StepUpController
import stapel.cache.StapelCache
import stapel.runtime.systemClock
import stapel.session.SessionManager
import kotlin.time.Clock

/** How guests are handled. */
public enum class GuestPolicy {
    /** No guest sessions are created by the library. */
    Off,

    /** A guest session is created when a flow asks for one ([AuthSession.ensureGuest], the guest presenter). */
    OnDemand,
}

/**
 * Project-level auth settings. Plain values; read at call time where they are lambdas.
 *
 * @property guest guest policy.
 * @property methods client-side fence over what the server enables (null = everything the client can
 *   complete); a backend misconfiguration cannot surface a method the app was not built for.
 * @property attribution forwarded on calls that can register an account.
 */
public class AuthConfig(
    public val guest: GuestPolicy = GuestPolicy.OnDemand,
    public val methods: Set<AuthMethodId>? = null,
    public val attribution: () -> SignupAttribution? = { null },
)

/**
 * The auth library's façade: everything a project wires, built from plain values (`di.authModule()`
 * is one way to call it).
 *
 * @param api the generated client on the AUTHENTICATED transport (bearer attached, refresh on 401,
 *   step-up through [stepUp], captcha plug-in when installed).
 * @param sessions the app's session manager; its `TokenRefresher` must be [stapel.auth.session.AuthTokenRefresher]
 *   over the BARE transport.
 * @param cache the app's cache; its partitions must follow the session (`CacheSessionBinding`), which
 *   is what wipes the profile and the sessions list on every sign-out.
 * @param scope application scope.
 * @param deviceId the installation id sent as `device_id` (guest dedup, per-device OTP cooldown).
 * @param stepUp the verification handler installed in the authenticated transport, when there is one.
 * @param clock time source for challenge expiry, resend timers and freshness stamps: the app's runtime
 *   clock (`authModule()` passes the graph's `Clock`); the default is the runtime's system clock.
 */
public class StapelAuth(
    /** The generated client on the authenticated transport. */
    public val api: AuthApi,
    sessions: SessionManager,
    cache: StapelCache,
    scope: CoroutineScope,
    /** Project settings. */
    public val config: AuthConfig = AuthConfig(),
    /** Optional platform capabilities. */
    public val platform: AuthPlatform = AuthPlatform(),
    deviceId: suspend () -> String? = { null },
    /** Step-up verification, when installed. */
    public val stepUp: StepUpController? = null,
    /** Time source for challenge expiry, resend timers and freshness stamps. */
    public val clock: Clock = systemClock(),
) {
    internal val sessionImpl: DefaultAuthSession = DefaultAuthSession(api, sessions, scope, clock, deviceId)
    internal val deviceIdSource: suspend () -> String? = deviceId

    /** Identity state, sign-out, guest. */
    public val session: AuthSession get() = sessionImpl

    /** `GET /capabilities/`. */
    public val capabilities: CapabilitiesRepository = CapabilitiesRepository(api, cache)

    /** `GET /me/`. */
    public val account: AccountRepository = AccountRepository(api, cache)

    /** Sessions and devices of the signed-in user. */
    public val devices: SessionsRepository = SessionsRepository(api, cache)
}
