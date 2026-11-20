package stapel.analytics

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

@OptIn(ExperimentalForeignApi::class)
public actual fun platformAnalyticsStorage(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    namespace: String,
): AnalyticsStorage {
    val manager = NSFileManager.defaultManager
    val support = manager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Application Support directory is not available")
    val root: NSURL = support.URLByAppendingPathComponent(namespace) ?: error("cannot build the analytics directory URL")
    manager.createDirectoryAtURL(root, withIntermediateDirectories = true, attributes = null, error = null)
    root.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    return FileAnalyticsStorage(root.path ?: error("the analytics directory has no file path"), dispatchers.io)
}
