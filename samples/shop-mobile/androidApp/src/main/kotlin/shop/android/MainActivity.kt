package shop.android

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import shop.ShopApp
import shop.createAndroidShopApp
import stapel.push.firebase.handleFirebasePushIntent
import stapel.app.di.StapelApp

/** Holds the one app instance. */
class ShopApplication : Application() {
    lateinit var app: StapelApp
        private set

    override fun onCreate() {
        super.onCreate()
        app = createAndroidShopApp(this)
    }
}

/** The single activity: the Compose app, and deep links forwarded to its navigator. */
class MainActivity : ComponentActivity() {
    private val app get() = (application as ShopApplication).app

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { app.openUrl(it.toString()) }
        setContent { ShopApp(app) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A tapped notification while the app runs; the launch intent's tap is read by the provider itself.
        if (handleFirebasePushIntent(intent)) return
        intent.data?.let { app.openUrl(it.toString()) }
    }
}
