package stapel.app.di

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import stapel.cache.di.cacheModule
import stapel.cache.session.di.cacheSessionModule
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.di.stapelKoin
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.i18n.StapelStringsBuilder
import stapel.navigation.CapabilityKey
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.NavOverrides
import stapel.navigation.NavPolicy
import stapel.navigation.StapelNavigator
import stapel.navigation.UnauthenticatedMode
import stapel.navigation.compose.NavContribution
import stapel.network.NetworkConfig
import stapel.network.di.networkModule
import stapel.result.text.ErrorTextResolver
import stapel.runtime.LocaleProvider
import stapel.runtime.LogLevel
import stapel.runtime.PlatformContext
import stapel.runtime.StapelLogger
import stapel.runtime.info
import stapel.runtime.di.runtimeModule
import stapel.runtime.platformLogger
import stapel.session.SessionConfig
import stapel.session.di.sessionModule
import stapel.session.vault.TokenVaultConfig
import stapel.shell.ShellTemplate
import stapel.shell.SlotPlan
import stapel.tokens.StapelDefaultTokens
import stapel.ui.StapelTheme
import stapel.ui.skin.FoundationSkin

/** Wraps the app's content in its theme (tokens + skin), e.g. `{ content -> StapelMaterialTheme(tokens = AppTokens, content = content) }`. */
public typealias AppTheme = @Composable (content: @Composable () -> Unit) -> Unit

/**
 * Builds a Stapel app from a small typed configuration: the substrate (runtime, network, session,
 * cache, the cache bound to the session, optional durable cache storage), the bricks' modules, their
 * navigation, strings and error texts, the theme and the shell. Sugar over public factories only:
 * everything here can be wired by hand with `stapelKoin(...)`, `StapelShell(...)` and the bricks'
 * own module factories.
 *
 * ```kotlin
 * val app = stapelApp(PlatformContext()) {
 *     environment(AppEnvironment)
 *     offline(cacheRoomModule())            // omit for the in-memory cache
 *     bricks(AuthBrick())
 *     navigation(home = DestinationId("shop.home"))
 *     theme { content -> StapelMaterialTheme(tokens = AppTokens, content = content) }
 * }
 * ```
 *
 * The builder installs `cacheSessionModule()` itself, once: a brick never binds the cache to the
 * session (the duplicate `authModule(bindCacheToSession = true)` + `cacheSessionModule()` cannot
 * happen through the builder).
 *
 * @param platform the platform handle (`PlatformContext(application)` on Android, `PlatformContext()` elsewhere).
 */
public fun stapelApp(platform: PlatformContext, configure: StapelAppBuilder.() -> Unit): StapelApp =
    StapelAppBuilder(platform).apply(configure).build()

/** Receiver of [stapelApp]. Every call is optional except [environment]. */
public class StapelAppBuilder internal constructor(private val platform: PlatformContext) {
    private var environment: StapelEnvironment? = null
    private var logger: StapelLogger? = null
    private var network = NetworkConfig()
    private var sessionConfig = SessionConfig()
    private var vaultConfig = TokenVaultConfig()
    private var storage: Module? = null
    private val bricks = mutableListOf<StapelBrick>()
    private val screens = mutableListOf<(BrickContext) -> NavContribution>()
    private val extraModules = mutableListOf<Module>()
    private val overrideModules = mutableListOf<Module>()
    private val capabilities = linkedSetOf<CapabilityKey>()
    private var home: DestinationId? = null
    private var template = ShellTemplate.AdaptiveTabs
    private var slots: SlotPlan? = null
    private var deepLinks: DeepLinkConfig? = null
    private var navOverrides = NavOverrides()
    private var unauthenticated = UnauthenticatedMode.Redirect
    private var theme: AppTheme = { content -> StapelTheme(skin = FoundationSkin, tokens = StapelDefaultTokens, content = content) }
    private val stringConfig = mutableListOf<StapelStringsBuilder.() -> Unit>()
    private var global = true

    /** The backend environment (base URLs, posture, client identity). Required. */
    public fun environment(environment: StapelEnvironment) {
        this.environment = environment
    }

    /** The logger; default the platform logger at info level. */
    public fun logger(logger: StapelLogger) {
        this.logger = logger
    }

    /** Transport options (certificate pins, retry, body logging). */
    public fun network(config: NetworkConfig) {
        network = config
    }

