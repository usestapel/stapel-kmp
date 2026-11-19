package stapel.cache.room

import stapel.runtime.PlatformContext
import stapel.runtime.application

internal actual fun platformRoomCacheStoreFactory(platform: PlatformContext, options: RoomCacheOptions): RoomCacheStoreFactory =
    RoomCacheStoreFactory(platform.application, options)
