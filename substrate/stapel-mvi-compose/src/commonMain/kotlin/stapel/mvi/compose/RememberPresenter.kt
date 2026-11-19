package stapel.mvi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.StateSaver
import kotlin.reflect.KClass

/**
 * How the UI obtains a presenter without knowing the container. Presenter constructors stay plain;
 * a project without Koin writes a small implementation that calls them.
 */
public interface PresenterFactory {
    /** Creates a presenter of [type] bound to [context]; [args] are the screen's arguments in order. */
    public fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P
}

/** The app's [PresenterFactory]; the default one fails with a message naming the missing setup. */
public val LocalPresenterFactory: ProvidableCompositionLocal<PresenterFactory> = staticCompositionLocalOf {
    object : PresenterFactory {
        override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P =
            error(
                "No PresenterFactory: provide LocalPresenterFactory at the app root " +
                    "(KoinPresenterFactory(koin) or your own) before calling rememberPresenter for ${type.simpleName}.",
            )
    }
}

/**
 * Returns the presenter of type [P] kept in the current `ViewModelStoreOwner` — a navigation entry,
 * a flow shared by several entries, or the app — so it survives recomposition and configuration
 * change and is cleared (its scope cancelled) when that owner is cleared.
 *
 * Presenters with different [args] or [key] are different instances.
 */
@Composable
public inline fun <reified P : Presenter<*, *, *>> rememberPresenter(
    vararg args: Any?,
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
): P = rememberPresenter(P::class, args.toList(), key, owner)

/** Non-reified form of [rememberPresenter]. */
@Composable
public fun <P : Presenter<*, *, *>> rememberPresenter(
    type: KClass<P>,
    args: List<Any?>,
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
): P {
    val factory = LocalPresenterFactory.current
    val storeOwner = owner ?: checkNotNull(LocalViewModelStoreOwner.current) {
        "rememberPresenter needs a ViewModelStoreOwner: call it inside a navigation entry or provide LocalViewModelStoreOwner"
    }
    val holder = viewModel(
        viewModelStoreOwner = storeOwner,
        key = "stapel.presenter:${type.qualifiedName ?: type.simpleName}:${key.orEmpty()}:${args.hashCode()}",
    ) {
        PresenterHolder(savedStateHandleOrNull(this)) { context -> factory.create(type, context, args) }
    }
    @Suppress("UNCHECKED_CAST")
    return holder.presenter as P
}

private fun savedStateHandleOrNull(extras: CreationExtras): SavedStateHandle? =
    try {
        extras.createSavedStateHandle()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

internal class PresenterHolder(
    handle: SavedStateHandle?,
    create: (PresenterContext) -> Presenter<*, *, *>,
) : ViewModel() {
    private val saver: StateSaver = handle?.let(::SavedStateHandleSaver) ?: StateSaver.None
    val presenter: Presenter<*, *, *> = create(PresenterContext(viewModelScope, saver))
}

private val saverJson = Json { encodeDefaults = true }
private const val VALUE = "v"

/** [StateSaver] over a `SavedStateHandle`: each value is JSON text under its key. */
internal class SavedStateHandleSaver(private val handle: SavedStateHandle) : StateSaver {
    override fun <T : Any> restore(key: String, serializer: KSerializer<T>): T? {
        val text = handle.get<SavedState>(key)?.read { getStringOrNull(VALUE) } ?: return null
        return saverJson.decodeFromString(serializer, text)
    }

    override fun <T : Any> register(key: String, serializer: KSerializer<T>, provider: () -> T?) {
        handle.setSavedStateProvider(key) {
            val value = provider()
            if (value == null) savedState() else savedState { putString(VALUE, saverJson.encodeToString(serializer, value)) }
        }
    }
}
