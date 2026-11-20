package stapel.push.firebase

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Declared in this artifact's manifest: FCM delivers tokens and foreground/data messages here.
 * Internal (a public class in bytecode, as the manifest needs): not API, and its vendor supertype's
 * members are not documented as ours.
 */
internal class StapelFirebaseMessagingService : FirebaseMessagingService() {
    /** Forwards a new registration token to the provider (the token path; see AndroidFirebasePushBridge.token). */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        FirebasePushRelay.bridge?.onNewToken(token)
    }

    /** Forwards a message to the provider (and shows it when the app is on screen and asks the system banner). */
    override fun onMessageReceived(message: RemoteMessage) {
        FirebasePushRelay.bridge?.onMessageReceived(message)
    }
}

/**
 * A transparent activity that shows the Android 13+ notification prompt and finishes. Started only
 * from `PushController.enable()` (a deliberate act), never at launch.
 */
internal class StapelPushPermissionActivity : Activity() {
    /** Asks once; the answer comes to [onRequestPermissionsResult]. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST)
        } else if (savedInstanceState == null) {
            finishWithAnswer()
        }
    }

    /** Reports the answer to the provider and closes. */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finishWithAnswer()
    }

    private fun finishWithAnswer() {
        FirebasePushRelay.bridge?.onPermissionResult()
        finish()
    }

    private companion object {
        const val REQUEST = 0x5350
    }
}

/**
 * Hands a tapped notification to the provider: call it from the activity's `onNewIntent` (a tap
 * while the app runs). The launch intent is read on its own. Returns whether [intent] was a tap.
 */
public fun handleFirebasePushIntent(intent: Intent?): Boolean = FirebasePushRelay.bridge?.handleIntent(intent) ?: false
