package stapel.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import stapel.network.RefreshVerdict
import stapel.session.vault.StoredSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FarewellHookTest {
    private class Harness(
        scope: TestScope,
        answer: suspend (TokenPair) -> RefreshOutcome = { RefreshOutcome.Rotated(pair("B")) },
        config: SessionConfig = SessionConfig(),
    ) {
        val vault = RecordingVault(StoredSession(pair("A"), SessionKind.Authenticated))
        val refresher = ScriptedRefresher(answer)
        val manager = SessionManager(SessionDependencies(vault, scope.backgroundScope, FakeClock()), refresher, config)
    }

    @Test
    fun farewellRunsWhileTheTokensAreValidAndBeforeEveryLogoutHook() = runTest {
        val h = Harness(this)
        h.manager.restore()
        val seen = mutableListOf<String>()
        h.manager.registerLogoutHook { seen += "logout-hook vault=${h.vault.record != null}" }
        h.manager.registerFarewellHook {
            seen += "farewell token=${h.manager.accessToken()} vault=${h.vault.record != null} " +
                "state=${h.manager.state.value::class.simpleName}"
        }
        h.manager.logout()
        assertEquals(
            listOf("farewell token=access-A vault=true state=Authenticated", "logout-hook vault=false"),
            seen,
        )
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
    }

    @Test
    fun aThrowingHookDoesNotStopTheOthersOrTheSignOut() = runTest {
        val h = Harness(this)
        h.manager.restore()
        val seen = mutableListOf<String>()
        h.manager.registerFarewellHook { throw IllegalStateException("boom") }
        h.manager.registerFarewellHook { seen += "second" }
        h.manager.logout()
        assertEquals(listOf("second"), seen)
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
    }

    @Test
    fun allHooksShareOneBudgetAndASlowHookIsCutOff() = runTest {
        val h = Harness(this, config = SessionConfig(farewellTimeout = 3.seconds))
        h.manager.restore()
        var cancelled = false
        val fast = CompletableDeferred<Unit>()
        h.manager.registerFarewellHook {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        h.manager.registerFarewellHook { fast.complete(Unit) }
        val start = currentTime
        h.manager.logout()
        assertEquals(3_000L, currentTime - start, "the sign-out waits exactly the farewell budget, not forever")
        assertTrue(cancelled, "the slow hook is cancelled at the budget")
        assertTrue(fast.isCompleted, "hooks run concurrently: the fast one is not queued behind the slow one")
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
    }

    @Test
    fun notCalledWhenTheSessionEndsWithoutASignOut() = runTest {
        val h = Harness(this, answer = { RefreshOutcome.Rejected(SessionEnd.Revoked) })
        h.manager.restore()
        var calls = 0
        h.manager.registerFarewellHook { calls++ }
        assertEquals(RefreshVerdict.SessionEnded, h.manager.refresh("access-A"))
        assertEquals(SessionState.Unauthenticated(SessionEnd.Revoked), h.manager.state.value)
        h.manager.adopt(pair("C"), SessionKind.Authenticated)
        h.manager.sessionLost(SessionEnd.Expired)
        assertEquals(0, calls)
        // Inversion: an explicit sign-out does call it.
        h.manager.adopt(pair("D"), SessionKind.Authenticated)
        h.manager.logout()
        assertEquals(1, calls)
    }

    @Test
    fun atMostOncePerSessionAndAgainForTheNextOne() = runTest {
        val h = Harness(this)
        h.manager.restore()
        var calls = 0
        val handle = h.manager.registerFarewellHook { calls++ }
        h.manager.farewell()
        h.manager.farewell()
        h.manager.logout()
        assertEquals(1, calls, "logout() after an explicit farewell() does not say goodbye twice")
        h.manager.adopt(pair("C"), SessionKind.Authenticated)
        h.manager.logout()
        assertEquals(2, calls, "a new session gets its own farewell")
        handle.close()
        h.manager.adopt(pair("D"), SessionKind.Authenticated)
        h.manager.logout()
        assertEquals(2, calls, "a closed handle unregisters")
        h.manager.farewell()
        assertEquals(2, calls, "no session: nothing to say goodbye to")
    }

    @Test
    fun aRefreshRejectedDuringTheFarewellStillEndsAsLogout() = runTest {
        val h = Harness(this, answer = { RefreshOutcome.Rejected(SessionEnd.Revoked) })
        h.manager.restore()
        val causes = mutableListOf<SessionEnd>()
        h.manager.registerLogoutHook { causes += it }
        var verdict: RefreshVerdict? = null
        var lost: Boolean? = null
        h.manager.registerFarewellHook {
            verdict = h.manager.refresh("access-A")
            lost = h.manager.sessionLost(SessionEnd.Revoked)
        }
        h.manager.logout()
        assertEquals(RefreshVerdict.SessionEnded, verdict)
        assertEquals(false, lost)
        assertEquals(listOf(SessionEnd.Logout), causes, "the sign-out, not the rejected refresh, ends the session")
        assertEquals(SessionState.Unauthenticated(SessionEnd.Logout), h.manager.state.value)
    }
}
