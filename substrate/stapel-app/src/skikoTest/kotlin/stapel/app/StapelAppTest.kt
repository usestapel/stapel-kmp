package stapel.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.app.di.BrickContext
import stapel.app.di.BrickSession
import stapel.app.di.StapelAppContent
import stapel.app.di.APP_LOG_TAG
import stapel.app.di.StapelBrick
import stapel.app.di.stapelApp
import stapel.cache.session.CacheSessionBinding
import stapel.i18n.I18nBundle
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.runtime.PlatformContext
import stapel.session.TokenRefresher
import stapel.session.vault.TokenVault
import stapel.session.vault.InMemoryTokenVault
import stapel.shell.Slots
import stapel.testing.RecordingLogger
import stapel.testing.testEnvironment
import stapel.ui.StapelText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data object HomeRoute : Route

@Serializable
private data object LoginRoute : Route

/** A minimal brick: a refresher for the session, a home and a sign-in screen, one top-bar embed, a bundle. */
private class DemoBrick(
    private val extra: List<Module> = emptyList(),
    private val owns: Boolean = true,
    override val key: String = "demo",
    private val refresher: Boolean = true,
) : StapelBrick {
    val auth = MutableStateFlow(NavAuthState.Anonymous)
    var restored = 0

    override val strings: List<I18nBundle> = listOf(I18nBundle("demo", mapOf("en" to { mapOf("demo.home" to "Demo home") })))

    override fun modules(): List<Module> =
        listOfNotNull(module { single<TokenRefresher> { TokenRefresher { error("no refresh here") } } }.takeIf { refresher }) + extra

    override fun navigation(context: BrickContext): NavContribution = navContribution("test:demo-$key", "0") {
        destination<HomeRoute>(
            id = "$key.home", path = "/home", labelKey = "demo.home", icon = NavIcons.Home,
            codec = PathCodec.constant(HomeRoute), requiresAuth = true, order = 0,
        ) { StapelText("home", Modifier.testTag("demo.home")) }
        destination<LoginRoute>(
            id = "$key.login", path = "/login", labelKey = "demo.login", icon = NavIcons.Login,
            codec = PathCodec.constant(LoginRoute), requiresAuth = false, menuVisibleDefault = false, order = 1,
        ) { StapelText("login", Modifier.testTag("demo.login")) }
        embed(id = "$key.badge", slot = Slots.TopbarEnd, order = 5) { }
    }

    override fun session(context: BrickContext): BrickSession? = if (!owns) null else object : BrickSession {
        override val signIn = DestinationId("$key.login")
        override fun authState(): StateFlow<NavAuthState> = auth
        override suspend fun restore() {
            restored++
        }
    }
}

private val memoryVault = module { single<TokenVault> { InMemoryTokenVault() } }

@OptIn(ExperimentalTestApi::class)
class StapelAppTest {
    private fun app(vararg bricks: StapelBrick, logger: RecordingLogger = RecordingLogger(), extra: List<Module> = emptyList()) = stapelApp(PlatformContext()) {
        environment(testEnvironment())
        logger(logger)
        bricks(*bricks)
        modules(*extra.toTypedArray())
        overrides(memoryVault)
        global(false)
    }

    @Test
    fun theBuilderWiresTheSubstrateTheBricksAndOneCacheSessionBinding() {
        app(DemoBrick()).use { app ->
            assertNotNull(app.koin.getOrNull<CacheSessionBinding>(), "the builder binds the cache to the session")
            assertEquals(listOf("demo.badge"), app.slots.filled[Slots.TopbarEnd]?.map { it.value })
            assertEquals(DestinationId("demo.login"), app.policy.signIn)
            assertEquals("Demo home", app.strings.t("demo.home"))
            assertEquals(listOf("test:demo-demo"), app.contributions.map { it.manifest.artifact })
        }
    }

    @Test
    fun aBrickThatBindsTheCacheToTheSessionItselfIsRefused() {
        val duplicate = module {
            single(createdAtStart = true) { CacheSessionBinding(get(), get(), get(stapel.di.StapelQualifiers.AppScope)) }
        }
        assertFailsWith<DefinitionOverrideException> { app(DemoBrick(extra = listOf(duplicate))) }
    }

    @Test
    fun anEnvironmentIsRequiredAndOnlyOneBrickOwnsSignIn() {
        assertFailsWith<IllegalStateException> { stapelApp(PlatformContext()) { global(false) } }
        val e = assertFailsWith<IllegalArgumentException> { app(DemoBrick(), DemoBrick(key = "other", refresher = false)) }
        assertTrue("more than one brick owns sign-in" in e.message.orEmpty(), e.message)
    }

    @Test
    fun withoutASessionBrickNavigationIsSignedOut() {
        app(DemoBrick(owns = false)).use { app ->
            assertEquals(NavAuthState.Anonymous, app.navAuth.authState().value)
            assertEquals(null, app.policy.signIn)
        }
    }

    @Test
    fun theContentRestoresTheSessionLogsTheMarkersAndRoutesTheGuard() = runComposeUiTest {
        val logger = RecordingLogger()
        val brick = DemoBrick()
        val app = app(brick, logger = logger)
        try {
            app.openUrl("elsewhere://nowhere/login")
            setContent { StapelAppContent(app) }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("demo.login")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, brick.restored)
            brick.auth.value = NavAuthState.Member
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("demo.home")).fetchSemanticsNodes().isNotEmpty() }
            val messages = logger.records.filter { it.tag == APP_LOG_TAG }.map { it.message }
            assertTrue("stapel.app.ready" in messages, "$messages")
            assertTrue("stapel.nav demo.login" in messages && "stapel.nav demo.home" in messages, "$messages")
            assertNotNull(app.navigator.value)
            // A link received before the navigator exists is queued, then handed to it (and refused: no deep-link config).
            assertTrue("stapel.link elsewhere://nowhere/login queued" in messages, "$messages")
            assertTrue("stapel.link elsewhere://nowhere/login refused" in messages, "$messages")
        } finally {
            app.close()
        }
    }

    private class PageRecorder : stapel.runtime.Analytics {
        val pages = mutableListOf<String>()
        override fun track(event: String, props: Map<String, String>) {}
        override fun page(name: String, props: Map<String, String>) {
            pages += name
        }
        override fun identify(userHash: String?) {}
    }

    @Test
    fun aBoundAnalyticsRecordsAPageOnEveryTopDestinationChange() = runComposeUiTest {
        val recorder = PageRecorder()
        val brick = DemoBrick()
        val app = app(brick, extra = listOf(module { single<stapel.runtime.Analytics> { recorder } }))
        try {
            setContent { StapelAppContent(app) }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("demo.login")).fetchSemanticsNodes().isNotEmpty() }
            brick.auth.value = NavAuthState.Member
            waitUntil(timeoutMillis = 10_000) { recorder.pages.contains("demo.home") }
            assertEquals(listOf("demo.login", "demo.home"), recorder.pages)
        } finally {
            app.close()
        }
    }
}
