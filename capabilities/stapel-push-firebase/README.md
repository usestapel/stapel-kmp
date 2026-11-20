# stapel-push-firebase

The Firebase Cloud Messaging provider of `stapel-push` — the default push provider of a Stapel app.
Android talks to FCM directly (`firebase-messaging`; a messaging service and a permission activity
come with this artifact's manifest). iOS declares `FirebasePushBridge`, implemented in Swift by the
app shell (`ios-bridges/firebase/StapelFirebasePush.swift`, emitted by the scaffold against the
`firebase-ios-sdk` Swift package) — this artifact links no vendor binary on iOS. Desktop compiles and
reports unsupported. Without `google-services.json` / `GoogleService-Info.plist` the provider is a
working no-op that reports `unconfigured`.

Coordinates: `io.github.usestapel:stapel-push-firebase` (provisional group).

```kotlin
// Android                                   // iOS (the app shell passes StapelFirebasePush.shared)
modules(pushModule(), firebasePushModule())  modules(pushModule(), firebasePushModule(bridge))
// MainActivity.onNewIntent(intent): handleFirebasePushIntent(intent)   // a tap while the app runs
```
