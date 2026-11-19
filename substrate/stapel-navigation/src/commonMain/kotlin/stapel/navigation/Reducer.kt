package stapel.navigation

/** A request to change [NavState]; applied by [reduceNav]. */
public sealed interface NavCommand {
    /**
     * Opens [route] on the topmost layer.
     *
     * @property route the target.
     * @property singleTop replace the top entry when it is the same destination.
     */
    public data class Push(public val route: Route, public val singleTop: Boolean = false) : NavCommand

    /** Pops one entry (overlays first, then screens above the tab host, then the tab stack). */
    public data object Back : NavCommand

    /**
     * Pops to the topmost entry of [id].
     *
     * @property id the destination to return to.
     * @property inclusive pop that entry too.
     */
    public data class BackTo(public val id: DestinationId, public val inclusive: Boolean = false) : NavCommand

    /**
     * Replaces everything above the tab host with [route] (or makes it the stack of its tab).
     *
     * @property route the new top.
     */
    public data class ReplaceAll(public val route: Route) : NavCommand

    /**
     * Selects a tab; re-selecting the selected tab pops it to its root.
     *
     * @property id the tab.
     */
    public data class SelectTab(public val id: DestinationId) : NavCommand

    /**
     * Opens a synthetic stack (deep link, return after sign-in); placed in its tab when it starts with one.
     *
     * @property routes bottom to top.
     */
    public data class OpenStack(public val routes: List<Route>) : NavCommand

    /**
     * The auth state changed; reconciles tabs, replays [NavState.pendingReturn], resets on sign-out.
     *
     * @property auth the new state.
     */
    public data class AuthChanged(public val auth: NavAuthState) : NavCommand
}

/**
 * Outcome of [reduceNav].
 *
 * @property state the new state.
 * @property refused why a guard refused, or null.
 */
public data class NavReduction(public val state: NavState, public val refused: GuardDecision? = null)

/**
 * Start state for [auth] — the mobile image of the web flow order (`/` → `/login` → `/app`):
 * unresolved → empty (the shell shows the wait screen); anonymous with a sign-in destination → the
 * sign-in screen alone; otherwise the tab host on [NavPolicy.home] (when it is a tab) or the first
 * tab; with no tab at all, [NavPolicy.home] alone.
 */
public fun initialNavState(nav: ResolvedNav, policy: NavPolicy, auth: NavAuthState): NavState =
    initialState(nav, policy, auth, nextUid = 0)

/** [initialNavState] continuing the uid counter, so a reset never reuses an id with saved UI state. */
private fun initialState(nav: ResolvedNav, policy: NavPolicy, auth: NavAuthState, nextUid: Long): NavState {
    val empty = NavState(auth = auth, nextUid = nextUid)
    if (auth == NavAuthState.Unresolved) return empty
    if (auth == NavAuthState.Anonymous && policy.signIn != null) {
        val route = nav.defaultRoute(policy.signIn) ?: return empty
        return empty.pushTo(Layer.Root, route)
    }
    return empty.activateTabHost(nav, policy)
}

/**
 * The only place navigation state changes. Pure: the same inputs give the same output, no clock,
 * no randomness ([StackEntry.uid] comes from [NavState.nextUid]).
 */
public fun reduceNav(
    state: NavState,
    command: NavCommand,
    nav: ResolvedNav,
    policy: NavPolicy,
    guard: RouteGuard = DefaultRouteGuard,
): NavReduction = Reducer(nav, policy, guard).reduce(state, command)

private enum class Layer { Tab, Root, Overlay }

