package shop

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.UIKit.UIViewController
import stapel.app.di.StapelApp
import stapel.push.firebase.FirebasePushBridge
import stapel.push.firebase.di.firebasePushModule
import stapel.runtime.LogLevel
import stapel.runtime.PlatformContext
import stapel.runtime.StapelLogger
import stapel.runtime.platformLogger

private var baseUrlOverride: String? = null
private var logFileName: String? = null

private var pushBridge: FirebasePushBridge? = null

private val app: StapelApp by lazy {
    createShopApp(PlatformContext(), "ios", baseUrlOverride, pushProvider = firebasePushModule(pushBridge)) {
        logFileName?.let { logger(FileTeeLogger(platformLogger(LogLevel.Info), documentsPath(it))) }
    }
}

/** Hands the app shell's Swift Firebase push bridge (`StapelFirebasePush.shared`) to Kotlin; call before anything else. */
fun installPushBridge(bridge: FirebasePushBridge) {
    pushBridge = bridge
}

/** Points the app at another backend (UI tests: a stand on its own port); call before anything else. */
fun useBaseUrl(url: String) {
    baseUrlOverride = url
}

/**
 * Also appends every info-and-above log record to `Documents/<name>` in the app's data container
 * (the launch smoke reads its markers from there: the unified log's filtering differs between
 * simulator runtimes, a file does not); call before anything else.
 */
fun useLogFile(name: String) {
    logFileName = name
}

/** The Compose root the Swift host embeds. */
fun MainViewController(): UIViewController = ComposeUIViewController { ShopApp(app) }

/** Forwards a URL the system opened the app with (`shopmobile://…`, universal links). */
fun openUrl(url: String): Boolean = app.openUrl(url)

private fun documentsPath(name: String): String =
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String) + "/" + name

/** Writes through [next] and appends the same line to the file at [path]. */
private class FileTeeLogger(private val next: StapelLogger, private val path: String) : StapelLogger {
    override fun isEnabled(level: LogLevel, tag: String): Boolean = next.isEnabled(level, tag)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        next.log(level, tag, message, error, fields)
        val line = "${level.name.uppercase()} $tag: $message" + (error?.let { " | $it" } ?: "") + "\n"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) fm.createFileAtPath(path, null, null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return
        handle.seekToEndOfFile()
        NSString.create(string = line).dataUsingEncoding(NSUTF8StringEncoding)?.let { handle.writeData(it) }
        handle.closeFile()
    }
}
