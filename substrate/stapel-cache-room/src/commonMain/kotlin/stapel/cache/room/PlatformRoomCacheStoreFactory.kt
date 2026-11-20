package stapel.cache.room

import stapel.runtime.PlatformContext

/**
 * The platform's default cache directory: Android `noBackupFilesDir`, iOS Application Support,
 * desktop [appId]'s per-user data directory (`desktopDataDirectory`); [appId] is read only on desktop,
 * which fails with a message naming both fixes when it is null.
 */
internal expect fun platformRoomCacheStoreFactory(
    platform: PlatformContext,
    options: RoomCacheOptions,
    appId: () -> String?,
): RoomCacheStoreFactory
