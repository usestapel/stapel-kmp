package stapel.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import stapel.network.RefreshVerdict
import stapel.network.RequestAuthenticator
import stapel.runtime.Connectivity
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.StapelTelemetry
import stapel.session.vault.TokenVault
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one owner of the session's tokens. Rules it keeps (each has a test):
 *
 * 1. Born [SessionState.Initializing]; [restore] settles it from the vault.
 * 2. Single-flight refresh: concurrent callers join one refresh call.
 * 3. Compare before rotating: a 401 for a token that is no longer current is answered with the
 *    current token, without a network call.
 * 4. Three outcomes: rotated → persist, then publish; rejected → teardown; transient → nothing
 *    changes (an unmeasured refresh never signs anybody out).
 * 5. The rotated pair is in the vault before any waiter receives it; a rotation marker is written
 *    before the refresh call so a process killed mid-rotation is detected on the next start.
 * 6. Teardown: tokens dropped → vault cleared → every logout hook (one throwing does not stop the
 *    rest) → state published → [SessionEvent.Ended]. During an explicit [logout], [sessionLost] is a no-op.
 * 6a. An explicit [logout] first runs the farewell hooks ([farewell]) while the tokens are still
 *    current, bounded by [SessionConfig.farewellTimeout]; a refresh rejected meanwhile does not end
 *    the session under another cause — the sign-out that follows ends it as [SessionEnd.Logout].
 * 7. Proactive refresh on the request path when the access token expires within
 *    [SessionConfig.proactiveRefreshWindow], clamped to a quarter of the token's own lifetime
 *    (`exp − iat`) so a short-lived token is not refreshed before every request; skipped while
 *    offline. No timers.
 * 8. An unreadable vault settles [SessionEnd.StorageLost] and is cleared; never a crash, never plaintext.
 * 9. One process owns the session: the vault is not safe for concurrent writers in several processes.
 */
public interface SessionManager {
    /** Current state; starts at [SessionState.Initializing]. */
    public val state: StateFlow<SessionState>

    /** One-shot events. */
    public val events: SharedFlow<SessionEvent>

    /** Reads the vault and settles [SessionState.Initializing]. Idempotent; called once by the app at start. */
    public suspend fun restore()

    /** Suspends until the state is no longer [SessionState.Initializing]. */
    public suspend fun awaitReady(): SessionState

    /** Adopts a freshly issued pair (sign-in, guest, QR, deep link): persists, then publishes a new generation. */
    public suspend fun adopt(tokens: TokenPair, kind: SessionKind)

    /** The access token for the next request (refreshing first when it is about to expire), or null. */
    public suspend fun accessToken(): String?

    /** The current pair (for the logout call, which sends the refresh token), or null. */
    public suspend fun currentTokens(): TokenPair?

    /** Single-flight refresh after a 401 for [failedToken] (null = no specific token). */
    public suspend fun refresh(failedToken: String? = null): RefreshVerdict

    /**
     * Explicit sign-out: the farewell hooks unless [farewell] already ran for this session (rule
     * 6a), then local teardown (rule 6). The server-side revoke is the caller's, best effort, and
     * must read [currentTokens] before calling this.
     */
    public suspend fun logout()

    /**
     * Runs every [FarewellHook] now, concurrently, while the session's tokens are still valid; all
     * of them together get [SessionConfig.farewellTimeout], after which they are cancelled. A caller
     * that revokes the session on the server before [logout] calls this first, so the hooks'
     * authenticated calls are made with a live bearer. At most once per session: [logout] skips it
     * when it already ran. Does nothing without a session.
     */
    public suspend fun farewell()

    /** Registers [hook] for every later explicit sign-out; close the handle to unregister. */
    public fun registerFarewellHook(hook: FarewellHook): AutoCloseable

    /**
     * A module learned independently that the session is dead. Returns whether it ended the
     * session; false while an explicit logout runs or when there is no session.
     */
    public suspend fun sessionLost(cause: SessionEnd): Boolean

    /** Registers [hook] for every later session end; close the handle to unregister. */
    public fun registerLogoutHook(hook: LogoutHook): AutoCloseable

    /** The adapter `stapel-network` consumes. */
    public val authenticator: RequestAuthenticator
}

/** Collaborators of a [SessionManager]. */
public class SessionDependencies(
    /** Where the record lives. */
    public val vault: TokenVault,
    /** Application scope: a refresh runs here so a cancelled caller does not cancel it for the others. */
    public val scope: CoroutineScope,
    /** Time source for expiry and the rotation marker. */
    public val clock: Clock,
    /** When present, proactive refresh is skipped while it reports offline. */
    public val connectivity: Connectivity? = null,
    /** Diagnostics; never receives token material. */
    public val logger: StapelLogger = NoopLogger,
    /** Session telemetry (`rotation_interrupted`, …). */
    public val telemetry: StapelTelemetry = StapelTelemetry.Noop,
)

/** Tunables. */
public data class SessionConfig(
    /**
     * Refresh before sending when the access token expires within this window; [Duration.ZERO]
     * disables. The effective window is at most [PROACTIVE_WINDOW_SHARE] of the token's lifetime
     * (`exp − iat`, when both claims are present): a 5-second token refreshes in its last 1.25 s,
     * not before every request; a 15-minute token keeps the full 30 s.
     */
    val proactiveRefreshWindow: Duration = 30.seconds,
    /**
     * A rotation marker younger than this on start means another process may still be persisting:
     * wait it out once and re-read. An older one means a process died between the server's rotation
     * and our write (the backend's grace for the superseded token is 10 s).
     */
    val rotationHandoffWindow: Duration = 3.seconds,
    /**
     * The budget all [FarewellHook]s share on an explicit sign-out; a hook still running after it is
     * cancelled and the sign-out goes on.
     */
    val farewellTimeout: Duration = 3.seconds,
)

/** Creates the session manager. Plain function: usable without a DI container. */
public fun SessionManager(
    deps: SessionDependencies,
    refresher: TokenRefresher,
    config: SessionConfig = SessionConfig(),
): SessionManager = DefaultSessionManager(deps, refresher, config)

/**
 * Largest share of an access token's lifetime the proactive window may cover: the token is used
 * for at least the first three quarters of its life, and the refresh still lands before expiry
 * with a margin proportional to the lifetime (the request that triggers it is not delayed by a
 * refresh on every call, which a window longer than the lifetime would cause).
 */
public const val PROACTIVE_WINDOW_SHARE: Double = 0.25

