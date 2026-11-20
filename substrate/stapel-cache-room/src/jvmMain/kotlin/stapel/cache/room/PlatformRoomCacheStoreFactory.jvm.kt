package stapel.cache.room

import stapel.runtime.PlatformContext

internal actual fun platformRoomCacheStoreFactory(
    platform: PlatformContext,
    options: RoomCacheOptions,
    appId: () -> String?,
): RoomCacheStoreFactory {
    val id = appId() ?: throw IllegalStateException(
        "the desktop cache directory is per app: bind a StapelEnvironment (networkModule / stapelApp { environment(...) }), " +
            "whose client.appId names it, or pass cacheRoomModule(directory = <the app's data directory>)",
    )
    return RoomCacheStoreFactory(desktopDataDirectory(id), options)
}
