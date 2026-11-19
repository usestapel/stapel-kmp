package stapel.navigation

import stapel.navigation.NavAuthState.Anonymous
import stapel.navigation.NavAuthState.Guest
import stapel.navigation.NavAuthState.Member
import stapel.navigation.NavAuthState.Unresolved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val CHAT = DestinationId("chat.inbox")
private val FEED = DestinationId("feed.home")
private val SETTINGS = DestinationId("profiles.settings")
private val ACCOUNT = DestinationId("account.root")

class ReducerTest {
    private fun start(auth: NavAuthState, policy: NavPolicy = redirectPolicy) = initialNavState(testNav, policy, auth)

    // ── start state ─────────────────────────────────────────────────────────────────────────

    @Test
    fun startStatePerAuthState() {
        assertEquals(emptyList(), start(Unresolved).visibleRoutes())
        assertEquals(listOf<Route>(LoginRoute), start(Anonymous).visibleRoutes())
        assertNull(start(Anonymous).selectedTab)
        assertEquals(listOf(FEED), start(Guest).tabs)
        assertEquals(listOf<Route>(FeedRoute), start(Guest).visibleRoutes())
        assertEquals(listOf(FEED, CHAT, SETTINGS, ACCOUNT), start(Member).tabs)
        assertEquals(listOf<Route>(InboxRoute), start(Member).visibleRoutes(), "home is a tab: selected")
        val noSignIn = NavPolicy(signIn = null)
        assertEquals(listOf<Route>(FeedRoute), start(Anonymous, noSignIn).visibleRoutes())
    }

    // ── Push × auth state (table) ───────────────────────────────────────────────────────────

    private data class PushRow(val auth: NavAuthState, val route: Route, val visible: List<Route>, val refused: GuardDecision?)

    @Test
    fun pushEveryAuthStateAgainstMemberPublicAndAuthOnlyPublicTargets() {
        val thread = ThreadRoute("7")
        val qr = QrRoute("k1")
        val item = ItemRoute("42")
        val rows = listOf(
            PushRow(Member, thread, listOf(InboxRoute, thread), null),
            PushRow(Guest, thread, listOf(FeedRoute), GuardDecision.Deny(REASON_NO_MANDATE)),
            PushRow(Anonymous, thread, listOf(LoginRoute), GuardDecision.SignIn(UnauthenticatedMode.Redirect, listOf(InboxRoute, thread))),
            PushRow(Unresolved, thread, emptyList(), GuardDecision.Wait),
            PushRow(Member, qr, listOf(InboxRoute, qr), null),
            PushRow(Guest, qr, listOf(FeedRoute, qr), null),
            PushRow(Anonymous, qr, listOf(LoginRoute), GuardDecision.SignIn(UnauthenticatedMode.Redirect, listOf(qr))),
            PushRow(Unresolved, qr, emptyList(), GuardDecision.Wait),
            PushRow(Member, item, listOf(InboxRoute, item), null),
            PushRow(Guest, item, listOf(FeedRoute, item), null),
            PushRow(Anonymous, item, listOf(LoginRoute, item), null),
            PushRow(Unresolved, item, listOf(item), null),
        )
        for (row in rows) {
            val r = reduceNav(start(row.auth), NavCommand.Push(row.route), testNav, redirectPolicy)
            assertEquals(row.visible, r.state.visibleRoutes(), "visible for $row")
            assertEquals(row.refused, r.refused, "refusal for $row")
        }
    }

    @Test
    fun aRefusedPushKeepsTheIntendedStackForLater() {
        val r = reduceNav(start(Unresolved), NavCommand.Push(ThreadRoute("7")), testNav, redirectPolicy)
        assertEquals(listOf(InboxRoute, ThreadRoute("7")), r.state.pendingReturn)
    }

    @Test
    fun pushOfAnUnknownRouteIsRefusedByName() {
        val r = reduceNav(start(Member), NavCommand.Push(HelpRoute), testNav, redirectPolicy)
        assertEquals(GuardDecision.Deny(REASON_UNKNOWN_DESTINATION), r.refused)
        assertEquals(start(Member), r.state)
    }

