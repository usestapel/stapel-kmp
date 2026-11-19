package stapel.cache.room

import android.content.Context

/**
 * The Android cache storage: partitions under `Context.noBackupFilesDir/stapel-cache/` — not the
 * OS cache directory (the system may purge it under storage pressure) and not backed up.
 */
public fun RoomCacheStoreFactory(
    context: Context,
    options: RoomCacheOptions,
): RoomCacheStoreFactory = RoomCacheStoreFactory(context.noBackupFilesDir.absolutePath, options)
