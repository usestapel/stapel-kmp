package shop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.koin.dsl.module
import stapel.app.di.StapelAppContent
import stapel.auth.StapelAuth
import stapel.auth.session.AuthState
import stapel.auth.testing.AuthTags
import stapel.auth.testing.awaitTag
import stapel.auth.testing.signInWithMockOtp
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The generated app's sign-in walk: the whole app (Koin graph, shell, the real auth screens, the real
 * transport) against a scaffolded stapel-auth in prototype posture at WALK_BASE_URL. Compiled in only
 * with `-Pstapel.walk.baseUrl=<url>`; a closed port fails the walk, it is never skipped once compiled.
 */
@OptIn(ExperimentalTestApi::class)
class SignInWalkTest {
    @Test
    fun aNewCustomerSignsInWithTheMockCodeAndLandsOnHome() = runComposeUiTest {
        val app = createShopApp(testPlatformContext(), testTarget, WALK_BASE_URL) {
            // The token store is replaced: the walk proves the flow, not the platform vault (see the report).
            overrides(module { single<TokenVault> { InMemoryTokenVault() } })
            global(false)
        }
        try {
            setContent { StapelAppContent(app) }
            signInWithMockOtp("walk+${Random.nextLong(1_000_000_000L)}@example.com")
            awaitTag("shop.home")
            // The account control (sign-out) is in the top bar once signed in.
            awaitTag(AuthTags.ACCOUNT_MENU)
            assertTrue(app.koin.get<StapelAuth>().session.state.value is AuthState.SignedIn)
        } finally {
            app.close()
        }
    }
}
