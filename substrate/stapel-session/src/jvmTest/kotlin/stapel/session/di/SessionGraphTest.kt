package stapel.session.di

import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.SessionScopeHandle
import stapel.di.stapelKoin
import stapel.network.HttpTransport
import stapel.network.RequestAuthenticator
import stapel.network.WireOutcome
import stapel.network.WireRequest
import stapel.network.di.NetworkQualifiers
import stapel.network.di.networkModule
import stapel.result.StapelResult
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.session.LogoutHook
import stapel.session.RefreshOutcome
import stapel.session.SessionEnd
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.TokenPair
import stapel.session.TokenRefresher
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import stapel.wire.ApiTransport
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.ErrorDecoder
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.StapelError
import stapel.result.TransportFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/** G-D3: the composition root resolves, and adding sessionModule() is what turns bearer auth on. */
class SessionGraphTest {
    private class Wire : HttpTransport {
        val requests = mutableListOf<WireRequest>()

        override suspend fun send(request: WireRequest): WireOutcome {
            requests += request
            return WireOutcome.Response(204, emptyList(), ByteArray(0))
        }

        override fun close() {}
    }

    private val ping = OperationSpec<Unit, Unit, StapelError>(
        "ping", "auth", "GET", "/auth/api/v1/ping/", OperationKind.Query, AuthRequirement.Optional,
        BodyEncoding.None, null, ResponseKind.Empty, mapOf(204 to null),
        object : ErrorDecoder<StapelError> {
            override fun domain(envelope: ErrorEnvelope): StapelError = ServerRefusal(envelope)

            override fun transport(failure: TransportFailure): StapelError = failure
        },
    )

    @Test
    fun graphResolvesAndTheSessionAuthenticatesTheTransport() = runBlocking {
        val wire = Wire()
        val hookCauses = mutableListOf<SessionEnd>()
        val env = StapelEnvironment.single(
            "test", "https://api.example.com/", Posture.Prototype,
            ClientInfo("com.example.app", "1", "1", ClientPlatform.Desktop, "macOS", "15", null, "0"),
        )
        val app = stapelKoin(
            modules = listOf(
                runtimeModule(PlatformContext()),
                networkModule(env),
                sessionModule(),
                module {
                    single<TokenRefresher> { TokenRefresher { RefreshOutcome.Transient("not in this test") } }
                    single<LogoutHook>(stapel.di.StapelQualifiers.contribution("test")) { LogoutHook { hookCauses += it } }
                },
            ),
            overrides = listOf(
                module {
                    single<TokenVault> { InMemoryTokenVault() }
                    single<HttpTransport> { wire }
                },
            ),
            global = false,
        )
        try {
            val koin = app.koin
            val session = koin.get<SessionManager>()
            assertSame(session.authenticator, koin.get<RequestAuthenticator>())
            val api = koin.get<ApiTransport>()
            assertNotSame(api, koin.get<ApiTransport>(NetworkQualifiers.Bare))
            val scopes = koin.get<SessionScopeHandle>()

            session.restore()
            assertIs<StapelResult.Ok<Unit>>(api.execute(ping, OperationCall()))
            assertNull(wire.requests.last().header("Authorization"))

            session.adopt(TokenPair("access-1", "refresh-1"), SessionKind.Authenticated)
            api.execute(ping, OperationCall())
            assertEquals("Bearer access-1", wire.requests.last().header("Authorization"))
            koin.get<ApiTransport>(NetworkQualifiers.Bare).execute(ping, OperationCall())
            assertNull(wire.requests.last().header("Authorization"), "the bare transport never authenticates")

            repeat(50) { if (scopes.current.value == null) kotlinx.coroutines.delay(10) }
            assertNotNull(scopes.current.value, "a session scope opens for a live session")
            session.logout()
            assertEquals(listOf(SessionEnd.Logout), hookCauses, "contributed logout hooks are registered")
        } finally {
            app.close()
        }
    }
}
