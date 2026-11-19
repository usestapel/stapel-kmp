package shop

import android.app.Application
import stapel.app.di.StapelApp
import stapel.runtime.PlatformContext

/** The Android app, created once in `Application.onCreate`. */
fun createAndroidShopApp(application: Application): StapelApp = createShopApp(PlatformContext(application), "android")
