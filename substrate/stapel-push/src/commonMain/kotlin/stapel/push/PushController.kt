package stapel.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import stapel.runtime.AppLifecycle
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.session.InstallationIdentity
import stapel.session.SessionManager

/** Where this installation stands with the backend's device registry. */
public enum class Registration {
    /** Not registered: push off, no session, a guest session, or no registry. */
    Off,

    /** A registration is in flight. */
    Registering,

    /** The current token is registered to the signed-in account. */
    Registered,

    /** Push is wanted but the OS permission does not allow it. */
    Blocked,

    /** The last attempt failed; the next start or sign-in retries. */
    Failed,
}

/**
 * What the push toggle and diagnostics show.
 *
 * @property availability whether the provider can work here.
 * @property permission the OS permission as last read.
 * @property registration the registry state.
 * @property enabled the person's choice, persisted per installation; survives sign-out.
 */
public data class PushState(
    val availability: ProviderAvailability,
    val permission: PushPermission,
    val registration: Registration,
    val enabled: Boolean,
)

/** Opens a deep link; the app passes `stapelApp::openUrl`. Returns false when the link was refused. */
public fun interface PushLinkOpener {
    /** Opens [uri]. */
    public fun open(uri: String): Boolean
}

/**
 * The step before the OS prompt (ruling K5): the app explains why it asks. Returning false keeps
 * the OS prompt unshown — it can be shown only once, so it is never spent on a person who just said no.
 */
public fun interface PushRationale {
    /** Shows the explanation; true when the person wants to continue to the OS prompt. */
    public suspend fun explain(): Boolean
}

/** Called after a tap was routed (the brick adapter marks the feed row read). Failures are logged, never thrown. */
public fun interface PushOpenListener {
    /** [message] was opened. */
    public suspend fun onOpened(message: PushMessage)
}

/**
 * Message → link and message → Android channel. Data, so a brick adapter can supply it.
 *
 * @property linkKeys `data` keys holding the link, first present wins.
 * @property fallbackUri what a tap opens when the message has no link or its link was refused; null = just bring the app forward.
 * @property channels the Android channels to create.
 */
public class PushRouting(
    public val linkKeys: List<String> = listOf("url"),
    public val fallbackUri: String? = null,
    public val channels: List<PushChannelSpec> = listOf(PushChannelSpec("default", "Notifications")),
)

/** When the OS permission is asked. Never at launch. */
public enum class AskPolicy {
    /** Only when the app calls [PushController.enable] (a toggle, an onboarding step). */
    OnDemand,

    /**
     * After the first sign-in of an installation: iOS asks for provisional authorization (no
     * prompt, quiet delivery until the person chooses); Android shows the rationale and the prompt.
     */
    AfterSignIn,
}

/**
 * Options of [PushController].
 *
 * @property ask when the OS permission is asked.
 * @property foreground what the OS shows for a notification while the app is on screen.
 * @property routing link keys, fallback and channels.
 */
public class PushOptions(
    public val ask: AskPolicy = AskPolicy.OnDemand,
    public val foreground: ForegroundPresentation = ForegroundPresentation.System,
    public val routing: PushRouting = PushRouting(),
)

/**
 * What apps and brick presenters use.
 *
 * Lifecycle it keeps (each row has a test in `PushControllerTest`):
 * - registers on sign-in and on every start while signed in (when enabled and allowed), and again
 *   when the provider rotates the token;
 * - on an explicit sign-out, while the bearer is valid, unregisters this installation
 *   (a `stapel-session` farewell hook), then deletes the token at the provider;
 * - deletes the provider token on every session end (expired, revoked, storage lost too), so the
 *   next account on this installation gets a fresh token;
 * - a guest session never registers; a 409 for the token is answered by one new token and one retry;
 * - a token the registry reports inactive is replaced;
 * - the OS permission is never asked at launch; [enable] runs the rationale first.
 */
public interface PushController {
    /** Current state. */
    public val state: StateFlow<PushState>

    /**
     * A deliberate act of the person: records the choice, asks permission when it was never asked
     * (rationale first), mints a token and registers it when signed in. Returns the resulting state.
     */
    public suspend fun enable(): PushState

    /** Records the choice, unregisters this installation and deletes the token; the OS permission stays the OS's. */
    public suspend fun disable(): PushState

    /** Every notification event of the transactional provider. */
    public val events: Flow<PushEvent>

    /**
     * Connects taps to navigation. Taps that came before (the launch notification) are kept and
     * opened now; each is opened once. Close the handle to detach.
     */
    public fun attach(opener: PushLinkOpener): AutoCloseable
}

/**
 * The persisted half of the controller: the person's choice and what was last registered.
 *
 * @property enabled the person turned push on.
 * @property registeredFingerprint fingerprint of the token last registered, null when none.
 * @property registeredAccount the account it was registered for.
 * @property optedOut the person turned push off ([PushController.disable]); [AskPolicy.AfterSignIn] does not ask again.
 */
public data class PushRecord(
    val enabled: Boolean = false,
    val optedOut: Boolean = false,
    val registeredFingerprint: String? = null,
    val registeredAccount: String? = null,
)

/** Where [PushRecord] lives: one small record per installation, outside the token vault (it survives sign-out). */
public interface PushStore {
    /** The stored record, or the default when there is none. */
    public suspend fun load(): PushRecord

    /** Replaces the record. */
    public suspend fun save(record: PushRecord)
}

/** A [PushStore] in memory: tests, and desktop where push is unsupported. */
public class InMemoryPushStore(initial: PushRecord = PushRecord()) : PushStore {
    /** The current record. */
    public var record: PushRecord = initial
        private set

    override suspend fun load(): PushRecord = record

    override suspend fun save(record: PushRecord) {
        this.record = record
    }
}

/**
 * Collaborators of a [PushController].
 *
 * @property provider the transactional provider (the one whose tokens the registry takes); null = push not installed.
 * @property registry the backend seam; null = tokens are minted but registered nowhere.
 * @property sessions the session; registration follows its state.
 * @property identity the installation id sent as `device_id`.
 * @property store where the person's choice lives.
 * @property scope application scope.
 * @property lifecycle when present, the permission is re-read on every return to the foreground.
 * @property rationale the step before the OS prompt; null = none.
 * @property openListeners called after a tap was routed.
 * @property logger the app logger; receives the `stapel.push …` markers.
 */
public class PushDependencies(
    public val provider: PushProvider?,
    public val registry: PushDeviceRegistry?,
    public val sessions: SessionManager,
    public val identity: InstallationIdentity,
    public val store: PushStore,
    public val scope: CoroutineScope,
    public val lifecycle: AppLifecycle? = null,
    public val rationale: PushRationale? = null,
    public val openListeners: List<PushOpenListener> = emptyList(),
    public val logger: StapelLogger = NoopLogger,
)

/**
 * Creates the controller and starts following the session. Plain function: usable without a DI
 * container. Registers a farewell hook and a logout hook on [PushDependencies.sessions].
 */
public fun PushController(deps: PushDependencies, options: PushOptions = PushOptions()): PushController =
    DefaultPushController(deps, options).also { it.start() }

/** The tag of the `stapel.push …` log markers (what launch smokes wait for). */
public const val PUSH_LOG_TAG: String = "stapel.push"
