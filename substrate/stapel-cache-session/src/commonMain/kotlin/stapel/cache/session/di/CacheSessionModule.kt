package stapel.cache.session.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import stapel.cache.ManagedStapelCache
import stapel.cache.session.CacheSessionBinding
import stapel.di.StapelQualifiers
import stapel.session.SessionManager

/**
 * Binds the [CacheSessionBinding] that keeps the cache's partitions on the session's account, created
 * at start so it follows the session from the first state. Needs `cacheModule()`, `sessionModule()`
 * and `runtimeModule()`.
 */
public fun cacheSessionModule(): Module = module {
    single(createdAtStart = true) {
        CacheSessionBinding(
            cache = get<ManagedStapelCache>(),
            session = get<SessionManager>(),
            scope = get<CoroutineScope>(StapelQualifiers.AppScope),
        )
    } onClose { it?.close() }
}
