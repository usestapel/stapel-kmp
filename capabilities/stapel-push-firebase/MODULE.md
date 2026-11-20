# stapel-push-firebase — module map

## Provides
- common: `FirebasePushBridge` + `FirebasePushListener` (the Objective-C-exportable bridge,
  `FIREBASE_PUSH_BRIDGE_ABI` = 1), `firebasePushProvider(bridge, platform, dispatchers, logger)`.
- Android: `androidFirebasePushProvider(platform, …)`, `di.firebasePushModule()`,
  `handleFirebasePushIntent(intent)`, `StapelFirebaseMessagingService`, `StapelPushPermissionActivity`
  (both declared in this artifact's manifest with `POST_NOTIFICATIONS` and
  `firebase_messaging_auto_init_enabled=false`).
- iOS: `di.firebasePushModule(bridge)`. jvm: `desktopFirebasePushProvider(…)`, `di.firebasePushModule()`.

## Invariants (tests: `FirebasePushProviderTest` on every target, `DesktopFirebasePushTest`)
- Passes `PushProviderContract` over a recording bridge on jvm, Android host and iOS simulator.
- Unconfigured (no config file) and desktop: `verifyUnavailable` — every call answers, nothing
  throws, an unconfigured bridge is never called beyond `configured()`.
- A token request with no answer (simulator without APNs) is `Transient` after 30 s, not a hang;
  an unknown permission word reads as denied.
- The launch notification tapped before Kotlin started reaches the first collector once.

## What is not proven here (on zip-ties until a device run)
- `AndroidFirebasePushBridge` (FCM calls, the permission activity, foreground posting, tray-tap
  intents): compiled and wired, exercised only on a device (no emulator image, R15).
- The Swift bridge: compiled by the reference app against the pinned `firebase-ios-sdk`; the
  simulator walk proves taps and foreground delivery, not a real APNs/FCM token.

## What not to do
- No cinterop, SwiftPM import or CocoaPods block here (ruling K3).
- Never log a token; bridge errors are descriptions.
