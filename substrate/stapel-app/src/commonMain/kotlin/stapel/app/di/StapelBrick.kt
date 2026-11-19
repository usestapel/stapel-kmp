package stapel.app.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
import stapel.i18n.I18nBundle
import stapel.mvi.compose.PresenterFactory
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthSource
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.NavContribution
import stapel.result.text.ErrorCatalog

/**
 * What one brick brings to an app, as one value: its Koin modules, its navigation, its strings and
 * error texts, and the capabilities it needs. Every brick UI library exposes exactly one public
 * implementation, named `<Brick>Brick` in its `di` package (`stapel.auth.ui.di.AuthBrick`), with a
 * constructor whose parameters all have defaults. [stapelApp] installs a list of them; an app that
 * wires by hand calls the same public factories the descriptor calls.
 *
 * A descriptor never starts anything itself: [modules] are plain factories, [navigation] only builds
 * data and composable lambdas (it is also called with an empty graph by the build's manifest export),
 * and [session] is asked once by the app.
 */
public interface StapelBrick {
    /** The backend library key (`auth`), the same key the scaffold and `kmp-registry.json` use. */
    public val key: String

    /** Capabilities this brick needs from the app; a destination missing one shows a named placeholder. */
    public val requires: Set<CapabilityKey> get() = emptySet()

    /** UI string bundles, registered after the core floor in this brick's order. */
    public val strings: List<I18nBundle> get() = emptyList()

    /** Error-text catalogs for this brick's error codes, consulted before the client floor. */
    public val errorCatalogs: List<ErrorCatalog> get() = emptyList()

    /**
     * The brick's Koin modules (domain and UI). They may expect the substrate the app builder always
     * installs (runtime, config, network, session, cache, cache-session) and must not bind the
     * cache-session binding themselves: the builder owns it.
     */
    public fun modules(): List<Module>

    /** The brick's navigation, its screens bound to the app's graph through [context]; null = no screens. */
    public fun navigation(context: BrickContext): NavContribution?

    /** The sign-in owner, when this brick provides the session the navigation guards read; null otherwise. */
    public fun session(context: BrickContext): BrickSession? = null

    /** Presenter creation for this brick's screens, chained before [next]. */
    public fun presenters(context: BrickContext, next: PresenterFactory): PresenterFactory = next
}

/**
 * What a brick sees of the app while building its navigation and session.
 *
 * @property koin the app's graph; read it lazily (inside composables), never while building.
 * @property appScope the app-lifetime scope, cancelled when the app closes.
 */
public class BrickContext(public val koin: Koin, public val appScope: CoroutineScope)

/** The brick that owns sign-in: the navigation's auth source plus its lifecycle hooks. */
public interface BrickSession : NavAuthSource {
    /** The sign-in destination the navigation policy redirects to. */
    public val signIn: DestinationId

    /** Restores a stored session (called once when the app's content starts). */
    public suspend fun restore()

    /** Connects the app's navigator once it exists (e.g. to bring the user back after an expired session). */
    public fun attach(navigator: StapelNavigator) {}
}
