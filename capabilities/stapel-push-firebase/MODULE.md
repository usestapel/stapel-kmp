# stapel-push-firebase — module map

## Provides
- common: `FirebasePushBridge` + `FirebasePushListener` (the Objective-C-exportable bridge,
  `FIREBASE_PUSH_BRIDGE_ABI` = 1), `firebasePushProvider(bridge, platform, dispatchers, logger)`.
- Android: `androidFirebasePushProvider(platform, …)`, `di.firebasePushModule()`,
  `handleFirebasePushIntent(intent)`; internal `StapelFirebaseMessagingService` and
  `StapelPushPermissionActivity` (both declared in this artifact's manifest with `POST_NOTIFICATIONS` and
  `firebase_messaging_auto_init_enabled=false`).
- iOS: `di.firebasePushModule(bridge)`. jvm: `desktopFirebasePushProvider(…)`, `di.firebasePushModule()`.

## Invariants (tests: `FirebasePushProviderTest` on every target, `DesktopFirebasePushTest`)
- Passes `PushProviderContract` over a recording bridge on jvm, Android host and iOS simulator.
- Unconfigured (no config file) and desktop: `verifyUnavailable` — every call answers, nothing
  throws, an unconfigured bridge is never called beyond `configured()`.
- A token request with no answer (simulator without APNs) is `Transient` after 30 s, not a hang;
  an unknown permission word reads as denied.
- The launch notification tapped before Kotlin started reaches the first collector once.

## Android bridge on the JVM (`AndroidFirebasePushBridgeTest`, Robolectric, API 34 and 32)
- A tray-tap intent becomes one `PushMessage` with only the app's data keys (`google.*`, `gcm.*`,
  `from`, `collapse_key` dropped) and opens its link through a real `PushController`; the same intent
  never opens twice; an intent without a message id is not a tap.
- Channels are created with their name and importance (`low` / `default` / `high`).
- A notification arriving while an activity is started is posted on its `group` channel with title
  and body, and its `PendingIntent` round-trips back to an `Opened` message; in the background nothing
  is posted by the app; `Silent` posts nothing.
- The prompt activity is started from the resumed activity only, asks `POST_NOTIFICATIONS` once, and
  maps the answer (`granted` / `denied`); after one answer no second prompt; without a resumed activity
  the request answers `notDetermined` at once; below Android 13 no prompt and enabled reads `granted`.

## What is not proven here (device only)
- Real FCM: token minting and rotation, delivery by Google Play services, the system's own tray
  rendering of a background notification and its launch intent, the prompt's UI, notification
  settings changed by the person; `FirebaseApp` configured from a real `google-services.json`. No
  emulator image on the build host (R15): "not run".
- The Swift bridge: compiled by the reference app against the pinned `firebase-ios-sdk`; the
  simulator walk proves taps and foreground delivery, not a real APNs/FCM token.

## What not to do
- No cinterop, SwiftPM import or CocoaPods block here (ruling K3).
- Never log a token; bridge errors are descriptions.
