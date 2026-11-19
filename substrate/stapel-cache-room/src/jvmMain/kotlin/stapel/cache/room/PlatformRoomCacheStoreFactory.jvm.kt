package stapel.cache.room

import stapel.runtime.PlatformContext

internal actual fun platformRoomCacheStoreFactory(platform: PlatformContext, options: RoomCacheOptions): RoomCacheStoreFactory =
    throw IllegalStateException("desktop has no platform cache directory: use cacheRoomModule(directory = <the app's data directory>)")