private class Reducer(val nav: ResolvedNav, val policy: NavPolicy, val guard: RouteGuard) {
    fun reduce(state: NavState, command: NavCommand): NavReduction = when (command) {
        is NavCommand.Push -> guarded(state, listOf(command.route), nav.syntheticStack(command.route)) { push(state, command) }
        NavCommand.Back -> NavReduction(back(state))
        is NavCommand.BackTo -> NavReduction(backTo(state, command))
        is NavCommand.ReplaceAll -> guarded(state, listOf(command.route), nav.syntheticStack(command.route)) { replaceAll(state, command.route) }
        is NavCommand.SelectTab -> selectTab(state, command.id)
        is NavCommand.OpenStack -> if (command.routes.isEmpty()) {
            NavReduction(state)
        } else {
            guarded(state, command.routes, command.routes) { openStack(state, command.routes) }
        }
        is NavCommand.AuthChanged -> authChanged(state, command.auth)
    }

    /** Runs the guard over [checked]; on Allow applies [open], otherwise records the refusal. */
    private inline fun guarded(state: NavState, checked: List<Route>, returnTo: List<Route>, open: () -> NavState): NavReduction {
        for (route in checked) {
            val dest = nav.destinationOf(route)
                ?: return NavReduction(state, GuardDecision.Deny(REASON_UNKNOWN_DESTINATION))
            when (val d = guard.check(dest, state.auth, policy)) {
                GuardDecision.Allow -> Unit
                GuardDecision.Wait -> return NavReduction(state.copy(pendingReturn = returnTo), d)
                is GuardDecision.SignIn -> return signIn(state, d.mode, returnTo)
                is GuardDecision.Deny -> return NavReduction(state, d)
            }
        }
        return NavReduction(open())
    }

    private fun signIn(state: NavState, mode: UnauthenticatedMode, returnTo: List<Route>): NavReduction {
        val signInId = policy.signIn
        val route = signInId?.let { nav.defaultRoute(it) }
            ?: return NavReduction(state, GuardDecision.Deny(REASON_SIGN_IN_UNAVAILABLE))
        val decision = GuardDecision.SignIn(mode, returnTo)
        val withReturn = state.copy(pendingReturn = returnTo)
        val already = (withReturn.root + withReturn.overlays).any { nav.destinationOf(it.route)?.id == signInId }
        if (already) return NavReduction(withReturn, decision)
        val layer = if (mode == UnauthenticatedMode.Wall) Layer.Overlay else Layer.Root
        return NavReduction(withReturn.pushTo(layer, route), decision)
    }

    private fun push(state: NavState, command: NavCommand.Push): NavState {
        val dest = nav.destinationOf(command.route)!!
        if (dest.id in state.tabs && state.tabHostActive && dest.spec?.presentation != Presentation.Cover &&
            command.route == nav.defaultRoute(dest.id)
        ) {
            return switchTab(state, dest.id, reselectPops = true)
        }
        val layer = layerFor(state, dest.presentation)
        if (command.singleTop) {
            val stack = state.stackOf(layer)
            val top = stack.lastOrNull()
            if (top != null && nav.destinationOf(top.route)?.id == dest.id) {
                return state.withStack(layer, stack.dropLast(1) + top.copy(route = command.route))
            }
        }
        return state.pushTo(layer, command.route)
    }

    private fun layerFor(state: NavState, presentation: Presentation): Layer = when {
        state.overlays.isNotEmpty() -> Layer.Overlay
        presentation == Presentation.Dialog || presentation == Presentation.Sheet -> Layer.Overlay
        presentation == Presentation.Cover -> Layer.Root
        state.root.isNotEmpty() || !state.tabHostActive -> Layer.Root
        else -> Layer.Tab
    }

    private fun back(state: NavState): NavState {
        val popped: StackEntry
        val next = when {
            state.overlays.isNotEmpty() -> {
                popped = state.overlays.last()
                state.copy(overlays = state.overlays.dropLast(1))
            }
            state.root.size > 1 || (state.root.isNotEmpty() && state.tabHostActive) -> {
                popped = state.root.last()
                state.copy(root = state.root.dropLast(1))
            }
            state.root.isEmpty() && state.selectedStack.size > 1 -> {
                popped = state.selectedStack.last()
                state.withStack(Layer.Tab, state.selectedStack.dropLast(1))
            }
            else -> return state
        }
        return if (isSignIn(popped)) next.copy(pendingReturn = null) else next
    }

