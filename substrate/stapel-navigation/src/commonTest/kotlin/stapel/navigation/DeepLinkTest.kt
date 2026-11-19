package stapel.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {
    private val config = DeepLinkConfig(schemes = setOf("demoapp"), hosts = setOf("demo.example"), basePath = "")
    private val links = DeepLinkResolver(testNav, config)

    @Test
    fun httpsAndCustomSchemeLinksResolveToTheSameStack() {
        val https = links.resolve("https://demo.example/app/chat/t/42")
        val custom = links.resolve("demoapp://app/chat/t/42")
        assertEquals(DeepLinkMatch(DestinationId("chat.thread"), listOf(InboxRoute, ThreadRoute("42"))), https)
        assertEquals(https, custom)
    }

    @Test
    fun parametersAreDecodedAndQueryReachesTheCodec() {
        val m = links.resolve("https://demo.example/l/a%2Fb%20c?tab=photos&x=1#frag")
        assertEquals(listOf<Route>(ItemRoute("a/b c", "photos")), m?.stack)
        assertEquals(listOf<Route>(QrRoute("k 1")), links.resolve("demoapp://qr-confirm?key=k+1")?.stack)
    }

    @Test
    fun foreignHostsSchemesAndUnknownPathsAreNotOurs() {
        assertNull(links.resolve("https://evil.example/app/chat"))
        assertNull(links.resolve("other://app/chat"))
        assertNull(links.resolve("https://demo.example/nowhere"))
        assertNull(links.resolve("not a uri"))
        assertNull(links.resolve("demoapp://qr-confirm"), "the codec refuses a missing required query parameter")
    }

    @Test
    fun basePathIsStrippedFromHttpsLinksOnly() {
        val prefixed = DeepLinkResolver(testNav, DeepLinkConfig(setOf("demoapp"), setOf("demo.example"), basePath = "/kmp"))
        assertEquals(listOf<Route>(FeedRoute), prefixed.resolve("https://demo.example/kmp/feed")?.stack)
        assertNull(prefixed.resolve("https://demo.example/feed"))
        assertEquals("https://demo.example/kmp/l/7", prefixed.uriFor(ItemRoute("7")))
    }

    @Test
    fun uriForIsTheInverseOfResolve() {
        val routes = listOf(ThreadRoute("a b/c"), ItemRoute("x", "t"), SecurityRoute, QrRoute("q"), FeedRoute, PrivacyRoute)
        for (r in routes) {
            for (uri in listOf(links.uriFor(r), links.uriFor(r, scheme = "demoapp"))) {
                assertEquals(r, links.resolve(uri!!)?.stack?.last(), "round trip of $r via $uri")
            }
        }
        assertEquals("demoapp://app/settings/security", links.uriFor(SecurityRoute, "demoapp"))
    }

    @Test
    fun aDeepLinkOpensItsStackInsideTheRightTab() {
        val member = initialNavState(testNav, redirectPolicy, NavAuthState.Member)
        val stack = links.resolve("https://demo.example/app/settings/security")!!.stack
        val s = reduceNav(member, NavCommand.OpenStack(stack), testNav, redirectPolicy).state
        assertEquals(DestinationId("profiles.settings"), s.selectedTab)
        assertEquals(listOf(SettingsRoute, SecurityRoute), s.visibleRoutes())
        val back = reduceNav(s, NavCommand.Back, testNav, redirectPolicy).state
        assertEquals(listOf<Route>(SettingsRoute), back.visibleRoutes(), "back walks the synthesized parent")
    }

    @Test
    fun moreLiteralSegmentsWin() {
        val a = dest("x.user", "/u/:userId", ThreadRoute.serializer(),
            PathCodec({ p, _ -> p["userId"]?.let(::ThreadRoute) }, { RouteAddress(mapOf("userId" to it.threadId)) }), requiresAuth = false)
        val b = dest("x.user_posts", "/u/:userId/posts", ItemRoute.serializer(),
            PathCodec({ p, _ -> p["userId"]?.let { ItemRoute(it) } }, { RouteAddress(mapOf("userId" to it.id)) }), requiresAuth = false)
        val c = dest("x.me", "/u/me", FeedRoute.serializer(), PathCodec.constant(FeedRoute), requiresAuth = false, order = 99)
        val nav = resolveNav(listOf(NavManifest("t", "0", listOf(a, b, c))))
        val r = DeepLinkResolver(nav, config)
        assertEquals(DestinationId("x.me"), r.resolve("demoapp://u/me")?.target)
        assertEquals(DestinationId("x.user"), r.resolve("demoapp://u/ann")?.target)
        assertEquals(DestinationId("x.user_posts"), r.resolve("demoapp://u/ann/posts")?.target)
    }
}