    /** Session and token-vault options. */
    public fun session(config: SessionConfig = SessionConfig(), vault: TokenVaultConfig = TokenVaultConfig()) {
        sessionConfig = config
        vaultConfig = vault
    }

    /**
     * Durable cache storage (offline support): pass the storage library's module, e.g.
     * `cacheRoomModule()`. Without it the cache keeps entries in memory. The only line that changes
     * when an app turns offline on.
     */
    public fun offline(storage: Module) {
        this.storage = storage
    }

    /** Installs [bricks] in this order (their strings register in this order too). */
    public fun bricks(vararg bricks: StapelBrick) {
        require(this.bricks.none { b -> bricks.any { it.key == b.key } }) { "a brick is installed twice" }
        this.bricks += bricks
    }

    /** The app's own screens: navigation contributions built against the app's graph. */
    public fun screens(vararg contributions: (BrickContext) -> NavContribution) {
        screens += contributions
    }

    /** Extra Koin modules: capabilities (`captchaModule(...)`), the app's own clients and repositories. */
    public fun modules(vararg modules: Module) {
        extraModules += modules
    }

    /** Modules loaded last with overrides allowed (tests, an app replacing a library default). */
    public fun overrides(vararg modules: Module) {
        overrideModules += modules
    }

    /** Capabilities the app provides (keys of the bricks' [StapelBrick.requires]). */
    public fun capabilities(keys: Iterable<CapabilityKey>) {
        capabilities += keys
    }

    /**
     * Navigation policy and chrome.
     *
     * @param home where a signed-in user lands; null = the first tab.
     * @param template the shell template.
     * @param slots which embed fills which chrome slot; null = every installed embed in its declared slot.
     * @param deepLinks schemes and hosts the app answers; null = no deep links.
     * @param overrides project overrides of labels, order and menu visibility.
     * @param unauthenticated what a signed-out user gets on a member destination.
     */
    public fun navigation(
        home: DestinationId? = null,
        template: ShellTemplate = ShellTemplate.AdaptiveTabs,
        slots: SlotPlan? = null,
        deepLinks: DeepLinkConfig? = null,
        overrides: NavOverrides = NavOverrides(),
        unauthenticated: UnauthenticatedMode = UnauthenticatedMode.Redirect,
    ) {
        this.home = home
        this.template = template
        this.slots = slots
        this.deepLinks = deepLinks
        this.navOverrides = overrides
        this.unauthenticated = unauthenticated
    }

    /** The theme wrapper (tokens + skin); default the foundation skin over the library tokens. */
    public fun theme(theme: AppTheme) {
        this.theme = theme
    }

    /** The app's own strings: overrides and extra bundles, applied after the bricks' bundles. */
    public fun strings(configure: StapelStringsBuilder.() -> Unit) {
        stringConfig += configure
    }

    /** False keeps Koin out of the global context (tests, previews, several apps in one process). */
    public fun global(global: Boolean) {
        this.global = global
    }

    internal fun build(): StapelApp {
        val env = checkNotNull(environment) { "stapelApp: environment(...) is required" }
        val substrate = listOfNotNull(
            runtimeModule(platform, logger ?: platformLogger(LogLevel.Info)),
            networkModule(env, network),
            sessionModule(sessionConfig, vaultConfig),
            cacheModule(),
            storage,
            cacheSessionModule(),
        )
        val koinApp = stapelKoin(substrate + bricks.flatMap { it.modules() } + extraModules, overrideModules, global)
        val koin = koinApp.koin
        val context = BrickContext(koin, koin.get<CoroutineScope>(StapelQualifiers.AppScope))
        val contributions = bricks.mapNotNull { it.navigation(context) } + screens.map { it(context) }
        val sessions = bricks.mapNotNull { it.session(context) }
        require(sessions.size <= 1) { "stapelApp: more than one brick owns sign-in (${bricks.map { it.key }})" }
        val session = sessions.singleOrNull()
        val strings = StapelStrings {
            initialLocale = koin.getOrNull<LocaleProvider>()?.locale?.value?.value
            register(CoreUiBundle)
            bricks.flatMap { it.strings }.forEach { register(it) }
            stringConfig.forEach { it() }
        }
        // The app's strings follow the effective locale: the device's, or the in-app choice
        // (`LocaleProvider.setAppLocale`, e.g. the profiles brick's app language) — at once, and with
        // them the layout direction of every StapelTheme under the app (R17).
        koin.getOrNull<LocaleProvider>()?.let { provider ->
            context.appScope.launch { provider.locale.collect { strings.setLocale(it.value) } }
        }
        val embedsBySlot = contributions.flatMap { it.manifest.embeds }.groupBy { it.slot }
            .mapValues { (_, specs) -> specs.sortedWith(compareBy({ it.order }, { it.id.value })).map { it.id } }
        return StapelApp(
            koinApplication = koinApp,
            bricks = bricks.toList(),
            context = context,
            contributions = contributions,
            session = session,
            policy = NavPolicy(signIn = session?.signIn, unauthenticated = unauthenticated, home = home),
            template = template,
            slots = slots ?: SlotPlan(embedsBySlot),
            deepLinks = deepLinks,
            navOverrides = navOverrides,
            capabilities = capabilities.toSet(),
            theme = theme,
            strings = strings,
            errorTexts = ErrorTextResolver(bricks.flatMap { it.errorCatalogs }),
        )
    }
}

