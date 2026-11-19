package stapel.auth.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import stapel.auth.StapelAuth
import stapel.auth.di.authModule
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.OtpChannel
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.session.AuthState
import stapel.cache.ManagedStapelCache
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.captcha.CaptchaSolver
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.di.stapelKoin
import stapel.mvi.PresenterContext
import stapel.network.di.networkModule
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.session.InstallationIdentity
import stapel.session.SessionManager
import stapel.session.di.sessionModule
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import stapel.wire.WireJson
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The Koin composition and the captcha seam against the real stand. */
class WiringAndCaptchaTest {

    @Test
    fun `authModule wires the whole stack - a presenter from Koin signs in over real HTTP`(): Unit = runBlocking {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val env = StapelEnvironment.single(
            "e2e", Stand.baseUrl, Posture.Prototype,
            ClientInfo("stapel.auth.e2e", "0.0.0", "e2e", ClientPlatform.Desktop, "JVM", "21", "koin"),
        )
        val koin = stapelKoin(
            modules = listOf(
                runtimeModule(PlatformContext()),
                networkModule(env),
                sessionModule(),
                authModule(),
                module {
                    single<ManagedStapelCache> {
                        StapelCache(StapelCacheConfig(json = WireJson, scope = get(StapelQualifiers.AppScope), clock = Clock.System))
                    }
                },
            ),
            overrides = listOf(
                module {
                    single<TokenVault> { InMemoryTokenVault() }
                    single<InstallationIdentity> { object : InstallationIdentity { override suspend fun deviceId() = "e2e-koin-" + UUID.randomUUID() } }
                    single<CoroutineScope>(StapelQualifiers.AppScope) { appScope }
                },
            ),
            global = false,
        ).koin
        try {
            koin.get<SessionManager>().restore()
            val auth = koin.get<StapelAuth>()
            val presenter = koin.get<OtpSignInPresenter> { parametersOf(PresenterContext(appScope)) }
            presenter.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            presenter.state.await { it is OtpSignInState.CodeSent }
            presenter.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
            assertIs<OtpSignInState.Done>(presenter.state.await { it is OtpSignInState.Done })
            assertIs<AuthState.SignedIn>(auth.session.state.await { it is AuthState.SignedIn })
            waitPastExpiry()
            assertIs<stapel.result.StapelResult.Ok<*>>(auth.session.refreshUser(), "refresh wired through the bare transport")
        } finally {
            koin.close()
            appScope.cancel()
        }
    }

    @Test
    fun `captcha - without a solver the request ends typed CaptchaRequired, with one it is solved once and sent`(): Unit = runBlocking {
        val captchaUrl = Stand.captchaBaseUrl
        assumeTrue(captchaUrl != null, "needs -Pstapel.e2e.captchaBaseUrl (the stand's captcha instance)")
        Device("captcha-none", baseUrl = captchaUrl!!).use { d ->
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            val failed = p.state.await { it is OtpSignInState.RequestFailed || it is OtpSignInState.CodeSent }
            assertIs<OtpSignInState.RequestFailed>(failed)
            assertEquals(AuthErrorReason.CaptchaRequired, failed.error.reason)
            assertEquals(1, d.log.count(SignInScenariosTest.EMAIL_REQUEST), "no second request without a solver")
        }
        val solves = AtomicInteger()
        Device("captcha-solver", baseUrl = captchaUrl, captcha = CaptchaSolver { solves.incrementAndGet(); "e2e-captcha-ok" }).use { d ->
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            assertIs<OtpSignInState.CodeSent>(p.state.await { it is OtpSignInState.RequestFailed || it is OtpSignInState.CodeSent })
            assertEquals(1, solves.get(), "one solve")
            assertEquals(listOf(400, 200), d.log.attempts.filter { it.operationId == SignInScenariosTest.EMAIL_REQUEST }.map { it.status })
        }
        Device("captcha-wrong", baseUrl = captchaUrl, captcha = CaptchaSolver { "not-the-token" }).use { d ->
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            val failed = p.state.await { it is OtpSignInState.RequestFailed || it is OtpSignInState.CodeSent }
            assertIs<OtpSignInState.RequestFailed>(failed)
            assertEquals(AuthErrorReason.CaptchaInvalid, failed.error.reason)
        }
        assertTrue(Stand.cookieRequests(captchaUrl) == 0, "no Cookie on the captcha instance either")
    }
}
