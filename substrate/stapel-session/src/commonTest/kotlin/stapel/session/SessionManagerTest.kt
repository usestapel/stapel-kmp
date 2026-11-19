package stapel.session

import stapel.testing.FakeConnectivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.network.RefreshVerdict
import stapel.runtime.NetworkStatus
import stapel.session.vault.StoredSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private class Harness(
        scope: TestScope,
        initial: StoredSession? = StoredSession(pair("A"), SessionKind.Authenticated),
        answer: suspend (TokenPair) -> RefreshOutcome = { RefreshOutcome.Rotated(pair("B")) },
        config: SessionConfig = SessionConfig(),
    ) {
        val clock = FakeClock()
        val vault = RecordingVault(initial)
        val refresher = ScriptedRefresher(answer)
        val connectivity = FakeConnectivity()
        val telemetry = RecordingTelemetry()
        val manager = SessionManager(
            SessionDependencies(vault, scope.backgroundScope, clock, connectivity, telemetry = telemetry),
            refresher,
            config,
        )
    }

    // Rule 1 ---------------------------------------------------------------------------------

    @Test
    fun bornInitializingAndNothingReadsNoSessionBeforeRestore() = runTest {
        val h = Harness(this)
        assertEquals(SessionState.Initializing, h.manager.state.value)
        val token = async { h.manager.accessToken() }
        advanceUntilIdle()
        assertFalse(token.isCompleted, "accessToken() answered before the vault was read")
        h.manager.restore()
        assertEquals("access-A", token.await())
        assertEquals(SessionState.Authenticated(1), h.manager.state.value)
    }

    @Test
    fun emptyVaultSettlesUnauthenticatedWithoutCause() = runTest {
        val h = Harness(this, initial = null)
        h.manager.restore()
        assertEquals(SessionState.Unauthenticated(null), h.manager.state.value)
        assertNull(h.manager.accessToken())
    }

    // Rule 8 ---------------------------------------------------------------------------------

    @Test
    fun unreadableVaultIsStorageLostAndCleared() = runTest {
        val h = Harness(this)
        h.vault.unreadable = "key invalidated"
        h.manager.restore()
        assertEquals(SessionState.Unauthenticated(SessionEnd.StorageLost), h.manager.state.value)
        assertTrue("clear" in h.vault.log)
        assertNull(h.vault.record)
    }

    // Rule 2 ---------------------------------------------------------------------------------

    @Test
    fun concurrentRefreshesShareOneCall() = runTest {
        val h = Harness(this)
        h.manager.restore()
        h.refresher.gate = CompletableDeferred()
        val callers = List(50) { async { h.manager.refresh("access-A") } }
        runCurrent()
        h.refresher.gate!!.complete(Unit)
        val verdicts = callers.awaitAll()
        assertEquals(1, h.refresher.calls, "N concurrent refreshes must make exactly one call")
        assertTrue(verdicts.all { it == RefreshVerdict.Refreshed("access-B") })
    }

    @Test
    fun sequentialRefreshesOfTheCurrentTokenEachCall() = runTest {
        // Inversion of single-flight: once a refresh finished, a 401 on the NEW token refreshes again.
        val h = Harness(this, answer = { RefreshOutcome.Rotated(pair(if (it.accessToken == "access-A") "B" else "C")) })
        h.manager.restore()
        assertEquals(RefreshVerdict.Refreshed("access-B"), h.manager.refresh("access-A"))
        assertEquals(RefreshVerdict.Refreshed("access-C"), h.manager.refresh("access-B"))
        assertEquals(2, h.refresher.calls)
    }

    // Rule 3 ---------------------------------------------------------------------------------

    @Test
    fun aFailedTokenThatIsNoLongerCurrentIsAnsweredWithoutACall() = runTest {
        val h = Harness(this)
        h.manager.restore()
        assertEquals(RefreshVerdict.Refreshed("access-A"), h.manager.refresh("access-OLD"))
        assertEquals(0, h.refresher.calls)
        // Inversion: the current token does refresh.
        assertEquals(RefreshVerdict.Refreshed("access-B"), h.manager.refresh("access-A"))
        assertEquals(1, h.refresher.calls)
    }

    // Rule 4 ---------------------------------------------------------------------------------

    @Test
    fun rotatedPairIsStoredAndUsed() = runTest {
        val h = Harness(this)
        h.manager.restore()
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { h.manager.events.collect { events += it } }
        runCurrent()
        assertEquals(RefreshVerdict.Refreshed("access-B"), h.manager.refresh("access-A"))
        assertEquals("refresh-B", h.vault.record!!.tokens.refreshToken)
        assertNull(h.vault.record!!.rotationStartedAt)
        assertEquals("access-B", h.manager.accessToken())
        assertEquals(SessionState.Authenticated(1), h.manager.state.value, "a refresh is not a new generation")
        runCurrent()
        assertEquals(listOf<SessionEvent>(SessionEvent.Refreshed), events)
    }

    @Test
    fun rejectedRefreshTearsDown() = runTest {
        val h = Harness(this, answer = { RefreshOutcome.Rejected(SessionEnd.Revoked, "error.401.refresh_revoked") })
        h.manager.restore()
        val hookCauses = mutableListOf<SessionEnd>()
        h.manager.registerLogoutHook { hookCauses += it }
        assertEquals(RefreshVerdict.SessionEnded, h.manager.refresh("access-A"))
        assertEquals(SessionState.Unauthenticated(SessionEnd.Revoked), h.manager.state.value)
        assertNull(h.vault.record)
        assertEquals(listOf(SessionEnd.Revoked), hookCauses)
        assertNull(h.manager.accessToken())
    }

    @Test
    fun transientRefreshNeverSignsOut() = runTest {
        val h = Harness(this, answer = { RefreshOutcome.Transient("502") })
        h.manager.restore()
        val hookCalls = mutableListOf<SessionEnd>()
        h.manager.registerLogoutHook { hookCalls += it }
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { h.manager.events.collect { events += it } }
        runCurrent()
        assertEquals(RefreshVerdict.Unavailable, h.manager.refresh("access-A"))
        assertEquals(SessionState.Authenticated(1), h.manager.state.value)
        assertEquals("refresh-A", h.vault.record!!.tokens.refreshToken)
        assertNull(h.vault.record!!.rotationStartedAt, "the marker is cleared after a transient outcome")
        assertEquals("access-A", h.manager.accessToken())
        assertTrue(hookCalls.isEmpty())
        runCurrent()
        assertEquals(listOf<SessionEvent>(SessionEvent.RefreshUnavailable), events)
    }

    @Test
    fun aThrowingRefresherIsTransient() = runTest {
        val h = Harness(this, answer = { throw IllegalStateException("socket closed") })
        h.manager.restore()
        assertEquals(RefreshVerdict.Unavailable, h.manager.refresh("access-A"))
        assertEquals(SessionState.Authenticated(1), h.manager.state.value)
    }

    // Rule 5 ---------------------------------------------------------------------------------

    @Test
    fun rotatedPairIsPersistedBeforeAnyWaiterReceivesIt() = runTest {
        val h = Harness(this)
        h.manager.restore()
        h.vault.holdWriteOf = "access-B"
        val waiter = async { h.manager.refresh("access-A") }
        advanceUntilIdle()
        assertFalse(waiter.isCompleted, "a waiter received the new token before it was persisted")
        assertEquals("refresh-A", h.vault.record!!.tokens.refreshToken)
        h.vault.writeGate.complete(Unit)
        assertEquals(RefreshVerdict.Refreshed("access-B"), waiter.await())
        assertEquals(listOf("read", "write:refresh-A:marker", "write:refresh-B:clean"), h.vault.log)
    }

    @Test
    fun markerIsWrittenBeforeTheRefreshCall() = runTest {
        lateinit var h: Harness
        var markerAtCall: Boolean? = null
        h = Harness(this, answer = {
            markerAtCall = h.vault.record?.rotationStartedAt != null
            RefreshOutcome.Rotated(pair("B"))
        })
        h.manager.restore()
        h.manager.refresh("access-A")
        assertEquals(true, markerAtCall)
    }

    @Test
    fun youngMarkerOnStartIsWaitedOutOnceAndReRead() = runTest {
        val clock = FakeClock()
        val h = Harness(this, config = SessionConfig(rotationHandoffWindow = 3.seconds))
        h.clock.set(clock.now())
        h.vault.record = StoredSession(pair("A"), SessionKind.Authenticated, rotationStartedAt = clock.now() - 1.seconds)
        val restore = async { h.manager.restore() }
        advanceTimeBy(1.seconds)
        assertFalse(restore.isCompleted)
        // The other process finishes its rotation while we wait.
        h.vault.record = StoredSession(pair("B"), SessionKind.Authenticated)
        advanceTimeBy(1.5.seconds)
        restore.await()
        assertEquals("access-B", h.manager.accessToken())
    }

    @Test
    fun oldMarkerMeansInterruptedRotationAndIsMeasuredWhenRevoked() = runTest {
        val h = Harness(this, answer = { RefreshOutcome.Rejected(SessionEnd.Revoked) })
        h.vault.record = StoredSession(pair("A"), SessionKind.Authenticated, rotationStartedAt = h.clock.now() - 60.seconds)
        h.manager.restore()
        assertEquals(SessionState.Authenticated(1), h.manager.state.value, "the next refresh decides, not the marker")
        h.manager.refresh("access-A")
        assertEquals(listOf("rotation_interrupted"), h.telemetry.events.map { it.name })
    }

    // Rule 6 ---------------------------------------------------------------------------------

    @Test
    fun logoutClearsVaultThenRunsEveryHookThenPublishes() = runTest {
        val h = Harness(this)
        h.manager.restore()
        val seen = mutableListOf<String>()
        h.manager.registerLogoutHook {
            seen += "hook1 vault=${h.vault.record != null} state=${h.manager.state.value::class.simpleName}"
            throw IllegalStateException("hook failure must not stop the rest")
        }
        val handle = h.manager.registerLogoutHook { seen += "hook2 $it" }
        h.manager.registerLogoutHook { seen += "hook3" }
        h.manager.logout()
        assertEquals(listOf("hook1 vault=false state=Authenticated", "hook2 Logout", "hook3"), seen)
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
        assertNull(h.manager.accessToken())

        handle.close()
        seen.clear()
        h.manager.adopt(pair("C"), SessionKind.Authenticated)
        h.manager.logout()
        assertEquals(2, seen.size, "a closed hook handle unregisters: $seen")
    }

    @Test
    fun sessionLostDuringExplicitLogoutIsANoOp() = runTest {
        val h = Harness(this)
        h.manager.restore()
        var lost: Boolean? = null
        h.manager.registerLogoutHook { lost = h.manager.sessionLost(SessionEnd.Revoked) }
        h.manager.logout()
        assertEquals(false, lost)
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
        // Inversion: outside a logout, sessionLost ends a live session.
        h.manager.adopt(pair("C"), SessionKind.Authenticated)
        assertTrue(h.manager.sessionLost(SessionEnd.Revoked))
        assertEquals(SessionState.Unauthenticated(SessionEnd.Revoked), h.manager.state.value)
    }

    @Test
    fun aRotationLandingAfterLogoutIsDiscarded() = runTest {
        val h = Harness(this)
        h.manager.restore()
        h.refresher.gate = CompletableDeferred()
        val refresh = async { h.manager.refresh("access-A") }
        runCurrent()
        h.manager.logout()
        h.refresher.gate!!.complete(Unit)
        assertEquals(RefreshVerdict.SessionEnded, refresh.await())
        assertNull(h.vault.record, "a late rotation resurrected a signed-out session")
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
    }

    @Test
    fun adoptPersistsThenPublishesANewGeneration() = runTest {
        val h = Harness(this, initial = null)
        h.manager.restore()
        h.manager.adopt(pair("G"), SessionKind.Anonymous)
        assertEquals(SessionState.Anonymous(1), h.manager.state.value)
        assertEquals("refresh-G", h.vault.record!!.tokens.refreshToken)
        h.manager.adopt(pair("U"), SessionKind.Authenticated)
        assertEquals(SessionState.Authenticated(2), h.manager.state.value)
    }

    // Rule 7 ---------------------------------------------------------------------------------

    @Test
    fun accessTokenRefreshesProactivelyBeforeExpiry() = runTest {
        val clock = FakeClock()
        val h = Harness(
            this,
            initial = StoredSession(pair("A", exp = clock.now() + 10.seconds), SessionKind.Authenticated),
            answer = { RefreshOutcome.Rotated(pair("B", exp = clock.now() + 300.seconds)) },
        )
        h.clock.set(clock.now())
        h.manager.restore()
        assertEquals(jwt("B", clock.now() + 300.seconds), h.manager.accessToken())
        assertEquals(1, h.refresher.calls)
        // Inversion: a token far from expiry is not refreshed.
        assertEquals(jwt("B", clock.now() + 300.seconds), h.manager.accessToken())
        assertEquals(1, h.refresher.calls)
    }

    @Test
    fun aShortLivedTokenIsNotRefreshedBeforeEveryRequest() = runTest {
        val start = FakeClock().now()
        val short = TokenPair(jwt("A", iat = start, exp = start + 5.seconds), "refresh-A")
        val h = Harness(
            this,
            initial = StoredSession(short, SessionKind.Authenticated),
            answer = { RefreshOutcome.Rotated(TokenPair(jwt("B", iat = start + 4.seconds, exp = start + 9.seconds), "refresh-B")) },
        )
        h.clock.set(start)
        h.manager.restore()
        // Inversion of the unclamped 30 s window, which is longer than the whole 5 s lifetime.
        assertEquals(short.accessToken, h.manager.accessToken())
        h.clock.set(start + 3.seconds)
        assertEquals(short.accessToken, h.manager.accessToken())
        assertEquals(0, h.refresher.calls, "a 5 s token refreshed with more than a quarter of its life left")
        // Inside the last quarter (1.25 s) it refreshes.
        h.clock.set(start + 4.seconds)
        assertEquals(jwt("B", iat = start + 4.seconds, exp = start + 9.seconds), h.manager.accessToken())
        assertEquals(1, h.refresher.calls)
    }

    @Test
    fun theProactiveWindowIsClampedToAQuarterOfTheLifetime() {
        val now = FakeClock().now()
        val longToken = jwt("L", iat = now, exp = now + 15.minutes)
        assertEquals(30.seconds, effectiveProactiveWindow(30.seconds, longToken, now + 15.minutes))
        val shortToken = jwt("S", iat = now, exp = now + 8.seconds)
        assertEquals(2.seconds, effectiveProactiveWindow(30.seconds, shortToken, now + 8.seconds))
        // Without `iat` the lifetime is unknown: the configured window stands.
        assertEquals(30.seconds, effectiveProactiveWindow(30.seconds, jwt("X", now + 8.seconds), now + 8.seconds))
    }

    @Test
    fun noProactiveRefreshWhileOffline() = runTest {
        val clock = FakeClock()
        val h = Harness(this, initial = StoredSession(pair("A", exp = clock.now() + 10.seconds), SessionKind.Authenticated))
        h.clock.set(clock.now())
        h.connectivity.set(NetworkStatus.Offline)
        h.manager.restore()
        assertEquals(jwt("A", clock.now() + 10.seconds), h.manager.accessToken())
        assertEquals(0, h.refresher.calls)
    }

    @Test
    fun proactiveRefreshWithoutVerdictKeepsTheCurrentToken() = runTest {
        val clock = FakeClock()
        val h = Harness(
            this,
            initial = StoredSession(pair("A", exp = clock.now() + 10.seconds), SessionKind.Authenticated),
            answer = { RefreshOutcome.Transient("timeout") },
        )
        h.clock.set(clock.now())
        h.manager.restore()
        assertEquals(jwt("A", clock.now() + 10.seconds), h.manager.accessToken())
        assertEquals(SessionState.Authenticated(1), h.manager.state.value)
    }

    @Test
    fun jwtExpiryIsReadWithoutVerification() {
        val exp = FakeClock().now() + 42.milliseconds
        assertEquals(exp.epochSeconds, jwtExpiry(jwt("x", exp))!!.epochSeconds)
        assertNull(jwtExpiry("not-a-jwt"))
        assertNull(jwtExpiry("a.%%%.c"))
        assertIs<TokenPair>(pair("A"))
        assertEquals("TokenPair(***)", pair("A").toString())
    }
}
