package stapel.cache.room

import stapel.runtime.PlatformContext

internal actual fun platformRoomCacheStoreFactory(
    platform: PlatformContext,
    options: RoomCacheOptions,
    appId: () -> String?,
): RoomCacheStoreFactory = RoomCacheStoreFactory(options)
