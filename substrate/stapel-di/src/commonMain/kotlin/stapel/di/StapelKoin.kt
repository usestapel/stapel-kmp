package stapel.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.ScopeDSL
import org.koin.dsl.koinApplication

/** The qualifiers stapel-kmp libraries agree on. */
public object StapelQualifiers {
    /** The app-lifetime `CoroutineScope` (bound by `runtimeModule`, cancelled when Koin closes). */
    public val AppScope: Qualifier = named("stapel.appScope")

    /** The Koin scope qualifier of session-bound definitions (see [sessionScoped]). */
    public val SessionScope: Qualifier = named("stapel.sessionScope")

    /** `stapel.<key>`: a per-library qualified definition. */
    public fun module(key: String): Qualifier = named("stapel.$key")

    /** The qualifier [contribute] uses for one element of a multi-binding. */
    public fun contribution(key: String): Qualifier = named("stapel.contribution.$key")
}

/**
 * Contributes one element to a multi-binding read with `getAll<T>()` (error catalogs, network
 * plug-ins, logout hooks). [key] must be unique per type across the app; a duplicate fails at
 * start like any other duplicate binding.
 */
public inline fun <reified T : Any> Module.contribute(key: String, noinline definition: Definition<T>) {
    single(StapelQualifiers.contribution(key), definition = definition)
}

/**
 * Declares session-bound definitions: created inside the session scope opened for each signed-in
 * session (see [bindSessionScope]) and closed when that session ends, so they can never be handed
 * to the next user.
 */
public fun Module.sessionScoped(block: ScopeDSL.() -> Unit) {
    scope(StapelQualifiers.SessionScope, block)
}

/**
 * Starts the app's Koin with the override semantics made explicit.
 *
 * [modules] are loaded with overrides **forbidden**: two libraries binding the same type and
 * qualifier fail here (`DefinitionOverrideException`) instead of silently letting the last one
 * win (Koin's own default allows it). [overrides] are loaded afterwards with overrides allowed —
 * the only place a redefinition is legal (tests, an app replacing a library default).
 *
 * @param global true registers the application as the global Koin context (`startKoin`); false
 *   returns an isolated application (tests, previews). Libraries never call this; the app does.
 */
public fun stapelKoin(
    modules: List<Module>,
    overrides: List<Module> = emptyList(),
    global: Boolean = true,
): KoinApplication {
    val app = koinApplication(createEagerInstances = false) {
        allowOverride(false)
        modules(modules)
    }
    if (overrides.isNotEmpty()) app.koin.loadModules(overrides, allowOverride = true)
    if (global) startKoin(app)
    app.createEagerInstances()
    return app
}
