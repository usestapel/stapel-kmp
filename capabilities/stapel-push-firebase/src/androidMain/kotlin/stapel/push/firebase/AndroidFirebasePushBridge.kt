package stapel.push.firebase

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage

/**
 * The Android half of the Firebase push provider, over FCM. One per process ([FirebasePushRelay]
 * routes the messaging service and the permission activity to it).
 */
internal class AndroidFirebasePushBridge(private val app: Application) : FirebasePushBridge {
    private val prefs = app.getSharedPreferences("stapel.push.firebase", Context.MODE_PRIVATE)
    private val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var listener: FirebasePushListener? = null
    private val pending = mutableListOf<(FirebasePushListener) -> Unit>()
    private var presentation = "system"
    private var started = 0
    private var resumed: Activity? = null
    private var permissionWaiters = mutableListOf<(String) -> Unit>()

    private val configured: Boolean by lazy {
        // Without google-services.json the gradle plugin generated no resources: no default app.
        FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null
    }

    init {
        FirebasePushRelay.bridge = this
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (savedInstanceState == null && activity !is StapelPushPermissionActivity) handleIntent(activity.intent)
                }

                override fun onActivityStarted(activity: Activity) {
                    started++
                }

                override fun onActivityResumed(activity: Activity) {
                    resumed = activity
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed === activity) resumed = null
                }

                override fun onActivityStopped(activity: Activity) {
                    started--
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }

    override fun configured(): Boolean = configured

    override fun permission(completion: (status: String) -> Unit) {
        completion(currentPermission())
    }

    private fun currentPermission(): String {
        val enabled = notifications.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return if (enabled) "granted" else "denied"
        val granted = app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            granted && enabled -> "granted"
            granted -> "denied"
            !prefs.getBoolean(KEY_ASKED, false) -> "notDetermined"
            else -> "denied"
        }
    }

    override fun requestPermission(provisional: Boolean, completion: (status: String) -> Unit) {
        val current = currentPermission()
        val host = resumed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || current != "notDetermined" || host == null) {
            completion(current)
            return
        }
        permissionWaiters += completion
        if (permissionWaiters.size == 1) host.startActivity(Intent(host, StapelPushPermissionActivity::class.java))
    }

    /** The permission activity answered. */
    fun onPermissionResult() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        val status = currentPermission()
        val waiting = permissionWaiters
        permissionWaiters = mutableListOf()
        waiting.forEach { it(status) }
    }

    // FCM registration tokens: what the notifications backend addresses (firebase_admin `token=`).
    // firebase-messaging 25.1+ deprecates them for Firebase Installation ID registration (opt-in by a
    // manifest flag); the token path stays the default until the backend can target an FID.
    @Suppress("DEPRECATION")
    override fun token(completion: (token: String?, error: String?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                completion(task.result, null)
            } else {
                completion(null, task.exception?.javaClass?.simpleName ?: "token failed")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun deleteToken(completion: (error: String?) -> Unit) {
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            completion(if (task.isSuccessful) null else task.exception?.javaClass?.simpleName ?: "delete failed")
        }
    }

    override fun setListener(listener: FirebasePushListener?) {
        this.listener = listener
        if (listener != null) {
            val events = pending.toList()
            pending.clear()
            events.forEach { it(listener) }
        }
    }

    override fun setForegroundPresentation(mode: String) {
        presentation = mode
    }

    override fun ensureChannel(id: String, name: String, importance: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val level = when (importance) {
            "low" -> NotificationManager.IMPORTANCE_LOW
            "high" -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        notifications.createNotificationChannel(NotificationChannel(id, name, level))
    }

    /** From the messaging service. */
    fun onNewToken(token: String) {
        deliver { it.onToken(token) }
    }

    /** From the messaging service: only foreground notifications and data messages reach it. */
    fun onMessageReceived(message: RemoteMessage) {
        val foreground = started > 0
        val title = message.notification?.title
        val body = message.notification?.body
        val data = message.data.toMap()
        deliver { it.onMessage(message.messageId, title, body, data, foreground) }
        if (foreground && presentation == "system" && (title != null || body != null)) {
            post(message.messageId, title, body, data, message.notification?.channelId ?: data["group"] ?: DEFAULT_CHANNEL)
        }
    }

    /** A launch or `onNewIntent` intent: a tap on a notification carries its data as extras. */
    fun handleIntent(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        val id = extras.getString(MESSAGE_ID) ?: return false
        val data = extras.keySet()
            .filterNot { key -> key == MESSAGE_ID || key.startsWith("google.") || key.startsWith("gcm.") || key == "from" || key == "collapse_key" }
            .mapNotNull { key -> extras.getString(key)?.let { key to it } }
            .toMap()
        // Delivered once: an activity recreated from this intent does not open it again.
        intent.removeExtra(MESSAGE_ID)
        deliver { it.onOpened(id, null, null, data, null) }
        return true
    }

    private fun post(id: String?, title: String?, body: String?, data: Map<String, String>, channel: String) {
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        launch.putExtra(MESSAGE_ID, id ?: "stapel-${data.hashCode()}")
        data.forEach { (k, v) -> launch.putExtra(k, v) }
        val tap = PendingIntent.getActivity(
            app,
            (id ?: data.toString()).hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(app, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(app)
        }
        val notification = builder
            .setSmallIcon(app.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            notifications.notify((id ?: data.toString()).hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the post: nothing to show.
        }
    }

    private fun deliver(event: (FirebasePushListener) -> Unit) {
        listener?.let(event) ?: pending.add(event)
    }

    private companion object {
        const val KEY_ASKED = "asked"
        const val MESSAGE_ID = "google.message_id"
        const val DEFAULT_CHANNEL = "default"
    }
}

/** Routes the Android components this artifact declares to the process's bridge. */
internal object FirebasePushRelay {
    @Volatile
    var bridge: AndroidFirebasePushBridge? = null
}
