package stapel.push.firebase

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import stapel.push.DevicePlatform
import stapel.push.Importance
import stapel.push.InMemoryPushStore
import stapel.push.PushChannelSpec
import stapel.push.PushController
import stapel.push.PushDependencies
import stapel.push.PushEvent
import stapel.push.PushProvider
import stapel.runtime.StapelDispatchers
import stapel.session.InstallationIdentity
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.vault.InMemoryTokenVault
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The Android half of the Firebase push provider on the JVM (Robolectric): what runs without a device.
 * Real FCM delivery, the system prompt's own UI and a real tray are device-only (MODULE.md).
 */
/** Everything in place: the bridge answers on the calling thread here (no Android main looper in the test). */
private object Immediate : StapelDispatchers {
    override val main: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined
    override val default: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined
    override val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidFirebasePushBridgeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notifications: NotificationManager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun firebaseConfigured() {
        // What google-services.json gives a real app: a default FirebaseApp.
        FirebaseApp.initializeApp(app, FirebaseOptions.Builder().setApplicationId("1:1:android:1").setProjectId("stapel-test").setApiKey("test").build())
        // A real app has an icon: the posted notification's small icon.
        app.applicationInfo.icon = android.R.drawable.ic_dialog_info
        // The app's launcher activity (a library has none of its own): what a posted notification opens.
        val launcher = ComponentName(app, Activity::class.java)
        shadowOf(app.packageManager).addActivityIfNotPresent(launcher)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @After
    fun reset() {
        scope.cancel()
        FirebasePushRelay.bridge = null
        FirebaseApp.clearInstancesForTest()
    }

    private fun provider(): Pair<AndroidFirebasePushBridge, PushProvider> {
        val bridge = AndroidFirebasePushBridge(app)
        return bridge to firebasePushProvider(bridge, DevicePlatform.Android, Immediate)
    }

    private fun remoteMessage(id: String, title: String?, body: String?, data: Map<String, String>): RemoteMessage =
        RemoteMessage(
            Bundle().apply {
                putString("google.message_id", id)
                // FCM's wire marker of a notification message (RemoteMessage.notification is null without it).
                if (title != null || body != null) putString("gcm.n.e", "1")
                title?.let { putString("gcm.n.title", it) }
                body?.let { putString("gcm.n.body", it) }
                data.forEach { (k, v) -> putString(k, v) }
            },
        )

    /** Every activity start recorded since the last read. */
    private fun startedSince(host: Activity): List<Intent> = generateSequence { shadowOf(host).nextStartedActivity }.toList()

    @Test
    fun aTrayTapIntentBecomesOnePushMessageWithOnlyTheAppsDataAndOpensItsLinkThroughTheController() = runBlocking {
        val (bridge, provider) = provider()
        val sessions = SessionManager(SessionDependencies(InMemoryTokenVault(), scope, Clock.System), { stapel.session.RefreshOutcome.Transient("offline") })
        sessions.restore()
        val controller = PushController(
            PushDependencies(
                provider = provider,
                registry = null,
                sessions = sessions,
                identity = object : InstallationIdentity { override suspend fun deviceId() = "installation" },
                store = InMemoryPushStore(),
                scope = scope,
            ),
        )
        val opened = mutableListOf<String>()
        controller.attach { opened += it; true }

        val tap = Intent(app, Activity::class.java).apply {
            putExtra("google.message_id", "m-1")
            putExtra("url", "shopmobile://app/orders/42")
            putExtra("type", "order_shipped")
            putExtra("google.sent_time", "1")
            putExtra("gcm.n.e", "1")
            putExtra("from", "sender")
            putExtra("collapse_key", "app")
        }
        assertTrue(handleFirebasePushIntent(tap), "a tray-tap intent is recognized")
        withTimeout(5_000) { while (opened.isEmpty()) kotlinx.coroutines.delay(10) }
        assertEquals(listOf("shopmobile://app/orders/42"), opened)
        assertFalse(handleFirebasePushIntent(tap), "the same intent never opens twice (an activity recreated from it)")
        assertFalse(handleFirebasePushIntent(Intent(app, Activity::class.java).putExtra("url", "x")), "an intent without a message id is not a tap")
        assertTrue(bridge.configured())
    }

    @Test
    fun theOpenedMessageCarriesOnlyTheAppsDataKeys() = runBlocking {
        val (bridge, provider) = provider()
        bridge.handleIntent(
            Intent().apply {
                putExtra("google.message_id", "m-2")
                putExtra("url", "shopmobile://app/orders/7")
                putExtra("google.delivered_priority", "high")
                putExtra("gcm.n.analytics_data", "x")
                putExtra("from", "sender")
            },
        )
        val event = withTimeout(5_000) { provider.events.first() }
        val message = (event as PushEvent.Opened).message
        assertEquals("m-2", message.id)
        assertEquals(mapOf("url" to "shopmobile://app/orders/7"), message.data)
        assertEquals(FIREBASE_PUSH_PROVIDER_ID, message.provider)
    }

    @Test
    fun channelsAreCreatedWithTheirImportance() {
        val (_, provider) = provider()
        provider.ensureChannels(
            listOf(
                PushChannelSpec("orders", "Orders", Importance.High),
                PushChannelSpec("news", "News", Importance.Low),
                PushChannelSpec("default", "Notifications"),
            ),
        )
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notifications.getNotificationChannel("orders").importance)
        assertEquals("Orders", notifications.getNotificationChannel("orders").name)
        assertEquals(NotificationManager.IMPORTANCE_LOW, notifications.getNotificationChannel("news").importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, notifications.getNotificationChannel("default").importance)
    }