    private fun backTo(state: NavState, command: NavCommand.BackTo): NavState {
        for (layer in listOf(Layer.Overlay, Layer.Root, Layer.Tab)) {
            val stack = state.stackOf(layer)
            val index = stack.indexOfLast { nav.destinationOf(it.route)?.id == command.id }
            if (index < 0) continue
            val keep = if (command.inclusive) index else index + 1
            val mustKeepOne = layer == Layer.Tab || (layer == Layer.Root && !state.tabHostActive)
            if (keep == 0 && mustKeepOne) return state
            val trimmed = stack.take(keep)
            var next = state.withStack(layer, trimmed)
            if (layer == Layer.Root) next = next.copy(overlays = emptyList())
            if (layer == Layer.Tab) next = next.copy(root = emptyList(), overlays = emptyList())
            return next
        }
        return state
    }

    private fun replaceAll(state: NavState, route: Route): NavState {
        val cleared = state.copy(root = emptyList(), overlays = emptyList())
        val dest = nav.destinationOf(route)!!
        if (cleared.tabHostActive && dest.id in cleared.tabs && route == nav.defaultRoute(dest.id)) {
            return switchTab(cleared, dest.id, reselectPops = true)
        }
        val stack = nav.syntheticStack(route)
        val tab = stack.first().let { nav.destinationOf(it)?.id }
        if (cleared.tabHostActive && tab != null && tab in cleared.tabs && stack.first() == nav.defaultRoute(tab)) {
            return cleared.replaceTabStack(tab, stack)
        }
        return cleared.pushTo(Layer.Root, route)
    }

    private fun selectTab(state: NavState, id: DestinationId): NavReduction {
        if (id !in state.tabs || !state.tabHostActive) return NavReduction(state, GuardDecision.Deny(REASON_NOT_A_TAB))
        return NavReduction(switchTab(state, id, reselectPops = true))
    }

    private fun switchTab(state: NavState, id: DestinationId, reselectPops: Boolean): NavState {
        val cleared = state.copy(root = emptyList(), overlays = emptyList())
        if (cleared.selectedTab == id) {
            return if (reselectPops) cleared.withStack(Layer.Tab, cleared.selectedStack.take(1)) else cleared
        }
        return cleared.ensureTabStack(nav, id).copy(selectedTab = id)
    }

    private fun openStack(state: NavState, routes: List<Route>): NavState {
        val target = routes.last()
        val modal = nav.destinationOf(target)?.presentation.let { it == Presentation.Dialog || it == Presentation.Sheet }
        val base = if (modal) routes.dropLast(1) else routes
        var next = state.copy(root = emptyList(), overlays = emptyList())
        if (base.isNotEmpty()) {
            val tab = nav.destinationOf(base.first())?.id
            next = if (next.tabHostActive && tab != null && tab in next.tabs && base.first() == nav.defaultRoute(tab)) {
                next.replaceTabStack(tab, base)
            } else {
                base.fold(next) { s, r -> s.pushTo(Layer.Root, r) }
            }
        }
        if (modal) next = next.pushTo(Layer.Overlay, target)
        return next
    }

