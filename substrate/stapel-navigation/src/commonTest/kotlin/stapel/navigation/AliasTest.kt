package stapel.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable data object AlInbox : Route
@Serializable data class AlThread(val threadId: String) : Route
@Serializable data object AlPromo : Route
@Serializable data object AlLogin : Route

private val threadCodec = PathCodec<AlThread>({ p, _ -> p["threadId"]?.let(::AlThread) }, { RouteAddress(path = mapOf("threadId" to it.threadId)) })

private fun inbox(aliases: List<String> = emptyList()) = DestinationSpec(
    DestinationId("al.inbox"), AlInbox::class, AlInbox.serializer(), PathPattern("chat"), PathCodec.constant(AlInbox),
    "inbox", NavIcons.Message, Placement.Top, true, true, 1, aliases = aliases.map(::PathPattern),
)

private fun thread(aliases: List<String>) = DestinationSpec(
    DestinationId("al.thread"), AlThread::class, AlThread.serializer(), PathPattern("t/:threadId"), threadCodec,
    "thread", NavIcons.Message, Placement.Submenu(DestinationId("al.inbox")), false, true, 1, aliases = aliases.map(::PathPattern),
)

private fun promo(aliases: List<String>) = DestinationSpec(
    DestinationId("al.promo"), AlPromo::class, AlPromo.serializer(), PathPattern("/promo"), PathCodec.constant(AlPromo),
    "promo", NavIcons.Tag, Placement.Top, false, false, 5, surface = Surface.Public, aliases = aliases.map(::PathPattern),
)

private val login = DestinationSpec(
    DestinationId("al.login"), AlLogin::class, AlLogin.serializer(), PathPattern("/login"), PathCodec.constant(AlLogin),
    "login", NavIcons.Login, Placement.Top, false, false, 0,
)

private fun chatUi(threadAliases: List<String> = listOf("/t/:threadId", "/m/:threadId")) =
    NavManifest("test:chat-ui", "0", listOf(inbox(), thread(threadAliases)))

private fun promoUi(aliases: List<String> = listOf("/p")) = NavManifest("test:promo-ui", "0", listOf(promo(aliases), login))

class AliasTest {
    private val nav = resolveNav(listOf(chatUi(), promoUi()))
    private val links = DeepLinkResolver(nav, DeepLinkConfig(setOf("demoapp"), setOf("demo.example")))
    private val policy = NavPolicy(signIn = DestinationId("al.login"))

    @Test
    fun anAliasOpensTheSameStackAsTheCanonicalLink() {
        val canonical = links.resolve("https://demo.example/app/chat/t/42")
        assertEquals(DeepLinkMatch(DestinationId("al.thread"), listOf(AlInbox, AlThread("42"))), canonical)
        assertEquals(canonical, links.resolve("https://demo.example/t/42"))
        assertEquals(canonical, links.resolve("demoapp://m/42"))
        assertEquals(listOf<Route>(AlPromo), links.resolve("demoapp://p")?.stack)
        assertNull(links.resolve("https://demo.example/t"), "an alias matches like any path pattern: segment count first")
    }

    @Test
    fun theCanonicalAddressNeverChanges() {
        assertEquals("https://demo.example/app/chat/t/42", links.uriFor(AlThread("42")))
        assertEquals("demoapp://promo", links.uriFor(AlPromo, scheme = "demoapp"))
        assertEquals(mapOf("threadId" to "42"), nav.addressOf(AlThread("42"))?.path)
        assertEquals("/app/chat/t/:threadId", nav.byId.getValue(DestinationId("al.thread")).absolutePath)
    }

    @Test
    fun anAliasLinkPassesTheSameGuard() {
        val anon = initialNavState(nav, policy, NavAuthState.Anonymous)
        val stack = links.resolve("https://demo.example/t/9")!!.stack
        val r = reduceNav(anon, NavCommand.OpenStack(stack), nav, policy)
        assertEquals(GuardDecision.SignIn(UnauthenticatedMode.Redirect, listOf(AlInbox, AlThread("9"))), r.refused)
        assertEquals(listOf(AlInbox, AlThread("9")), r.state.pendingReturn)
        val member = reduceNav(r.state, NavCommand.AuthChanged(NavAuthState.Member), nav, policy).state
        assertEquals(listOf(AlInbox, AlThread("9")), member.visible.map { it.route })
    }

