package stapel.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveTest {
    @Test
    fun pathsFollowThePlannerRule() {
        val paths = testNav.byId.mapValues { it.value.absolutePath }.mapKeys { it.key.value }
        assertEquals("/login", paths["auth.login"])
        assertEquals("/feed", paths["feed.home"])
        assertEquals("/app/chat", paths["chat.inbox"])
        assertEquals("/app/chat/t/:threadId", paths["chat.thread"])
        assertEquals("/app/settings/security", paths["auth.security"])
        assertEquals("/l/:id", paths["listings.detail"])
        assertEquals("/app/account", paths["account.root"])
        assertEquals("/app/account/privacy", paths["account.privacy"])
        assertEquals(listOf(DestinationId("account.root")), testNav.containerRoots)
    }

    @Test
    fun anOrphanIsDroppedWithANamedReasonNotSilently() {
        val nav = resolveNav(listOf(authManifest)) // no profiles library: auth.security has no parent
        assertNull(nav.byId[DestinationId("auth.security")])
        assertEquals(listOf(DroppedEntry("auth.security", "parent 'profiles.settings' is not installed")), nav.dropped)
    }

    @Test
    fun theOrphanIsKeptWhenItsParentIsInstalled() {
        assertTrue(testNav.dropped.isEmpty(), "dropped: ${testNav.dropped}")
        assertEquals(DestinationId("profiles.settings"), testNav.byId.getValue(DestinationId("auth.security")).parent)
    }

    @Test
    fun contractErrorsAreRefusedNotDegraded() {
        val dup = NavManifest("test:dup", "0", listOf(authManifest.destinations.first()))
        assertFailsWith<NavContractException> { resolveNav(listOf(authManifest, dup)) }.also {
            assertTrue(it.problems.single().startsWith("auth.login: declared twice"))
        }
        val badIcon = NavManifest(
            "test:icon", "0",
            listOf(dest("x.bad", "bad", HelpRoute.serializer(), PathCodec.constant(HelpRoute), icon = NavIcon("AppstoreOutlined"))),
        )
        assertFailsWith<NavContractException> { resolveNav(listOf(badIcon)) }.also {
            assertTrue(it.problems.single().startsWith("x.bad: icon 'AppstoreOutlined'"))
        }
        val typo = NavManifest(
            "test:typo", "0",
            listOf(dest("x.kid", "kid", HelpRoute.serializer(), PathCodec.constant(HelpRoute), placement = Placement.Submenu(DestinationId("profiles.setings")))),
        )
        val known = setOf(DestinationId("profiles.settings"))
        assertFailsWith<NavContractException> { resolveNav(listOf(typo), knownIds = known) }.also {
            assertTrue(it.problems.single().startsWith("x.kid: placement.parentId 'profiles.setings'"))
        }
        val sameRouteType = NavManifest(
            "test:route", "0",
            listOf(dest("x.other", "/other", LoginRoute.serializer(), PathCodec.constant(LoginRoute))),
        )
        assertFailsWith<NavContractException> { resolveNav(listOf(authManifest, sameRouteType)) }
    }

    @Test
    fun embedsAreCheckedAndOrphanEmbedsDropped() {
        val pageWithoutScope = NavManifest("t:e", "0", emptyList(), listOf(EmbedSpec(EmbedId("e.a"), SlotId("page.actions"), 1)))
        assertFailsWith<NavContractException> { resolveNav(listOf(pageWithoutScope)) }
        val chromeWithScope = NavManifest(
            "t:e", "0", emptyList(),
            listOf(EmbedSpec(EmbedId("e.b"), SlotId("topbar.end"), 1, scope = DestinationId("chat.inbox"))),
        )
        assertFailsWith<NavContractException> { resolveNav(listOf(chromeWithScope)) }
        val embeds = NavManifest(
            "t:e", "0", emptyList(),
            listOf(
                EmbedSpec(EmbedId("e.menu"), SlotId("topbar.end"), 90, opens = DestinationId("account.root")),
                EmbedSpec(EmbedId("e.ghost"), SlotId("topbar.end"), 10, opens = DestinationId("nobody.here")),
                EmbedSpec(EmbedId("e.web"), SlotId("footer"), 10),
            ),
        )
        val nav = resolveNav(listOf(authManifest, appManifest, embeds))
        assertEquals(listOf("e.menu"), nav.embeds.map { it.id.value })
        assertEquals(setOf("e.ghost", "e.web"), nav.dropped.map { it.id }.toSet())
    }

    @Test
    fun overridesChangeOrderVisibilityAndSurface() {
        val overrides = NavOverrides(
            mapOf(
                DestinationId("profiles.settings") to NavOverride(order = 0),
                DestinationId("feed.home") to NavOverride(menuVisible = false),
                DestinationId("chat.inbox") to NavOverride(surface = Surface.Public, requiresAuth = false),
            ),
        )
        val nav = resolveNav(listOf(authManifest, appManifest), overrides)
        assertEquals(listOf("profiles.settings", "chat.inbox", "account.root"), nav.menuFor(NavAuthState.Member).map { it.id.value })
        assertEquals("/chat", nav.byId.getValue(DestinationId("chat.inbox")).absolutePath, "a public relative top is hoisted")
        assertEquals(listOf("chat.inbox"), nav.menuFor(NavAuthState.Anonymous).map { it.id.value })
    }

    @Test
    fun syntheticStackBuildsAncestorsFromTheTargetsOwnParameters() {
        assertEquals(listOf(InboxRoute, ThreadRoute("9")), testNav.syntheticStack(ThreadRoute("9")))
        assertEquals(listOf(SectionRoute(DestinationId("account.root")), PrivacyRoute), testNav.syntheticStack(PrivacyRoute))
        assertEquals(listOf<Route>(ItemRoute("1")), testNav.syntheticStack(ItemRoute("1")))
    }
}