/**
 * A built app: its Koin graph and everything [StapelAppContent] draws. One instance per process
 * (Android: in `Application.onCreate`; iOS: once in the app delegate or `@main`; desktop: in `main`).
 */
public class StapelApp internal constructor(
    private val koinApplication: KoinApplication,
    internal val bricks: List<StapelBrick>,
    internal val context: BrickContext,
    /** The navigation of every installed brick and the app's own screens, in installation order. */
    public val contributions: List<NavContribution>,
    internal val session: BrickSession?,
    /** The navigation policy (sign-in from the session brick, home, unauthenticated mode). */
    public val policy: NavPolicy,
    internal val template: ShellTemplate,
    /** Which embed fills which chrome slot. */
    public val slots: SlotPlan,
    internal val deepLinks: DeepLinkConfig?,
    internal val navOverrides: NavOverrides,
    /** Capabilities the app provides. */
    public val capabilities: Set<CapabilityKey>,
    internal val theme: AppTheme,
    /** UI strings: core floor, the bricks' bundles, the app's overrides. */
    public val strings: StapelStrings,
    /** Error texts: the bricks' catalogs over the client floor. */
    public val errorTexts: ErrorTextResolver,
) : AutoCloseable {
    /** The app's Koin graph. */
    public val koin: Koin get() = koinApplication.koin

    /** The navigation auth source: the session brick's, or a signed-out constant without one. */
    public val navAuth: NavAuthSource = session ?: anonymous

    private val navigatorState = MutableStateFlow<StapelNavigator?>(null)
    private val pendingUrls = mutableListOf<String>()

    /** The navigator once the content has created it. */
    public val navigator: StateFlow<StapelNavigator?> = navigatorState.asStateFlow()

    /**
     * Opens a deep link (`<scheme>://login`, `https://<host>/…`); kept until the navigator exists. Logs
     * `stapel.link <uri> opened|refused|queued` through the app's logger (what a launch smoke waits for).
     *
     * @return false when the navigator exists and the address is not one of the app's.
     */
    public fun openUrl(uri: String): Boolean {
        val n = navigatorState.value
        if (n == null) {
            pendingUrls += uri
            log("stapel.link $uri queued")
            return true
        }
        return n.open(uri).also { log("stapel.link $uri ${if (it) "opened" else "refused"}") }
    }

    internal fun attach(navigator: StapelNavigator) {
        if (navigatorState.value === navigator) return
        navigatorState.value = navigator
        session?.attach(navigator)
        val pending = pendingUrls.toList()
        pendingUrls.clear()
        pending.forEach { uri -> navigator.open(uri).also { log("stapel.link $uri ${if (it) "opened" else "refused"}") } }
    }

    private fun log(message: String) {
        koin.getOrNull<StapelLogger>()?.info(APP_LOG_TAG) { message }
    }

    /** Closes the Koin graph (and with it the app scope). */
    override fun close() {
        koinApplication.close()
    }

    private companion object {
        private val anonymous: NavAuthSource = object : NavAuthSource {
            private val state = MutableStateFlow(NavAuthState.Anonymous)
            override fun authState(): StateFlow<NavAuthState> = state.asStateFlow()
        }
    }
}