    @Test
    fun dialogsGoToOverlaysAndEverythingPushedAboveThemStaysThere() {
        val r = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ConfirmRoute), NavCommand.Push(ItemRoute("1")))
        assertEquals(listOf<Route>(ConfirmRoute, ItemRoute("1")), r.state.overlays.map { it.route })
        assertEquals(listOf<Route>(InboxRoute), r.state.selectedStack.map { it.route })
    }

    @Test
    fun singleTopReplacesTheTopEntryOfTheSameDestinationKeepingItsUid() {
        val a = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ThreadRoute("1"))).state
        val b = reduceNav(a, NavCommand.Push(ThreadRoute("2"), singleTop = true), testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ThreadRoute("2")), b.visibleRoutes())
        assertEquals(a.visible.last().uid, b.visible.last().uid)
        val c = reduceNav(a, NavCommand.Push(ThreadRoute("2")), testNav, redirectPolicy).state
        assertEquals(3, c.visible.size)
    }

    @Test
    fun pushingATabRootSwitchesTab() {
        val r = reduceNav(start(Member), NavCommand.Push(SettingsRoute), testNav, redirectPolicy).state
        assertEquals(SETTINGS, r.selectedTab)
        assertEquals(listOf<Route>(SettingsRoute), r.visibleRoutes())
    }

    // ── Back / BackTo ───────────────────────────────────────────────────────────────────────

    @Test
    fun backPopsOverlaysThenRootThenTheTabStackAndStopsAtTheBottom() {
        var s = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ThreadRoute("1")), NavCommand.Push(ConfirmRoute)).state
        s = reduceNav(s, NavCommand.Back, testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ThreadRoute("1")), s.visibleRoutes())
        s = reduceNav(s, NavCommand.Back, testNav, redirectPolicy).state
        assertEquals(listOf<Route>(InboxRoute), s.visibleRoutes())
        val bottom = reduceNav(s, NavCommand.Back, testNav, redirectPolicy).state
        assertEquals(s, bottom, "the last entry is never popped: back reports 'not handled'")
        val anon = start(Anonymous)
        assertEquals(anon, reduceNav(anon, NavCommand.Back, testNav, redirectPolicy).state)
    }

    @Test
    fun backFromTheSignInScreenAbandonsTheReturnTarget() {
        val s = reduceAll(start(Anonymous), redirectPolicy, NavCommand.ReplaceAll(ItemRoute("1")), NavCommand.Push(ThreadRoute("7"))).state
        assertEquals(listOf(ItemRoute("1"), LoginRoute), s.visibleRoutes())
        val back = reduceNav(s, NavCommand.Back, testNav, redirectPolicy).state
        assertNull(back.pendingReturn)
        assertEquals(listOf<Route>(ItemRoute("1")), back.visibleRoutes())
    }

    @Test
    fun backToPopsToTheDestinationInclusiveOrNot() {
        val s = reduceAll(
            start(Member), redirectPolicy,
            NavCommand.Push(ThreadRoute("1")), NavCommand.Push(ItemRoute("a")), NavCommand.Push(ItemRoute("b")),
        ).state
        val keep = reduceNav(s, NavCommand.BackTo(DestinationId("chat.thread")), testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ThreadRoute("1")), keep.visibleRoutes())
        val incl = reduceNav(s, NavCommand.BackTo(DestinationId("chat.thread"), inclusive = true), testNav, redirectPolicy).state
        assertEquals(listOf<Route>(InboxRoute), incl.visibleRoutes())
        val tabRoot = reduceNav(s, NavCommand.BackTo(CHAT, inclusive = true), testNav, redirectPolicy).state
        assertEquals(s, tabRoot, "a tab keeps its root")
        val absent = reduceNav(s, NavCommand.BackTo(SETTINGS), testNav, redirectPolicy).state
        assertEquals(s, absent)
    }

    // ── ReplaceAll / SelectTab / OpenStack ──────────────────────────────────────────────────

    @Test
    fun replaceAllClearsEverythingAboveTheTabHost() {
        val s = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ThreadRoute("1")), NavCommand.Push(ConfirmRoute)).state
        val r = reduceNav(s, NavCommand.ReplaceAll(SecurityRoute), testNav, redirectPolicy).state
        assertEquals(SETTINGS, r.selectedTab)
        assertEquals(listOf(SettingsRoute, SecurityRoute), r.visibleRoutes())
        assertTrue(r.overlays.isEmpty())
        val outside = reduceNav(s, NavCommand.ReplaceAll(ItemRoute("9")), testNav, redirectPolicy).state
        assertEquals(listOf(ItemRoute("9")), outside.root.map { it.route })
        assertTrue(outside.overlays.isEmpty())
    }

    @Test
    fun selectTabSwitchesKeepsEachTabsStackAndReselectPopsToRoot() {
        var s = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ThreadRoute("1"))).state
        s = reduceNav(s, NavCommand.SelectTab(SETTINGS), testNav, redirectPolicy).state
        assertEquals(listOf<Route>(SettingsRoute), s.visibleRoutes())
        s = reduceNav(s, NavCommand.SelectTab(CHAT), testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ThreadRoute("1")), s.visibleRoutes(), "the chat stack survived the tab switch")
        s = reduceNav(s, NavCommand.SelectTab(CHAT), testNav, redirectPolicy).state
        assertEquals(listOf<Route>(InboxRoute), s.visibleRoutes(), "re-select pops to the tab root")
        val notATab = reduceNav(start(Guest), NavCommand.SelectTab(CHAT), testNav, redirectPolicy)
        assertEquals(GuardDecision.Deny(REASON_NOT_A_TAB), notATab.refused)
    }

    @Test
    fun openStackPlacesAStackThatStartsWithATabRootIntoThatTab() {
        val r = reduceNav(start(Member), NavCommand.OpenStack(listOf(SettingsRoute, SecurityRoute)), testNav, redirectPolicy).state
        assertEquals(SETTINGS, r.selectedTab)
        assertEquals(listOf(SettingsRoute, SecurityRoute), r.tabStacks.getValue(SETTINGS).map { it.route })
        assertTrue(r.root.isEmpty())
        val outside = reduceNav(start(Member), NavCommand.OpenStack(listOf(ItemRoute("3"))), testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ItemRoute("3")), outside.visibleRoutes())
        val section = reduceNav(start(Member), NavCommand.OpenStack(listOf(SectionRoute(ACCOUNT), PrivacyRoute)), testNav, redirectPolicy).state
        assertEquals(ACCOUNT, section.selectedTab)
    }

    // ── AuthChanged × auth state ────────────────────────────────────────────────────────────

    @Test
    fun redirectToSignInAndReturnToTheOriginalDestinationWithItsArguments() {
        val target = ThreadRoute("thread/with spaces & ü")
        val refused = reduceNav(start(Anonymous), NavCommand.Push(target), testNav, redirectPolicy)
        assertIs<GuardDecision.SignIn>(refused.refused)
        assertEquals(listOf<Route>(LoginRoute), refused.state.visibleRoutes())
        val signedIn = reduceNav(refused.state, NavCommand.AuthChanged(Member), testNav, redirectPolicy)
        assertNull(signedIn.refused)
        assertEquals(listOf(InboxRoute, target), signedIn.state.visibleRoutes())
        assertEquals(CHAT, signedIn.state.selectedTab)
        assertNull(signedIn.state.pendingReturn)
        assertEquals(target, signedIn.state.top?.route)
    }

    @Test
    fun wallModeShowsSignInOverTheIntactScreenThenOpensTheTarget() {
        val s = reduceAll(start(Anonymous, wallPolicy), wallPolicy, NavCommand.ReplaceAll(ItemRoute("1"))).state
        val wall = reduceNav(s, NavCommand.Push(ThreadRoute("7")), testNav, wallPolicy)
        assertEquals(GuardDecision.SignIn(UnauthenticatedMode.Wall, listOf(InboxRoute, ThreadRoute("7"))), wall.refused)
        assertEquals(listOf<Route>(ItemRoute("1")), wall.state.root.map { it.route }, "screen underneath intact")
        assertEquals(listOf<Route>(LoginRoute), wall.state.overlays.map { it.route })
        val done = reduceNav(wall.state, NavCommand.AuthChanged(Member), testNav, wallPolicy).state
        assertTrue(done.overlays.isEmpty())
        assertEquals(ThreadRoute("7"), done.top?.route)
    }

    @Test
    fun aGuestReturningToAMemberTargetIsDeniedAndLandsOnItsTabs() {
        val s = reduceNav(start(Anonymous), NavCommand.Push(ThreadRoute("7")), testNav, redirectPolicy).state
        val r = reduceNav(s, NavCommand.AuthChanged(Guest), testNav, redirectPolicy)
        assertEquals(GuardDecision.Deny(REASON_NO_MANDATE), r.refused)
        assertEquals(listOf<Route>(FeedRoute), r.state.visibleRoutes())
        assertNull(r.state.pendingReturn)
    }

    @Test
    fun signOutResetsToTheSignInScreen() {
        val s = reduceAll(start(Member), redirectPolicy, NavCommand.Push(ThreadRoute("1")), NavCommand.Push(ConfirmRoute)).state
        val r = reduceNav(s, NavCommand.AuthChanged(Anonymous), testNav, redirectPolicy).state
        assertEquals(listOf<Route>(LoginRoute), r.visibleRoutes())
        assertTrue(r.tabs.isEmpty())
        assertTrue(r.nextUid >= s.nextUid, "uids never restart, so saved UI state is never reused")
    }

    @Test
    fun unresolvedWaitsThenOpensTheTargetForAMemberOrSignInForAnAnonymousUser() {
        val waiting = reduceNav(start(Unresolved), NavCommand.Push(ThreadRoute("7")), testNav, redirectPolicy).state
        val member = reduceNav(waiting, NavCommand.AuthChanged(Member), testNav, redirectPolicy).state
        assertEquals(listOf(InboxRoute, ThreadRoute("7")), member.visibleRoutes())
        val anon = reduceNav(waiting, NavCommand.AuthChanged(Anonymous), testNav, redirectPolicy)
        assertEquals(listOf<Route>(LoginRoute), anon.state.visibleRoutes())
        assertEquals(listOf(InboxRoute, ThreadRoute("7")), anon.state.pendingReturn, "still wanted after sign-in")
        val unresolvedAgain = reduceNav(member, NavCommand.AuthChanged(Unresolved), testNav, redirectPolicy).state
        assertEquals(member.visible, unresolvedAgain.visible, "a refresh in flight never tears the stack down")
    }

    @Test
    fun aPublicDeepLinkOpenedWhileUnresolvedSurvivesResolution() {
        val s = reduceNav(start(Unresolved), NavCommand.OpenStack(listOf(ItemRoute("42"))), testNav, redirectPolicy).state
        val anon = reduceNav(s, NavCommand.AuthChanged(Anonymous), testNav, redirectPolicy).state
        assertEquals(ItemRoute("42"), anon.top?.route)
    }

    @Test
    fun guestUpgradingToMemberKeepsStacksAndGainsMemberTabs() {
        val g = reduceAll(start(Guest), redirectPolicy, NavCommand.Push(ItemRoute("1"))).state
        val m = reduceNav(g, NavCommand.AuthChanged(Member), testNav, redirectPolicy).state
        assertEquals(listOf(FEED, CHAT, SETTINGS, ACCOUNT), m.tabs)
        assertEquals(listOf(FeedRoute, ItemRoute("1")), m.visibleRoutes())
        val back = reduceNav(m, NavCommand.AuthChanged(Guest), testNav, redirectPolicy).state
        assertEquals(listOf(FEED), back.tabs)
        assertTrue(back.tabStacks.keys.all { it == FEED }, "member-only tab stacks are dropped")
    }

    // ── purity ──────────────────────────────────────────────────────────────────────────────

    private val script = listOf(
        NavCommand.Push(ThreadRoute("1")), NavCommand.Push(ConfirmRoute), NavCommand.Back,
        NavCommand.SelectTab(SETTINGS), NavCommand.Push(SecurityRoute), NavCommand.AuthChanged(Anonymous),
        NavCommand.Push(ThreadRoute("2")), NavCommand.AuthChanged(Member), NavCommand.OpenStack(listOf(ItemRoute("x"))),
        NavCommand.ReplaceAll(SecurityRoute), NavCommand.BackTo(SETTINGS), NavCommand.AuthChanged(Guest),
    )

    @Test
    fun theReducerIsPureAndUidsAreUniqueAtEveryStep() {
        val a = mutableListOf<NavState>()
        val b = mutableListOf<NavState>()
        val s0 = start(Member)
        script.fold(s0) { s, c -> reduceNav(s, c, testNav, redirectPolicy).state.also { a += it } }
        script.fold(s0) { s, c -> reduceNav(s, c, testNav, redirectPolicy).state.also { b += it } }
        assertEquals(a, b)
        assertEquals(start(Member), s0, "the input state is never mutated")
        for (s in a) {
            val uids = (s.tabStacks.values.flatten() + s.root + s.overlays).map { it.uid }
            assertEquals(uids.size, uids.toSet().size, "duplicate uid in $s")
        }
    }
}
