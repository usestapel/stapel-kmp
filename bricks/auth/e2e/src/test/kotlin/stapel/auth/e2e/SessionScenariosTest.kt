package stapel.auth.e2e

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import stapel.auth.api.model.AnonymousAuth
import stapel.auth.api.model.TokenRefreshRequest
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.presentation.SessionsIntent
import stapel.auth.presentation.SessionsPresenter
import stapel.auth.presentation.SignOutIntent
import stapel.auth.presentation.SignOutPresenter
import stapel.auth.presentation.SignOutState
import stapel.auth.session.AuthState
import stapel.auth.session.LogoutResult
import stapel.auth.session.SessionChange
import stapel.auth.session.SignOutReason
import stapel.cache.QuerySpec
import stapel.result.StapelResult
import stapel.result.TransportCarrier
import stapel.result.state.Resource
import stapel.session.SessionState
import stapel.session.TokenPair
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Token lifecycle, revocation, sign-out and guest scenarios (ARCH-AUTH §9.2 D-6…D-12). */
class SessionScenariosTest {

    @Test
    fun `D-6 logout revokes on the server, clears vault, state and the user's cache partition`(): Unit = runBlocking {
        Device("d6").use { d ->
            signInByEmail(d, Stand.email())
            val old: TokenPair = d.storedTokens()!!
            // Fill the user partition, then prove sign-out wipes it.
            assertIs<StapelResult.Ok<*>>(d.auth.account.get())
            val meSpec = QuerySpec(stapel.auth.api.AuthKeys.getMe(), stapel.auth.api.model.User.serializer(), stapel.auth.api.AuthContract.SCHEMA_SHA256)
            assertNotNull(d.cache.peek(meSpec), "profile cached in the account partition")
            val changes = mutableListOf<SessionChange>()
            val collector = d.scope.launch { d.auth.session.changes.collect { changes += it } }

            assertEquals(LogoutResult.RevokedOnServer, d.auth.session.logout())
            val state = d.auth.session.state.await { it is AuthState.SignedOut }
            assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), state)
            assertNull(d.storedTokens(), "vault empty")
            assertNull(d.cache.peek(meSpec), "cache partition wiped on sign-out")
            kotlinx.coroutines.delay(200)
            assertTrue(changes.any { it is SessionChange.SignedOut && it.reason == SignOutReason.UserRequested }, "changes: $changes")
            collector.cancel()

