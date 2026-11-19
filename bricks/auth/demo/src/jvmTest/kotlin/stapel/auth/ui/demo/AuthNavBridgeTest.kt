package stapel.auth.ui.demo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import stapel.auth.ui.AuthNavIds
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.NavPolicy
import stapel.navigation.NavState
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.StapelNavigator
import stapel.navigation.NavIcons
import stapel.navigation.compose.navContribution
import stapel.navigation.resolveNav
import stapel.session.SessionEnd
import stapel.session.SessionKind
import stapel.session.TokenPair
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A second step of the sign-in flow (a multi-screen sign-in an app adds to the auth flow). */
@Serializable
data object CodeStepRoute : Route

/**
 * [stapel.auth.ui.AuthNavBridge] against the real navigator and session manager, no backend: what it
 * remembers on an involuntary sign-out and restores after the next sign-in.
 */
class AuthNavBridgeTest {
    private fun access(exp: Long = 4_000_000_000L): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"none"}""".toByteArray()) + "." +
            enc.encodeToString("""{"user_id":"u1","exp":$exp}""".toByteArray()) + ".sig"
    }

    private val codeStep = navContribution("test:code-step", "0") {
        destination<CodeStepRoute>(
            id = "test.login_code", path = "/login/code", labelKey = "test.login_code", icon = NavIcons.Login,
            codec = PathCodec.constant(CodeStepRoute), menuVisibleDefault = false, requiresAuth = false, flow = AuthNavIds.SignInFlow,
        ) {}
    }

    private fun NavState.allRoutes(): List<Route> = (root + overlays + tabStacks.values.flatten()).map { it.route }

    private suspend fun StapelNavigator.await(predicate: (NavState) -> Boolean): NavState =
        withTimeout(10.seconds) { state.first(predicate) }

    @Test
    fun anInvoluntarySignOutWithACodeStepOpenRestoresTheScreensAndNoAuthStepSurvives(): Unit = runBlocking {
        DemoStack("http://127.0.0.1:9/", "bridge").use { stack ->
            val contributions = listOf(boundAuthContribution(stack.auth), demoContribution(stack.auth), codeStep)
            val nav = resolveNav(contributions.map { it.manifest })
            val navigator = StapelNavigator(nav, NavPolicy(signIn = AuthNavIds.Login, home = DestinationId("demo.home")), stack.navBridge, stack.scope)
            stack.navBridge.attach(navigator)
            stack.auth.session.restore()

            // A guest on the home screen opens the multi-screen sign-in to upgrade: login, then the code step.
            stack.sessions.adopt(TokenPair(access(), "r-guest"), SessionKind.Anonymous)
            navigator.await { it.auth == NavAuthState.Guest }
            navigator.navigate(stapel.auth.ui.SignInRoute())
            navigator.navigate(CodeStepRoute)
            assertEquals(CodeStepRoute, navigator.state.value.top?.route, "code step open: ${navigator.state.value}")

            // The guest session is revoked server-side while the code step is open.
            stack.sessions.sessionLost(SessionEnd.Revoked)
            navigator.await { it.auth == NavAuthState.Anonymous }
            val remembered = withTimeout(10.seconds) { stack.navBridge.returnTo.first { it != null } }!!
            assertTrue(HomeRoute in remembered, "the protected screen is remembered: $remembered")
            assertTrue(remembered.none { it is CodeStepRoute || it is stapel.auth.ui.SignInRoute }, "no sign-in step is remembered: $remembered")

            // Sign in again: the remembered screens come back and no auth step is left on any stack.
            stack.sessions.adopt(TokenPair(access(), "r-member"), SessionKind.Authenticated)
            val back = navigator.await { it.auth == NavAuthState.Member && it.top?.route == HomeRoute }
            assertTrue(
                back.allRoutes().none { it is CodeStepRoute || it is stapel.auth.ui.SignInRoute },
                "an auth step survived: ${back.allRoutes()}",
            )
            assertEquals(null, stack.navBridge.returnTo.value)
        }
    }
}
