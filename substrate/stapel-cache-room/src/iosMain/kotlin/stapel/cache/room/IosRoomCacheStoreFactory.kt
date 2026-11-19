package stapel.cache.room

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * The iOS cache storage: partitions under Application Support `/stapel-cache/`, with the
 * directory excluded from backup. Not the Caches directory: the system may purge it.
 */
@OptIn(ExperimentalForeignApi::class)
public fun RoomCacheStoreFactory(options: RoomCacheOptions): RoomCacheStoreFactory {
    val manager = NSFileManager.defaultManager
    val support = manager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Application Support directory is not available")
    val root: NSURL = support.URLByAppendingPathComponent(RoomCacheStoreFactory.ROOT_NAME)
        ?: error("cannot build the cache directory URL")
    manager.createDirectoryAtURL(root, withIntermediateDirectories = true, attributes = null, error = null)
    root.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val path = support.path ?: error("Application Support has no file path")
    return RoomCacheStoreFactory(path, options)
}