    @Test
    fun aMoreLiteralCanonicalPathWinsOverAnAlias() {
        // "/app/chat" (canonical, 2 literals) against an alias "/app/:x" of the promo (1 literal).
        val n = resolveNav(listOf(chatUi(emptyList()), promoUi(listOf("/app/:x", "/zz"))))
        val l = DeepLinkResolver(n, DeepLinkConfig(setOf("demoapp"), emptySet()))
        assertEquals(DestinationId("al.inbox"), l.resolve("demoapp://app/chat")?.target)
        assertEquals(DestinationId("al.promo"), l.resolve("demoapp://app/other")?.target)
    }

    @Test
    fun conflictingAliasesAreAContractErrorNamingBothOwners() {
        val err = assertFailsWith<NavContractException> { resolveNav(listOf(chatUi(listOf("/t/:threadId")), promoUi(listOf("/t/:code")))) }
        val line = err.problems.single()
        assertTrue(line.startsWith("al.promo:"), line)
        assertTrue("test:promo-ui" in line && "test:chat-ui" in line && "al.thread" in line, line)
    }

    @Test
    fun anAliasMatchingARoutedPathIsAContractErrorNamingBothOwners() {
        val err = assertFailsWith<NavContractException> { resolveNav(listOf(chatUi(emptyList()), promoUi(listOf("/app/chat")))) }
        val line = err.problems.single()
        assertTrue(line.startsWith("al.promo:") && "test:chat-ui" in line && "al.inbox" in line && "test:promo-ui" in line, line)
        val own = assertFailsWith<NavContractException> { resolveNav(listOf(chatUi(listOf("/app/chat/t/:x")))) }
        assertTrue(own.problems.single().startsWith("al.thread:"), own.problems.single())
    }

    @Test
    fun aRelativeAliasIsRefused() {
        val err = assertFailsWith<NavContractException> { resolveNav(listOf(chatUi(listOf("t/:threadId")))) }
        assertEquals("al.thread: alias 't/:threadId' must be an absolute path", err.problems.single())
    }

    @Test
    fun aliasesAreAMobileOnlyManifestFieldOutsideRoutePath() {
        val json = Json.parseToJsonElement(chatUi().toJson()).jsonObject
        val thread = json.getValue("entries").jsonArray.map { it.jsonObject }.single { it.getValue("id").jsonPrimitive.content == "al.thread" }
        assertEquals("t/:threadId", thread.getValue("route").jsonObject.getValue("path").jsonPrimitive.content)
        assertEquals(listOf("/t/:threadId", "/m/:threadId"), thread.getValue("aliases").jsonArray.map { it.jsonPrimitive.content })
        assertTrue("aliases" in MOBILE_ONLY_ENTRY_FIELDS)
        val withoutAliases = Json.parseToJsonElement(chatUi(emptyList()).toJson()).jsonObject
        assertFalse(withoutAliases.getValue("entries").jsonArray.any { "aliases" in it.jsonObject }, "absent when none are declared")
    }

    @Test
    fun everyFieldBeyondTheWebShapeIsListedAsMobileOnly() {
        val web = setOf("id", "labelKey", "shortLabelKey", "icon", "route", "placement", "menuVisibleDefault", "requiresAuth", "surface", "order")
        val full = NavManifest(
            "test:full", "0",
            listOf(
                DestinationSpec(
                    DestinationId("al.full"), AlInbox::class, AlInbox.serializer(), PathPattern("/full"), PathCodec.constant(AlInbox),
                    "full", NavIcons.Apps, Placement.Top, true, true, 1, surface = Surface.Member, shortLabelKey = "f",
                    presentation = Presentation.Sheet, pane = PaneRole.List, flow = FlowId("f"), pageSlots = setOf(SlotId("page.actions")),
                    requires = setOf(CapabilityKey("camera")), aliases = listOf(PathPattern("/f")),
                ),
            ),
        )
        val keys = Json.parseToJsonElement(full.toJson()).jsonObject.getValue("entries").jsonArray.single().jsonObject.keys
        assertEquals(MOBILE_ONLY_ENTRY_FIELDS, keys - web)
    }
}
