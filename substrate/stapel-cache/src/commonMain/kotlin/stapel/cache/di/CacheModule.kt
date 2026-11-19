package stapel.cache.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import stapel.cache.CacheObserver
import stapel.cache.CacheStoreFactory
import stapel.cache.ManagedStapelCache
import stapel.cache.MemoryCacheStoreFactory
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.di.StapelQualifiers
import stapel.runtime.Connectivity
import stapel.wire.WireJson
import kotlin.time.Clock

/**
 * Binds the app's cache as [ManagedStapelCache] and [StapelCache]. Storage is whatever
 * [CacheStoreFactory] the graph holds (`cacheRoomModule()` binds the durable one), else memory;
 * [CacheObserver] and [Connectivity] are picked up when bound (reconnect refetch). Needs
 * `runtimeModule()`. A function, not a value: every Koin application gets its own cache.
 *
 * @param json encoding of stored values; the wire configuration by default.
 * @param maxEntryBytes an encoded value above this size is kept in memory only.
 */
public fun cacheModule(json: Json = WireJson, maxEntryBytes: Long = 1L * 1024 * 1024): Module = module {
    single<ManagedStapelCache> {
        StapelCache(
            StapelCacheConfig(
                json = json,
                scope = get<CoroutineScope>(StapelQualifiers.AppScope),
                clock = get<Clock>(),
                storeFactory = getOrNull<CacheStoreFactory>() ?: MemoryCacheStoreFactory(),
                observer = getOrNull<CacheObserver>() ?: CacheObserver.None,
                maxEntryBytes = maxEntryBytes,
                connectivity = getOrNull<Connectivity>(),
            ),
        )
    } bind StapelCache::class
}