    @Test
    fun aNotificationArrivingOnScreenIsPostedAndItsTapOpensIt() = runBlocking {
        val (bridge, provider) = provider()
        provider.ensureChannels(listOf(PushChannelSpec("orders", "Orders")))
        // Android 13+: an app posts only with POST_NOTIFICATIONS granted (without it nothing appears).
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Robolectric.buildActivity(Activity::class.java).setup() // the app is on screen
        assertNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName), "the test app has a launcher activity")
        bridge.onMessageReceived(remoteMessage("m-3", "Shipped", "Order 42", mapOf("url" to "shopmobile://app/orders/42", "group" to "orders")))

        val received = withTimeout(5_000) { provider.events.first() } as PushEvent.Received
        assertTrue(received.foreground)
        val posted = shadowOf(notifications).allNotifications.single()
        assertEquals("Shipped", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Order 42", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("orders", posted.channelId)

        // The tray tap: the posted PendingIntent's intent carries the message back to the bridge.
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertTrue(bridge.handleIntent(tap))
        val opened = withTimeout(5_000) { provider.events.first() } as PushEvent.Opened
        assertEquals("m-3", opened.message.id)
        assertEquals("shopmobile://app/orders/42", opened.message.data["url"])
    }

    @Test
    fun inTheBackgroundNothingIsPostedByTheAppTheSystemShowsIt() = runBlocking {
        val (bridge, provider) = provider()
        bridge.onMessageReceived(remoteMessage("m-4", "Shipped", "Order 42", mapOf("url" to "u")))
        val received = withTimeout(5_000) { provider.events.first() } as PushEvent.Received
        assertFalse(received.foreground)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun withSilentForegroundPresentationNothingIsPosted() = runBlocking {
        val (bridge, provider) = provider()
        provider.setForegroundPresentation(stapel.push.ForegroundPresentation.Silent)
        Robolectric.buildActivity(Activity::class.java).setup()
        bridge.onMessageReceived(remoteMessage("m-5", "Shipped", "Order 42", emptyMap()))
        withTimeout(5_000) { provider.events.first() }
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun thePermissionActivityAsksOnceAndItsGrantReachesTheProvider() = runBlocking {
        val (bridge, provider) = provider()
        assertEquals(stapel.push.PushPermission.NotDetermined, provider.permission())
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()

        var answer: String? = null
        bridge.requestPermission(provisional = false) { answer = it }
        val started = assertNotNull(shadowOf(host).nextStartedActivity, "the prompt activity was started from the resumed activity")
        assertEquals(StapelPushPermissionActivity::class.java.name, started.component?.className)

        val prompt = Robolectric.buildActivity(StapelPushPermissionActivity::class.java, started).create().get()
        val asked = shadowOf(prompt).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), asked.requestedPermissions.toList())
        assertNull(answer, "no answer before the person answers")

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prompt.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertEquals("granted", answer)
        assertTrue(prompt.isFinishing, "the prompt activity closes after the answer")
        assertEquals(stapel.push.PushPermission.Granted, provider.permission())
    }

    @Test
    fun aDeniedPromptReadsAsDeniedAndIsNotAskedAgain() = runBlocking {
        val (bridge, provider) = provider()
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        var answer: String? = null
        bridge.requestPermission(provisional = false) { answer = it }
        val started = assertNotNull(shadowOf(host).nextStartedActivity)
        val prompt = Robolectric.buildActivity(StapelPushPermissionActivity::class.java, started).create().get()
        val asked = shadowOf(prompt).lastRequestedPermission
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prompt.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertEquals("denied", answer)
        assertEquals(stapel.push.PushPermission.Denied, provider.permission())

        var again: String? = null
        bridge.requestPermission(provisional = false) { again = it }
        assertEquals("denied", again, "after one answer the prompt is not shown again: the system settings decide")
        assertTrue(startedSince(host).none { it.component?.className == StapelPushPermissionActivity::class.java.name })
    }

    @Test
    fun withoutAnActivityOnScreenTheRequestAnswersWithoutAPrompt() = runBlocking {
        val (bridge, _) = provider()
        var answer: String? = null
        bridge.requestPermission(provisional = false) { answer = it }
        assertEquals("notDetermined", answer)
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13NoPromptIsShownAndEnabledNotificationsReadAsGranted() = runBlocking {
        val (bridge, provider) = provider()
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        var answer: String? = null
        bridge.requestPermission(provisional = false) { answer = it }
        assertEquals("granted", answer)
        assertTrue(startedSince(host).none { it.component?.className == StapelPushPermissionActivity::class.java.name })
        assertEquals(stapel.push.PushPermission.Granted, provider.permission())
    }
}
