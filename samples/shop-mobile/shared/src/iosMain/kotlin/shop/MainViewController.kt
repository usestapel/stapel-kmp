package shop

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import stapel.app.di.StapelApp
import stapel.runtime.PlatformContext

private val app: StapelApp by lazy { createShopApp(PlatformContext(), "ios") }

/** The Compose root the Swift host embeds. */
fun MainViewController(): UIViewController = ComposeUIViewController { ShopApp(app) }

/** Forwards a URL the system opened the app with (`shopmobile://…`, universal links). */
fun openUrl(url: String): Boolean = app.openUrl(url)
