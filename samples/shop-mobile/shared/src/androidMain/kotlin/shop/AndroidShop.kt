package shop

import android.app.Application
import android.content.Intent
import stapel.app.di.StapelApp
import stapel.push.firebase.di.firebasePushModule
import stapel.push.firebase.handleFirebasePushIntent
import stapel.runtime.PlatformContext

/** The Android app, created once in `Application.onCreate`. */
fun createAndroidShopApp(application: Application): StapelApp = createShopApp(PlatformContext(application), "android", pushProvider = firebasePushModule())

/** Hands a tapped notification (an activity's `onNewIntent`) to the push provider; true when it was one. */
fun handleShopPushIntent(intent: Intent): Boolean = handleFirebasePushIntent(intent)
