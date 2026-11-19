package stapel.mvi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import stapel.mvi.Store

/** The store's state as Compose state, collected only while the owner is at least started. */
@Composable
public fun <S : Any> Store<S, *, *>.collectState(): State<S> = state.collectAsStateWithLifecycle()

/**
 * Handles the store's one-off effects while the owner is at least started. Effects emitted while
 * stopped (or during a configuration change) wait in the store's buffer and are handled once, after
 * the owner starts again.
 */
@Composable
@Suppress("ComposableNaming")
public fun <E : Any> Store<*, *, E>.CollectEffects(onEffect: suspend (E) -> Unit) {
    val handler = rememberUpdatedState(onEffect)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(this, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler.value(it) }
        }
    }
}
