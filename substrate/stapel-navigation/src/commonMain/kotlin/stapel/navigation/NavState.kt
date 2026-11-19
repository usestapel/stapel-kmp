package stapel.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * One screen instance on a stack.
 *
 * @property uid unique within a navigator's lifetime; keeps two instances of the same route apart.
 * @property route the address of the screen.
 */
@Serializable
public data class StackEntry(public val uid: String, public val route: Route)

/**
 * The whole navigation state, plain serializable data changed only by [reduceNav].
 *
 * What is on screen is [visible]: the selected tab's stack (when the tab host is active), then
 * [root] (screens above the tab host, or the whole stack when there is no tab host: sign-in, public
 * screens, covers), then [overlays] (dialogs and sheets, and whatever is pushed while one is open).
 *
 * @property auth the auth state this stack was built for; a restored state is reconciled with the
 *   live one by [NavCommand.AuthChanged].
 * @property root screens above the tab host, or the whole stack without one.
 * @property tabs the tabs for [auth], in order.
 * @property selectedTab the selected tab; null = the tab host is not shown.
 * @property tabStacks one stack per visited tab; the first entry is the tab's root.
 * @property overlays dialogs and sheets, topmost last.
 * @property pendingReturn the stack to open once sign-in completes.
 * @property nextUid counter for [StackEntry.uid], so the reducer stays pure.
 */
@Serializable
public data class NavState(
    public val auth: NavAuthState,
    public val root: List<StackEntry> = emptyList(),
    public val tabs: List<DestinationId> = emptyList(),
    public val selectedTab: DestinationId? = null,
    public val tabStacks: Map<DestinationId, List<StackEntry>> = emptyMap(),
    public val overlays: List<StackEntry> = emptyList(),
    public val pendingReturn: List<Route>? = null,
    public val nextUid: Long = 0,
) {
    /** True when the tab host is shown under [root]. */
    public val tabHostActive: Boolean get() = selectedTab != null

    /** The selected tab's stack, empty without a tab host. */
    public val selectedStack: List<StackEntry> get() = selectedTab?.let { tabStacks[it] }.orEmpty()

    /** Everything on screen, bottom to top. */
    public val visible: List<StackEntry> get() = selectedStack + root + overlays

    /** The topmost entry, or null for an empty state (auth still unresolved). */
    public val top: StackEntry? get() = visible.lastOrNull()
}

/**
 * Encodes and decodes [NavState] (process death, configuration change). Its serializers module
 * registers every installed route type polymorphically, which is what non-JVM targets need — a
 * route cannot be navigable without being registered.
 */
public class NavStateCodec(manifests: List<NavManifest>) {
    /** The Json instance with the route serializers; also usable for [ResultKey] values. */
    public val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Route::class) {
                subclass(SectionRoute::class, SectionRoute.serializer())
                for (m in manifests) for (d in m.destinations) register(d)
            }
        }
    }

    /** The state as JSON text. */
    public fun encode(state: NavState): String = json.encodeToString(NavState.serializer(), state)

    /** The state from [encode]'s text. Throws `SerializationException` for an unknown route type. */
    public fun decode(text: String): NavState = json.decodeFromString(NavState.serializer(), text)
}

@Suppress("UNCHECKED_CAST")
private fun PolymorphicModuleBuilder<Route>.register(spec: DestinationSpec<*>) {
    subclass(spec.routeClass as KClass<Route>, spec.serializer as KSerializer<Route>)
}
