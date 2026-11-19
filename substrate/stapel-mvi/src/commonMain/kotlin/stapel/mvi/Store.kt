package stapel.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer

/**
 * The shape every headless screen state holder has: an observable [state], one-off [effects] and a
 * single entry point for user intents.
 *
 * @param S immutable screen state.
 * @param I intent (what the user asked for).
 * @param E one-off effect (something that must happen exactly once: navigate, show a message).
 */
public interface Store<out S : Any, in I : Any, out E : Any> {
    /** Current state; always has a value. */
    public val state: StateFlow<S>

    /**
     * One-off effects. Buffered while nobody collects, delivered once to a single collector and never
     * replayed to a later one.
     */
    public val effects: Flow<E>

    /** Hands [intent] to the store. Never blocks and never suspends; intents are handled in order. */
    public fun dispatch(intent: I)
}

/**
 * Values that survive process death. Never store secrets here: no tokens, no one-time codes, no
 * passwords — the platform writes these values to disk unencrypted.
 */
public interface StateSaver {
    /** The value saved under [key] by a previous process, or null when there is none. */
    public fun <T : Any> restore(key: String, serializer: KSerializer<T>): T?

    /**
     * Registers [provider] to be asked for the value of [key] whenever the platform saves state. A null
     * value removes the key. Registering the same key again replaces the provider.
     */
    public fun <T : Any> register(key: String, serializer: KSerializer<T>, provider: () -> T?)

    /** Holder of [None]. */
    public companion object {
        /** A saver that remembers nothing: [restore] always returns null. */
        public val None: StateSaver = object : StateSaver {
            override fun <T : Any> restore(key: String, serializer: KSerializer<T>): T? = null

            override fun <T : Any> register(key: String, serializer: KSerializer<T>, provider: () -> T?) = Unit
        }
    }
}

/**
 * What a presenter gets from whoever keeps it alive.
 *
 * @property scope cancelled when the owner (a navigation entry, a flow, the app) goes away; all work
 *   of the presenter runs in it.
 * @property saver process-death storage for small, non-secret values.
 */
public class PresenterContext(
    public val scope: CoroutineScope,
    public val saver: StateSaver = StateSaver.None,
)
