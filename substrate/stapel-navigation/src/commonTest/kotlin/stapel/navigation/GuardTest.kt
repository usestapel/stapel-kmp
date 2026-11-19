package stapel.navigation

import stapel.navigation.NavAuthState.Anonymous
import stapel.navigation.NavAuthState.Guest
import stapel.navigation.NavAuthState.Member
import stapel.navigation.NavAuthState.Unresolved
import kotlin.test.Test
import kotlin.test.assertEquals

class GuardTest {
    private fun target(surface: Surface, requiresAuth: Boolean) = ResolvedDestination(
        spec = null, id = DestinationId("t"), labelKey = "t", shortLabelKey = null, icon = NavIcons.Apps,
        absolutePath = "/t", surface = surface, requiresAuth = requiresAuth, order = 0, menuVisible = true,
        parent = null, children = emptyList(),
    )

    private val signInR = GuardDecision.SignIn(UnauthenticatedMode.Redirect, emptyList())
    private val signInW = GuardDecision.SignIn(UnauthenticatedMode.Wall, emptyList())
    private val allow = GuardDecision.Allow
    private val wait = GuardDecision.Wait
    private val noMandate = GuardDecision.Deny(REASON_NO_MANDATE)
    private val noSignIn = GuardDecision.Deny(REASON_SIGN_IN_UNAVAILABLE)

    private data class Row(val surface: Surface, val requiresAuth: Boolean, val expected: Map<NavAuthState, GuardDecision>)

    /** ARCH-NAV §1.3 guard table; Member surface ignores requiresAuth (the axes are independent). */
    private val table = listOf(
        Row(Surface.Public, false, mapOf(Anonymous to allow, Guest to allow, Member to allow, Unresolved to allow)),
        Row(Surface.Public, true, mapOf(Anonymous to signInR, Guest to allow, Member to allow, Unresolved to wait)),
        Row(Surface.Member, true, mapOf(Anonymous to signInR, Guest to noMandate, Member to allow, Unresolved to wait)),
        Row(Surface.Member, false, mapOf(Anonymous to signInR, Guest to noMandate, Member to allow, Unresolved to wait)),
    )

    @Test
    fun everySurfaceRequiresAuthAndAuthStateCombination() {
        val policy = NavPolicy(signIn = DestinationId("auth.login"))
        var checked = 0
        for (row in table) for ((auth, expected) in row.expected) {
            val actual = DefaultRouteGuard.check(target(row.surface, row.requiresAuth), auth, policy)
            assertEquals(expected, actual, "${row.surface} requiresAuth=${row.requiresAuth} as $auth")
            checked++
        }
        assertEquals(16, checked)
    }

    @Test
    fun wallModeAsksForSignInAsAWall() {
        val policy = NavPolicy(signIn = DestinationId("auth.login"), unauthenticated = UnauthenticatedMode.Wall)
        assertEquals(signInW, DefaultRouteGuard.check(target(Surface.Member, true), Anonymous, policy))
    }

    @Test
    fun withoutASignInDestinationTheGuardDeniesInsteadOfRedirectingNowhere() {
        val policy = NavPolicy(signIn = null)
        assertEquals(noSignIn, DefaultRouteGuard.check(target(Surface.Member, true), Anonymous, policy))
        assertEquals(noSignIn, DefaultRouteGuard.check(target(Surface.Public, true), Anonymous, policy))
        assertEquals(allow, DefaultRouteGuard.check(target(Surface.Public, false), Anonymous, policy))
    }

    @Test
    fun menuVisibilityAgreesWithTheGuard() {
        val policy = NavPolicy(signIn = DestinationId("auth.login"))
        for (row in table) for (auth in NavAuthState.entries) {
            val t = target(row.surface, row.requiresAuth)
            assertEquals(DefaultRouteGuard.check(t, auth, policy) == allow, isOpenTo(t, auth), "${row.surface}/${row.requiresAuth}/$auth")
        }
    }
}
