package stapel.navigation.compose

import kotlinx.serialization.Serializable
import stapel.navigation.DestinationId
import stapel.navigation.FlowId
import stapel.navigation.NavIcons
import stapel.navigation.NavState
import stapel.navigation.NavAuthState
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.SlotId
import stapel.navigation.StackEntry
import stapel.navigation.compose.engine.allEntries
import stapel.navigation.compose.engine.ownerKey
import stapel.navigation.resolveNav
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@Serializable data object LoginStep : Route
@Serializable data object CodeStep : Route
@Serializable data object Inbox : Route

class ContributionTest {
    private val login: ScreenContent = { }
    private val contribution = navContribution("test:auth-ui", "1") {
        destination<LoginStep>(id = "a.login", path = "/login", labelKey = "l", icon = NavIcons.Login, codec = PathCodec.constant(LoginStep), requiresAuth = false, flow = FlowId("a.sign_in")) { }
        destination<CodeStep>(id = "a.code", path = "/login/code", labelKey = "c", icon = NavIcons.Login, codec = PathCodec.constant(CodeStep), requiresAuth = false, flow = FlowId("a.sign_in")) { }
        destination<Inbox>(id = "a.inbox", path = "inbox", labelKey = "i", icon = NavIcons.Message, codec = PathCodec.constant(Inbox)) { }
        embed(id = "a.menu", slot = SlotId("topbar.end"), order = 1) { }
    }

    @Test
    fun theBuilderProducesTheManifestAndBindsEveryScreen() {
        assertEquals(listOf("a.login", "a.code", "a.inbox"), contribution.manifest.destinations.map { it.id.value })
        assertEquals(LoginStep::class, contribution.manifest.destinations.first().routeClass)
        assertEquals(setOf("a.login", "a.code", "a.inbox"), contribution.screens.keys.map { it.value }.toSet())
        assertEquals(listOf("a.menu"), contribution.manifest.embeds.map { it.id.value })
    }

    @Test
    fun overridingReplacesOneScreenAndRefusesAnUnknownId() {
        val replaced = contribution.overriding(DestinationId("a.login"), login)
        assertSame(login, replaced.screens[DestinationId("a.login")])
        assertSame(contribution.manifest, replaced.manifest)
        assertFailsWith<IllegalArgumentException> { contribution.overriding(DestinationId("a.nope"), login) }
    }

    @Test
    fun destinationsOfOneFlowShareOneOwnerAndOthersGetTheirOwn() {
        val nav = resolveNav(listOf(contribution.manifest))
        assertEquals("flow:a.sign_in", ownerKey(nav, StackEntry("e1", LoginStep)))
        assertEquals("flow:a.sign_in", ownerKey(nav, StackEntry("e2", CodeStep)))
        assertEquals("entry:e3", ownerKey(nav, StackEntry("e3", Inbox)))
        val s = NavState(
            auth = NavAuthState.Member, root = listOf(StackEntry("e1", LoginStep)), selectedTab = DestinationId("a.inbox"),
            tabStacks = mapOf(DestinationId("a.inbox") to listOf(StackEntry("e3", Inbox))),
        )
        assertEquals(setOf("e1", "e3"), s.allEntries().map { it.uid }.toSet(), "entries of every stack, not only the visible ones")
    }
}
