package stapel.auth.e2e

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import stapel.app.di.stapelApp
import stapel.auth.StapelAuth
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.model.OtpChannel
import stapel.auth.session.AuthState
import stapel.auth.ui.AuthNavIds
import stapel.auth.ui.di.AuthBrick
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.mvi.PresenterContext
import stapel.navigation.NavAuthState
import stapel.runtime.PlatformContext
import stapel.session.SessionManager
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** `stapelApp { bricks(AuthBrick()) }` and nothing else for auth: a real sign-in through the app's graph. */
class AppCompositionTest {
    @Test
    fun `AuthBrick alone wires auth into stapelApp - the mock code signs in and navigation sees a member`(): Unit = runBlocking {
        val app = stapelApp(PlatformContext()) {
            environment(
                StapelEnvironment.single(
                    "e2e", Stand.baseUrl, Posture.Prototype,
                    ClientInfo("stapel.auth.e2e", "0.0.0", "e2e", ClientPlatform.Desktop, "JVM", "21", "app"),
                ),
            )
            bricks(AuthBrick())
            // The JVM vault is an owner-only file; this test proves the composition, not the vault.
            overrides(module { single<TokenVault> { InMemoryTokenVault() } })
            global(false)
        }
        app.use {
            assertEquals(AuthNavIds.Login, app.policy.signIn)
            app.koin.get<SessionManager>().restore()
            val auth = app.koin.get<StapelAuth>()
            assertEquals(NavAuthState.Anonymous, app.navAuth.authState().await { it == NavAuthState.Anonymous })
            val scope = app.koin.get<kotlinx.coroutines.CoroutineScope>(StapelQualifiers.AppScope)
            val presenter = app.koin.get<OtpSignInPresenter> { parametersOf(PresenterContext(scope)) }
            presenter.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            presenter.state.await { it is OtpSignInState.CodeSent }
            presenter.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
            assertIs<OtpSignInState.Done>(presenter.state.await { it is OtpSignInState.Done })
            assertIs<AuthState.SignedIn>(auth.session.state.await { it is AuthState.SignedIn })
            assertEquals(NavAuthState.Member, app.navAuth.authState().await { it == NavAuthState.Member })
        }
    }
}
