package stapel.push.firebase

/**
 * The platform half of the Firebase push provider: everything that touches the Firebase Messaging
 * SDK and the OS notification APIs. On iOS it is implemented in Swift by the app
 * (`ios-bridges/firebase/StapelFirebasePush.swift`, emitted by the scaffold); on Android the
 * provider artifact implements it over FCM itself. Only strings, booleans, string maps and
 * single-shot callbacks cross it, so it exports to Objective-C as a plain protocol; the Kotlin half
 * owns every decision. Every call is made on the main thread; a completion may be called on any
 * thread, exactly once.
 */
public interface FirebasePushBridge {
    /** False when the configuration file is absent or unreadable; nothing else is called then. */
    public fun configured(): Boolean

    /** Answers the OS permission: `notDetermined`, `granted`, `provisional` or `denied`. */
    public fun permission(completion: (status: String) -> Unit)

    /**
     * Shows the OS prompt when the permission was never asked ([provisional]: iOS provisional
     * authorization, no prompt) and answers the resulting permission as [permission] does.
     */
    public fun requestPermission(provisional: Boolean, completion: (status: String) -> Unit)

    /**
     * Registers for remote notifications when needed and answers the FCM registration token, or an
     * error description (never containing a token).
     */
    public fun token(completion: (token: String?, error: String?) -> Unit)

    /** Deletes the FCM registration token; answers null on success, else an error description. */
    public fun deleteToken(completion: (error: String?) -> Unit)

    /**
     * Sets the receiver of tokens and notifications. Notifications that arrived before (the one
     * that launched the app) are delivered to it at once, each exactly once.
     */
    public fun setListener(listener: FirebasePushListener?)

    /** What the OS shows while the app is on screen: `system`, `inApp` or `silent`. */
    public fun setForegroundPresentation(mode: String)

    /**
     * Creates or updates an Android notification channel ([importance]: `low`, `default`, `high`);
     * a no-op on iOS.
     */
    public fun ensureChannel(id: String, name: String, importance: String)
}

/** Implemented by the Kotlin half; the platform half calls it. */
public interface FirebasePushListener {
    /** Firebase minted or rotated the registration token. */
    public fun onToken(token: String)

    /** A notification arrived while the process is alive; [foreground] = the app was on screen. */
    public fun onMessage(id: String?, title: String?, body: String?, data: Map<String, String>, foreground: Boolean)

    /** The person tapped a notification; [actionId] is null for the default tap. */
    public fun onOpened(id: String?, title: String?, body: String?, data: Map<String, String>, actionId: String?)
}

/**
 * The version of [FirebasePushBridge] and [FirebasePushListener]. A change to either bumps it; the
 * scaffold records the value it emitted the Swift bridge for and refuses a mismatch by name.
 */
public const val FIREBASE_PUSH_BRIDGE_ABI: Int = 1

/** The provider id the tokens and messages name. */
public const val FIREBASE_PUSH_PROVIDER_ID: String = "firebase"