    private fun authChanged(state: NavState, auth: NavAuthState): NavReduction {
        if (auth == state.auth) return NavReduction(state)
        // A session refresh in flight never tears the stack down: only the start state is unresolved.
        if (auth == NavAuthState.Unresolved) return NavReduction(state)
        val wasUnresolved = state.auth == NavAuthState.Unresolved
        // While unresolved, a target the guard made wait sits in pendingReturn; public screens opened
        // meanwhile (a deep link) are on screen. Both are replayed once auth resolves.
        val openedWhileUnresolved = if (wasUnresolved) {
            state.pendingReturn ?: state.visible.map { it.route }.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        if (auth == NavAuthState.Anonymous) {
            // Sign-out resets; resolving to anonymous at start keeps what the user asked for.
            val pending = openedWhileUnresolved
            val base = initialState(nav, policy, auth, state.nextUid)
            return if (pending != null) reduce(base, NavCommand.OpenStack(pending)) else NavReduction(base)
        }
        // Guest or member: remove sign-in entries, (re)build the tab host, replay the pending target.
        val pending = if (wasUnresolved) openedWhileUnresolved else state.pendingReturn
        val withoutSignIn = state.copy(
            auth = auth,
            root = state.root.filterNot(::isSignIn),
            overlays = state.overlays.filterNot(::isSignIn),
            pendingReturn = null,
        )
        val base = if (wasUnresolved || state.auth == NavAuthState.Anonymous && withoutSignIn.root.isEmpty()) {
            initialState(nav, policy, auth, state.nextUid)
        } else {
            withoutSignIn.reconcileTabs(nav, policy)
        }
        return if (pending != null) {
            val replay = reduce(base, NavCommand.OpenStack(pending))
            if (replay.refused == null) replay else NavReduction(base, replay.refused)
        } else {
            NavReduction(base)
        }
    }

    private fun isSignIn(entry: StackEntry): Boolean =
        policy.signIn != null && nav.destinationOf(entry.route)?.id == policy.signIn

    private fun NavState.replaceTabStack(tab: DestinationId, routes: List<Route>): NavState {
        var s = copy(selectedTab = tab, tabStacks = tabStacks + (tab to emptyList()))
        for (r in routes) s = s.pushTo(Layer.Tab, r)
        return s
    }
}

private fun NavState.stackOf(layer: Layer): List<StackEntry> = when (layer) {
    Layer.Tab -> selectedStack
    Layer.Root -> root
    Layer.Overlay -> overlays
}

private fun NavState.withStack(layer: Layer, stack: List<StackEntry>): NavState = when (layer) {
    Layer.Tab -> copy(tabStacks = tabStacks + (selectedTab!! to stack))
    Layer.Root -> copy(root = stack)
    Layer.Overlay -> copy(overlays = stack)
}

private fun NavState.pushTo(layer: Layer, route: Route): NavState {
    val entry = StackEntry("e$nextUid", route)
    return copy(nextUid = nextUid + 1).let { it.withStack(layer, it.stackOf(layer) + entry) }
}

private fun NavState.ensureTabStack(nav: ResolvedNav, tab: DestinationId): NavState {
    if (tabStacks[tab].orEmpty().isNotEmpty()) return this
    val route = nav.defaultRoute(tab) ?: return this
    val entry = StackEntry("e$nextUid", route)
    return copy(nextUid = nextUid + 1, tabStacks = tabStacks + (tab to listOf(entry)))
}

/** Tab host for [auth] from scratch: home (if a tab) or the first tab; without tabs, home alone. */
private fun NavState.activateTabHost(nav: ResolvedNav, policy: NavPolicy): NavState {
    val tabs = nav.tabsFor(auth)
    if (tabs.isEmpty()) {
        val home = policy.home?.let { nav.defaultRoute(it) } ?: return copy(tabs = emptyList(), selectedTab = null)
        return copy(tabs = emptyList(), selectedTab = null).pushTo(Layer.Root, home)
    }
    val selected = policy.home?.takeIf { it in tabs } ?: tabs.first()
    return copy(tabs = tabs, selectedTab = selected, tabStacks = emptyMap()).ensureTabStack(nav, selected)
}

/** Keeps stacks of tabs still allowed, drops the others, keeps the selection when possible. */
private fun NavState.reconcileTabs(nav: ResolvedNav, policy: NavPolicy): NavState {
    val tabs = nav.tabsFor(auth)
    if (tabs.isEmpty()) return copy(tabs = emptyList(), selectedTab = null, tabStacks = emptyMap())
    val kept = tabStacks.filterKeys { it in tabs }
    val selected = selectedTab?.takeIf { it in tabs } ?: policy.home?.takeIf { it in tabs } ?: tabs.first()
    return copy(tabs = tabs, tabStacks = kept, selectedTab = selected).ensureTabStack(nav, selected)
}