            // The old refresh token is dead server-side.
            val reuse = d.bareApi.createTokenRefresh(TokenRefreshRequest(old.refreshToken))
            assertIs<StapelResult.Err<*>>(reuse)
            val code = (reuse.error as? TransportCarrier)?.failure?.envelope?.code?.value ?: reuse.error.code.value
            assertEquals("error.401.refresh_revoked", code, "old refresh after logout")
        }
    }

    @Test
    fun `D-7 refresh after real expiry happens once, the caller never sees a 401, both tokens rotate`(): Unit = runBlocking {
        Device("d7").use { d ->
            signInByEmail(d, Stand.email())
            val before = d.storedTokens()!!
            waitPastExpiry()
            d.log.clear()
            val me = d.auth.session.refreshUser()
            assertIs<StapelResult.Ok<*>>(me)
            assertEquals(1, d.log.count(REFRESH), "exactly one refresh: ${d.log.attempts}")
            assertTrue(d.log.attempts.none { it.status == 401 }, "no 401 on the wire: proactive refresh: ${d.log.attempts}")
            val after = d.storedTokens()!!
            assertNotEquals(before.accessToken, after.accessToken)
            assertNotEquals(before.refreshToken, after.refreshToken, "refresh rotates")
        }
    }

    @Test
    fun `D-8 ten parallel calls after expiry share one refresh and all succeed`(): Unit = runBlocking {
        Device("d8").use { d ->
            signInByEmail(d, Stand.email())
            waitPastExpiry()
            d.log.clear()
            val results = (1..10).map { async(d.scope.coroutineContext) { d.api.getMe() } }.awaitAll()
            assertTrue(results.all { it is StapelResult.Ok }, "all succeed: ${results.filterIsInstance<StapelResult.Err<*>>()}")
            assertEquals(1, d.log.count(REFRESH), "single-flight: ${d.log.attempts.filter { it.operationId == REFRESH }}")
        }
    }

    @Test
    fun `D-9 reactive path - with proactive refresh off an expired call gets one 401, one refresh, one replay`(): Unit = runBlocking {
        Device("d9", proactiveWindow = Duration.ZERO).use { d ->
            signInByEmail(d, Stand.email())
            waitPastExpiry()
            d.log.clear()
            assertIs<StapelResult.Ok<*>>(d.api.getMe())
            val me = d.log.attempts.filter { it.operationId == ME }
            assertEquals(listOf(401, 200), me.map { it.status }, "401 then replay: ${d.log.attempts}")
            assertEquals(1, d.log.count(REFRESH))
        }
    }

    @Test
    fun `D-10 a session revoked from a second device signs the first out with reason Revoked`(): Unit = runBlocking {
        val email = Stand.email()
        Device("d10-a").use { a ->
            Device("d10-b").use { b ->
                signInByEmail(a, email)
                signInByEmail(b, email)
                val bChanges = mutableListOf<SessionChange>()
                val collector = b.scope.launch { b.auth.session.changes.collect { bChanges += it } }

                val list = (a.auth.devices.load() as StapelResult.Ok).value
                assertEquals(2, list.size, "two sessions: $list")
                val other = list.maxBy { it.createdAt!! } // B signed in last
                assertEquals(list.single { it.id != other.id }, list.single { it.isCurrent }, "A's own row is the current one: $list")
                val sessions = SessionsPresenter(a.auth, a.context())
                sessions.dispatch(SessionsIntent.Revoke(other.id))
                sessions.state.await { s -> other.id !in s.revoking && (s.sessions as? Resource.Ready)?.data?.none { it.id == other.id } == true }

                waitPastExpiry()
                val call = b.auth.session.refreshUser()
                assertIs<StapelResult.Err<AuthError>>(call)
                assertEquals(AuthState.SignedOut(SignOutReason.Revoked), b.auth.session.state.await { it is AuthState.SignedOut })
                assertNull(b.storedTokens())
                kotlinx.coroutines.delay(200)
                assertTrue(bChanges.any { it is SessionChange.SignedOut && it.reason == SignOutReason.Revoked }, "B changes $bChanges")
                collector.cancel()

                assertIs<StapelResult.Ok<*>>(a.auth.session.refreshUser(), "A is unaffected")
                assertIs<AuthState.SignedIn>(a.auth.session.state.value)
            }
        }
    }

    @Test
    fun `D-11 a refresh that reaches no server never signs anybody out`(): Unit = runBlocking {
        TcpProxy(Stand.baseUrl).use { proxy ->
            Device("d11", baseUrl = proxy.url).use { d ->
                signInByEmail(d, Stand.email())
                waitPastExpiry()
                proxy.online = false
                val failed = d.auth.session.refreshUser()
                assertIs<StapelResult.Err<AuthError>>(failed)
                assertEquals(AuthErrorReason.Network, failed.error.reason, "transient: ${failed.error}")
                assertIs<AuthState.SignedIn>(d.auth.session.state.value, "still signed in")
                assertNotNull(d.storedTokens())
                proxy.online = true
                assertIs<StapelResult.Ok<*>>(d.auth.session.refreshUser(), "recovers once the server is back")
            }
        }
    }

    @Test
    fun `D-12 guest - reuse, promotion in place, merge into an existing account`(): Unit = runBlocking {
        val existing = Stand.email()
        val accountId = Device("d12-owner").use { d -> signInByEmail(d, existing).user.id }

        Device("d12-a").use { d ->
            val changes = MutableSharedFlow<SessionChange>(replay = 16)
            val collector = d.scope.launch { d.auth.session.changes.collect { changes.emit(it) } }
            val guest = (d.auth.session.ensureGuest() as StapelResult.Ok).value
            assertTrue(guest.isGuest)
            assertIs<AuthState.Guest>(d.auth.session.state.await { it is AuthState.Guest })
            val calls = d.log.count(ANONYMOUS)
            assertEquals(guest.id, (d.auth.session.ensureGuest() as StapelResult.Ok).value.id)
            assertEquals(calls, d.log.count(ANONYMOUS), "second ensureGuest makes no call")

            val promoted = signInByEmail(d, Stand.email())
            assertEquals(guest.id, promoted.user.id, "promotion keeps the id")
            assertTrue(!promoted.user.isGuest)
            kotlinx.coroutines.delay(200)
            assertTrue(changes.replayCache.any { it is SessionChange.Promoted && it.user.id == guest.id }, "changes ${changes.replayCache}")
            collector.cancel()
        }

        Device("d12-b").use { d ->
            val changes = MutableSharedFlow<SessionChange>(replay = 16)
            val collector = d.scope.launch { d.auth.session.changes.collect { changes.emit(it) } }
            val guest = (d.auth.session.ensureGuest() as StapelResult.Ok).value
            val merged = signInByEmail(d, existing)
            assertEquals(accountId, merged.user.id)
            assertNotEquals(guest.id, merged.user.id)
            kotlinx.coroutines.delay(200)
            val change = changes.replayCache.filterIsInstance<SessionChange.Merged>().singleOrNull()
            assertNotNull(change, "merge reported: ${changes.replayCache}")
            assertEquals(guest.id, change.fromGuestId)
            assertEquals(accountId, change.into.id)
            collector.cancel()
        }
    }

    @Test
    fun `a public call with an expired guest token does not mint a second guest`(): Unit = runBlocking {
        Device("guest-dead-token").use { d ->
            val guest = (d.auth.session.ensureGuest() as StapelResult.Ok).value
            waitPastExpiry()
            // /anonymous/ is public: without the client's proactive refresh the expired bearer would be
            // ignored by the server and a new guest minted (see the inversion below).
            val again = d.api.anonymous(AnonymousAuth(deviceId = d.deviceId))
            assertTrue(again is StapelResult.Ok, "anonymous: ${'$'}again")
            assertEquals(guest.id, again.value.user.id, "same guest back")
        }
        // Inversion: the same call with proactive refresh disabled reaches the server with a dead token.
        Device("guest-dead-token-inverted", proactiveWindow = Duration.ZERO).use { d ->
            val guest = (d.auth.session.ensureGuest() as StapelResult.Ok).value
            waitPastExpiry()
            val again = d.api.anonymous(AnonymousAuth(deviceId = "e2e-other-" + java.util.UUID.randomUUID()))
            assertTrue(again is StapelResult.Ok, "anonymous: ${'$'}again")
            assertNotEquals(guest.id, again.value.user.id, "server silently mints a new guest for a dead bearer (backend finding L6)")
        }
    }

    @Test
    fun `sign out everywhere revokes the other devices and this one`(): Unit = runBlocking {
        val email = Stand.email()
        Device("all-a").use { a ->
            Device("all-b").use { b ->
                signInByEmail(a, email)
                signInByEmail(b, email)
                val p = SignOutPresenter(a.auth, a.context())
                p.dispatch(SignOutIntent.SignOutEverywhere)
                val done = p.state.await { it is SignOutState.Done } as SignOutState.Done
                assertTrue(done.result.othersRevoked)
                assertEquals(LogoutResult.RevokedOnServer, done.result.self)
                assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), a.auth.session.state.await { it is AuthState.SignedOut })

                waitPastExpiry()
                b.auth.session.refreshUser()
                assertEquals(AuthState.SignedOut(SignOutReason.Revoked), b.auth.session.state.await { it is AuthState.SignedOut })
                assertTrue(b.sessions.state.value is SessionState.Unauthenticated)
            }
        }
    }

    @Test
    fun `this device is marked in the sessions list on each device and stays marked across a refresh`(): Unit = runBlocking {
        val email = Stand.email()
        Device("current-a").use { a ->
            Device("current-b").use { b ->
                signInByEmail(a, email)
                signInByEmail(b, email)
                val fromA = (a.auth.devices.load() as StapelResult.Ok).value
                val fromB = (b.auth.devices.load() as StapelResult.Ok).value
                assertEquals(fromA.map { it.id }.toSet(), fromB.map { it.id }.toSet(), "one account, the same two sessions")
                val currentA = fromA.single { it.isCurrent }
                val currentB = fromB.single { it.isCurrent }
                assertNotEquals(currentA.id, currentB.id, "each device sees its own row marked")

                waitPastExpiry()
                assertIs<StapelResult.Ok<*>>(a.auth.session.refreshUser())
                val afterRefresh = (a.auth.devices.load() as StapelResult.Ok).value
                assertEquals(currentA.id, afterRefresh.single { it.isCurrent }.id, "a rotated refresh token keeps the session current")
            }
        }
    }

    @Test
    fun `revoke all others spares this device and signs the other one out`(): Unit = runBlocking {
        val email = Stand.email()
        Device("others-a").use { a ->
            Device("others-b").use { b ->
                signInByEmail(a, email)
                signInByEmail(b, email)
                val sessions = SessionsPresenter(a.auth, a.context())
                sessions.dispatch(SessionsIntent.Refresh)
                sessions.state.await { (it.sessions as? Resource.Ready)?.data?.size == 2 }
                sessions.dispatch(SessionsIntent.RevokeOthers)
                val done = sessions.state.await { !it.revokingOthers && (it.sessions as? Resource.Ready)?.data?.size == 1 }
                assertNull(done.error)
                assertTrue((done.sessions as Resource.Ready).data.single().isCurrent, "the row left is this device")

                val own = a.bareApi.createTokenRefresh(TokenRefreshRequest(a.storedTokens()!!.refreshToken))
                assertIs<StapelResult.Ok<*>>(own, "own session survives revoke-others")
                val theirs = b.bareApi.createTokenRefresh(TokenRefreshRequest(b.storedTokens()!!.refreshToken))
                assertIs<StapelResult.Err<*>>(theirs, "the other device's refresh token is dead")
                val list = (a.auth.devices.load() as StapelResult.Ok).value
                assertEquals(1, list.size, "server keeps one session: $list")
            }
        }
    }

    @Test
    fun `logout without a refresh token still kills the session's refresh token`(): Unit = runBlocking {
        Device("logout-bare").use { d ->
            signInByEmail(d, Stand.email())
            val tokens = d.storedTokens()!!
            // The access token alone identifies the session: no body, no cookie.
            assertIs<StapelResult.Ok<*>>(d.api.logout())
            val reuse = d.bareApi.createTokenRefresh(TokenRefreshRequest(tokens.refreshToken))
            assertIs<StapelResult.Err<*>>(reuse, "refresh after a body-less logout")
            val code = (reuse.error as? TransportCarrier)?.failure?.envelope?.code?.value ?: reuse.error.code.value
            assertEquals("error.401.refresh_revoked", code)
        }
    }

    companion object {
        /** Server-side: no request of this class carried a Cookie header. */
        @JvmStatic
        @AfterAll
        fun noCookieEverReachedTheServer() {
            val probe = Stand.probe()
            assertTrue(probe["requests"].toString().toInt() > 0, "the probe saw the suite's requests: ${'$'}probe")
            assertEquals(0, Stand.cookieRequests(), "requests with a Cookie header: ${'$'}probe")
        }

        const val REFRESH = "auth_api_v1_token_refresh_create"
        const val ME = "auth_api_v1_me_retrieve"
        const val ANONYMOUS = "auth_api_v1_anonymous_create"
    }
}
