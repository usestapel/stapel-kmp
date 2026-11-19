@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigatorTest {
    private fun TestScope.owned(): CoroutineScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))

    private val links = DeepLinkResolver(testNav, DeepLinkConfig(setOf("demoapp"), setOf("demo.example")))

    @Test
    fun followsTheAuthSourceAndReportsRefusals() = runTest {
        val scope = owned()
        val auth = MutableStateFlow(NavAuthState.Unresolved)
        val n = StapelNavigator(testNav, redirectPolicy, { auth }, scope, links)
        assertTrue(n.state.value.visible.isEmpty())
        n.navigate(ThreadRoute("5"))
        assertEquals(GuardDecision.Wait, n.lastRefusal.value)
        auth.value = NavAuthState.Member
        advanceUntilIdle()
        assertEquals(ThreadRoute("5"), n.state.value.top?.route)
        assertNull(n.lastRefusal.value)
        auth.value = NavAuthState.Anonymous
        advanceUntilIdle()
        assertEquals(LoginRoute, n.state.value.top?.route)
        scope.cancel()
    }

    @Test
    fun backReportsWhetherItHandledTheEvent() = runTest {
        val scope = owned()
        val n = StapelNavigator(testNav, redirectPolicy, { MutableStateFlow(NavAuthState.Member) }, scope)
        assertFalse(n.back(), "nothing to pop: the platform closes the app")
        n.navigate(ThreadRoute("1"))
        assertTrue(n.back())
        assertTrue(n.backTo(DestinationId("chat.inbox")).not(), "already there")
        scope.cancel()
    }

    @Test
    fun openUsesDeepLinksAndRefusesForeignLinks() = runTest {
        val scope = owned()
        val n = StapelNavigator(testNav, redirectPolicy, { MutableStateFlow(NavAuthState.Member) }, scope, links)
        assertTrue(n.open("https://demo.example/app/settings/security"))
        assertEquals(listOf(SettingsRoute, SecurityRoute), n.state.value.visibleRoutes())
        assertFalse(n.open("https://elsewhere.example/app/chat"))
        scope.cancel()
    }

    @Test
    fun aResultReachesTheDestinationBelowExactlyOnce() = runTest {
        val scope = owned()
        val key = ResultKey("picked", String.serializer())
        val n = StapelNavigator(testNav, redirectPolicy, { MutableStateFlow(NavAuthState.Member) }, scope)
        n.navigate(ThreadRoute("1"))
        n.navigate(ItemRoute("picker"))
        n.backWithResult(key, "blue") // delivered before anybody collects: buffered, not lost
        assertEquals(listOf(InboxRoute, ThreadRoute("1")), n.state.value.visibleRoutes())
        assertEquals("blue", n.results(key).first())
        val later = mutableListOf<String>()
        val job = launch { n.results(key).collect { later += it } }
        advanceUntilIdle()
        assertTrue(later.isEmpty(), "never replayed")
        job.cancel()
        scope.cancel()
    }

    @Test
    fun aRestoredStateIsReconciledWithTheLiveAuthState() = runTest {
        val scope = owned()
        val saved = reduceAll(initialNavState(testNav, redirectPolicy, NavAuthState.Member), redirectPolicy, NavCommand.Push(ThreadRoute("3"))).state
        val restored = NavStateCodec(listOf(authManifest, appManifest)).let { it.decode(it.encode(saved)) }
        val n = StapelNavigator(testNav, redirectPolicy, { MutableStateFlow(NavAuthState.Anonymous) }, scope, initial = restored)
        assertIs<LoginRoute>(n.state.value.top?.route, "the session ended while the process was dead")
        scope.cancel()
    }

    @Test
    fun externalUrisAreBufferedUntilCollected() = runTest {
        ExternalUris.offer("demoapp://feed")
        ExternalUris.offer("demoapp://app/chat")
        val got = mutableListOf<String>()
        val job = launch { ExternalUris.incoming.collect { got += it } }
        advanceUntilIdle()
        assertEquals(listOf("demoapp://feed", "demoapp://app/chat"), got)
        job.cancel()
    }
}
