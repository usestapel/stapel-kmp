package shop

import stapel.app.di.StapelApp
import stapel.push.firebase.di.firebasePushModule
import stapel.runtime.PlatformContext

/** The desktop app. */
fun createDesktopShopApp(baseUrl: String? = null): StapelApp = createShopApp(PlatformContext(), "desktop", baseUrl, pushProvider = firebasePushModule())
