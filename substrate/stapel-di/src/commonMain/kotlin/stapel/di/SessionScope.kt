package stapel.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.scope.Scope

/** The Koin scope id of the session with [generation]: `stapel.session.<generation>`. */
public fun sessionScopeId(generation: Long): String = "stapel.session.$generation"

/** The live session scope. */
public interface SessionScopeHandle {
    /** The open session scope, or null while signed out (or not yet known). */
    public val current: StateFlow<Scope?>
}

/**
 * Opens and closes the session scope from a stream of session generations.
 *
 * Each distinct non-null generation closes the previous scope (running its definitions' `onClose`)
 * and opens `stapel.session.<generation>` with qualifier [StapelQualifiers.SessionScope]; null
 * closes it. The generation is the one `stapel-session` publishes on `SessionState.Authenticated`
 * / `Anonymous` (it increments on every adopted sign-in), so a new user always gets a new scope.
 *
 * @param generations the current session generation, null when there is no session.
 * @param scope where the collection runs (the app scope); cancelling it stops tracking but
 *   leaves the last scope open until Koin closes.
 */
public fun Koin.bindSessionScope(generations: Flow<Long?>, scope: CoroutineScope): SessionScopeHandle {
    val koin = this
    val state = MutableStateFlow<Scope?>(null)
    scope.launch {
        generations.distinctUntilChanged().collect { generation ->
            state.value?.let { if (!it.closed) it.close() }
            state.value = generation?.let { koin.createScope(sessionScopeId(it), StapelQualifiers.SessionScope) }
        }
    }
    return object : SessionScopeHandle {
        override val current: StateFlow<Scope?> = state.asStateFlow()
    }
}
