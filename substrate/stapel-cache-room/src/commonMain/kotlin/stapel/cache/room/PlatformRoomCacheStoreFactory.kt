package stapel.cache.room

import stapel.runtime.PlatformContext

/** The platform's default cache directory; desktop has none and fails with a message naming the fix. */
internal expect fun platformRoomCacheStoreFactory(platform: PlatformContext, options: RoomCacheOptions): RoomCacheStoreFactory
