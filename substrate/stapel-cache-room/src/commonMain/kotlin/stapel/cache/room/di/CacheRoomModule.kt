package stapel.cache.room.di

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cache.CacheStoreFactory
import stapel.cache.room.RoomCacheOptions
import stapel.cache.room.RoomCacheStoreFactory
import stapel.cache.room.platformRoomCacheStoreFactory
import stapel.config.StapelEnvironment
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

/**
 * Binds the durable [CacheStoreFactory] (`cacheModule()` picks it up): Room 3 storage on the app's IO
 * dispatcher, in the platform directory or in [directory] when given. Platform directories: Android
 * `noBackupFilesDir`, iOS Application Support, desktop the app's per-user data directory
 * (`desktopDataDirectory(appId)`: macOS Application Support, Windows `%LOCALAPPDATA%`, else XDG data
 * home), named by the bound `StapelEnvironment`'s `client.appId` — without an environment in the graph
 * a desktop app passes [directory]. On the JVM `stapel-cache/` and every partition directory are
 * created owner-only (`rwx------`). Needs `runtimeModule()`.
 *
 * @param directory absolute directory that will hold `stapel-cache/`; null = the platform default.
 * @param driver the SQLite driver of each database; the bundled build by default.
 */
public fun cacheRoomModule(
    directory: String? = null,
    driver: () -> SQLiteDriver = { BundledSQLiteDriver() },
): Module = module {
    single<CacheStoreFactory> {
        val options = RoomCacheOptions(ioContext = get<StapelDispatchers>().io, driver = driver)
        if (directory != null) {
            RoomCacheStoreFactory(directory, options)
        } else {
            platformRoomCacheStoreFactory(get<PlatformContext>(), options) { getOrNull<StapelEnvironment>()?.client?.appId }
        }
    }
}
