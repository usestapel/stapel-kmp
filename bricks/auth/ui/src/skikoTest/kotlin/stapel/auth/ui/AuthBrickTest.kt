package stapel.auth.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.app.di.BrickContext
import stapel.app.di.BrickSession
import stapel.app.di.StapelBrick
import stapel.app.di.StapelAppContent
import stapel.app.di.stapelApp
import stapel.auth.StapelAuth
import stapel.auth.session.AuthState
import stapel.auth.ui.di.AuthBrick
import stapel.cache.session.CacheSessionBinding
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.compose.NavContribution
import stapel.runtime.PlatformContext
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.StoredSession
import stapel.session.vault.TokenVaultConfig
import stapel.session.vault.VaultRead
import stapel.session.vault.platformTokenVault
import stapel.runtime.defaultDispatchers
import stapel.auth.session.SignOutReason
import kotlin.random.Random
import stapel.shell.ShellTags
import stapel.session.vault.TokenVault
import stapel.result.TransportFailure
import stapel.testing.FakeAnswer
import stapel.testing.FakeApiTransport
import stapel.testing.testEnvironment
import stapel.wire.ApiTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A transport answering the calls the sign-in screen makes with a typed offline failure. */
private class OfflineTransport(private val fake: FakeApiTransport = FakeApiTransport()) : ApiTransport by fake {
    init {
        listOf("auth_api_v1_capabilities_retrieve", "auth_api_v1_me_retrieve").forEach { op ->
            fake.on(op) { FakeAnswer.Failure(TransportFailure.Offline("test")) }
        }
    }
}

/** `AuthBrick()` is the whole auth wiring of an app: nothing else is installed for auth here. */
@OptIn(ExperimentalTestApi::class)
class AuthBrickTest {
    private fun offline(vault: () -> TokenVault): Module = module {
        single<TokenVault> { vault() }
        // Offline: the sign-in screen renders its retry state and nothing leaves the test.
        single<ApiTransport> { OfflineTransport() }
    }

    private fun app(vararg bricks: StapelBrick, vault: () -> TokenVault = { InMemoryTokenVault() }) = stapelApp(PlatformContext()) {
        environment(testEnvironment())
        bricks(*bricks)
        overrides(offline(vault))
        global(false)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.firstLaunch(vault: () -> TokenVault): AuthState {
        val app = app(AuthBrick(), vault = vault)
        try {
            setContent { StapelAppContent(app) }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(AuthTestTags.SIGN_IN)).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            return app.koin.get<StapelAuth>().session.state.value
        } finally {
            app.close()
        }
    }

    @Test
    fun aNeverWrittenPlatformVaultIsNoSessionAndShowsPlainSignIn() = runComposeUiTest {
        // The JVM platform vault (owner-only file) under a namespace nothing ever wrote.
        val state = firstLaunch { platformTokenVault(PlatformContext(), defaultDispatchers(), TokenVaultConfig(namespace = "stapel.test.never-${Random.nextLong()}")) }
        assertEquals(AuthState.SignedOut(null), state)
        assertEquals(0, onAllNodes(hasTestTag(AuthTestTags.NOTICE)).fetchSemanticsNodes().size, "no notice on a first launch")
    }

    @Test
    fun anUnreadableStoredSessionIsReportedAsLost() = runComposeUiTest {
        // The inversion: a record that exists but cannot be read is a lost session and is said so.
        val state = firstLaunch {
            object : TokenVault {
                override suspend fun read(): VaultRead = VaultRead.Unreadable("key invalidated")
                override suspend fun write(session: StoredSession) {}
                override suspend fun clear() {}
            }
        }
        assertEquals(AuthState.SignedOut(SignOutReason.StorageLost), state)
        assertEquals(1, onAllNodes(hasTestTag(AuthTestTags.NOTICE)).fetchSemanticsNodes().size)
    }

    @Test
    fun theDescriptorAloneWiresAuthNavigationStringsAndErrorTexts() {
        app(AuthBrick()).use { app ->
            assertNotNull(app.koin.getOrNull<StapelAuth>(), "authModule() comes from the descriptor")
            assertNotNull(app.koin.getOrNull<CacheSessionBinding>(), "exactly one cache-session binding (the builder's)")
            assertEquals(AuthNavIds.Login, app.policy.signIn)
            assertEquals(listOf(AUTH_UI_ARTIFACT), app.contributions.map { it.manifest.artifact })
            val ids = app.contributions.single().manifest.destinations.map { it.id }
            assertEquals(listOf(AuthNavIds.Login, AuthNavIds.Security), ids)
            assertEquals(listOf(AuthNavIds.ACCOUNT_MENU), app.slots.filled[AuthNavIds.AccountMenuSlot]?.map { it.value })
            assertTrue(app.strings.t(AuthUiStrings.NavLogin.key) != AuthUiStrings.NavLogin.key, "the auth bundle is registered")
        }
    }

    @Test
    fun authAloneKeepsTheAccountMenuSoSignOutStaysReachable() {
        // No library hangs anything from account.root, so the section is not installed; the menu (and
        // its sign-out) stays in topbar.end, only its "Account" action goes. Resolved as the host does.
        app(AuthBrick()).use { app ->
            val nav = stapel.navigation.resolveNav(app.contributions.map { it.manifest })
            val menu = nav.embeds.single { it.id.value == AuthNavIds.ACCOUNT_MENU }
            assertEquals(AuthNavIds.AccountMenuSlot, menu.slot)
            assertEquals(null, menu.opens)
            assertTrue(DestinationId("account.root") !in nav.byId)
        }
    }

    @Test
    fun theContentRestoresTheSessionAndShowsSignIn() = runComposeUiTest {
        val app = app(AuthBrick())
        try {
            setContent { StapelAppContent(app) }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(AuthTestTags.SIGN_IN)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(AuthState.SignedOut(null), app.koin.get<StapelAuth>().session.state.value)
            assertEquals(NavAuthState.Anonymous, app.navAuth.authState().value)
            // Hosted in the shell, the chrome owns the title: the top bar shows the screen's title, the
            // screen draws no second heading.
            val title = app.strings.t(AuthUiStrings.LoginTitle.key)
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(ShellTags.Title) and hasText(title)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, onAllNodes(hasTestTag(AuthTestTags.TITLE)).fetchSemanticsNodes().size)
            assertEquals(1, onAllNodes(hasText(title)).fetchSemanticsNodes().size, "the sign-in title is drawn once")
        } finally {
            app.close()
        }
    }

    @Test
    fun aSecondSignInBrickIsRefused() {
        val other = object : StapelBrick {
            override val key: String = "other"
            override fun modules(): List<Module> = emptyList()
            override fun navigation(context: BrickContext): NavContribution? = null
            override fun session(context: BrickContext): BrickSession = object : BrickSession {
                override val signIn: DestinationId = DestinationId("other.login")
                override fun authState(): StateFlow<NavAuthState> = MutableStateFlow(NavAuthState.Anonymous)
                override suspend fun restore() {}
            }
        }
        val e = assertFailsWith<IllegalArgumentException> { app(AuthBrick(), other) }
        assertTrue("more than one brick owns sign-in" in e.message.orEmpty(), e.message)
    }
}
