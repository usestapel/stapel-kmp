package stapel.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/**
 * Typed name of a result passed back to the previous destination.
 *
 * @property name unique name of the channel.
 * @property serializer serializer of the value.
 */
public class ResultKey<T : Any>(public val name: String, public val serializer: KSerializer<T>)

/** What screens use to navigate. Implemented by [StapelNavigator]; tests may record calls instead. */
public interface Navigator {
    /** The navigation state. */
    public val state: StateFlow<NavState>

    /** Opens [route] (guarded; a refusal is reported, not thrown). */
    public fun navigate(route: Route, singleTop: Boolean = false)

    /** Pops one entry; false when there is nothing to pop (the platform may then close the app). */
    public fun back(): Boolean

    /** Pops to [id]; false when [id] is not on screen. */
    public fun backTo(id: DestinationId, inclusive: Boolean = false): Boolean

    /** Replaces everything above the tab host with [route]. */
    public fun replaceAll(route: Route)

    /** Selects tab [id]. */
    public fun selectTab(id: DestinationId)

    /** Opens a link; false when it is not one of the app's links. */
    public fun open(uri: String): Boolean

    /** Delivers [value] under [key] to the destination below, then pops. */
    public fun <T : Any> backWithResult(key: ResultKey<T>, value: T)

    /** Results delivered under [key]; each result reaches one collector exactly once. */
    public fun <T : Any> results(key: ResultKey<T>): Flow<T>
}

/**
 * The navigator: holds [NavState], applies commands through [reduceNav] and follows [auth].
 * Thread-safe: every command is a compare-and-set over a pure reduction.
 *
 * @property nav the resolved navigation.
 * @property policy the app's policy.
 * @param initial a restored state (process death); it is reconciled with the live auth state.
 */
public class StapelNavigator(
    public val nav: ResolvedNav,
    public val policy: NavPolicy,
    auth: NavAuthSource,
    scope: CoroutineScope,
    private val deepLinks: DeepLinkResolver? = null,
    private val guard: RouteGuard = DefaultRouteGuard,
    initial: NavState? = null,
) : Navigator {
    private val authFlow = auth.authState()
    private val mutableState = MutableStateFlow(initial ?: initialNavState(nav, policy, authFlow.value))
    private val refusal = MutableStateFlow<GuardDecision?>(null)
    private val resultChannels = HashMap<String, Channel<Any>>()

    override val state: StateFlow<NavState> = mutableState.asStateFlow()

    /** The last guard refusal (null after a successful command); the shell names it. */
    public val lastRefusal: StateFlow<GuardDecision?> = refusal.asStateFlow()

    init {
        dispatch(NavCommand.AuthChanged(authFlow.value))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authFlow.collect { dispatch(NavCommand.AuthChanged(it)) }
        }
    }

    /** Applies [command] and returns the reduction. */
    public fun dispatch(command: NavCommand): NavReduction {
        while (true) {
            val old = mutableState.value
            val reduction = reduceNav(old, command, nav, policy, guard)
            if (mutableState.compareAndSet(old, reduction.state)) {
                refusal.value = reduction.refused
                return reduction
            }
        }
    }

    override fun navigate(route: Route, singleTop: Boolean) {
        dispatch(NavCommand.Push(route, singleTop))
    }

    override fun back(): Boolean = changed { dispatch(NavCommand.Back) }

    override fun backTo(id: DestinationId, inclusive: Boolean): Boolean = changed { dispatch(NavCommand.BackTo(id, inclusive)) }

    override fun replaceAll(route: Route) {
        dispatch(NavCommand.ReplaceAll(route))
    }

    override fun selectTab(id: DestinationId) {
        dispatch(NavCommand.SelectTab(id))
    }

    override fun open(uri: String): Boolean {
        val match = deepLinks?.resolve(uri) ?: return false
        dispatch(NavCommand.OpenStack(match.stack))
        return true
    }

    override fun <T : Any> backWithResult(key: ResultKey<T>, value: T) {
        channel(key.name).trySend(value)
        back()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> results(key: ResultKey<T>): Flow<T> = channel(key.name).receiveAsFlow().map { it as T }

    private fun channel(name: String): Channel<Any> = resultChannels.getOrPut(name) { Channel(Channel.UNLIMITED) }

    private inline fun changed(block: () -> NavReduction): Boolean {
        val before = mutableState.value
        return block().state != before
    }
}
