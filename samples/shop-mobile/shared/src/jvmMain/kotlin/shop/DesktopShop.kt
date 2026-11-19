package shop

import stapel.app.di.StapelApp
import stapel.runtime.PlatformContext

/** The desktop app. */
fun createDesktopShopApp(baseUrl: String? = null): StapelApp = createShopApp(PlatformContext(), "desktop", baseUrl)
