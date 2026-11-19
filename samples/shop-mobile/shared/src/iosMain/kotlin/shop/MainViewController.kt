package shop

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import stapel.app.di.StapelApp
import stapel.runtime.PlatformContext

private var baseUrlOverride: String? = null

private val app: StapelApp by lazy { createShopApp(PlatformContext(), "ios", baseUrlOverride) }

/** Points the app at another backend (UI tests: a stand on its own port); call before anything else. */
fun useBaseUrl(url: String) {
    baseUrlOverride = url
}

/** The Compose root the Swift host embeds. */
fun MainViewController(): UIViewController = ComposeUIViewController { ShopApp(app) }

/** Forwards a URL the system opened the app with (`shopmobile://…`, universal links). */
fun openUrl(url: String): Boolean = app.openUrl(url)
