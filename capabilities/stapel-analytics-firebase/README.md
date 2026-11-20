# stapel-analytics-firebase

Google Analytics for Firebase as a sink of the `stapel-analytics` hub — the default vendor
provider. Only an app that chose Firebase depends on this artifact; no facade, brick or
`stapel-app` names it (gate G-X2).

Coordinates: `io.github.usestapel:stapel-analytics-firebase` (group provisional until the first publish).

| Platform | How | Without config |
|---|---|---|
| Android | links `com.google.firebase:firebase-analytics` 24.0.0 | no `google-services.json` → `Unconfigured("google-services.json")`, a no-op |
| iOS | the app shell's Swift bridge `StapelFirebaseAnalytics` (`ios-bridges/firebase`, Firebase Apple SDK 13.2.0, product `FirebaseAnalyticsCore`) | no `GoogleService-Info.plist` → `Unconfigured("GoogleService-Info.plist")` |
| Desktop | — | `Unsupported("desktop")` |

```kotlin
// androidMain / jvmMain
modules(analyticsModule(), firebaseAnalyticsModule())
// iosMain: the Swift bridge comes from the app shell
modules(analyticsModule(), firebaseAnalyticsModule(StapelFirebaseAnalytics.shared))
```

Consent: the artifact's Android manifest sets `firebase_analytics_collection_enabled=false`,
`google_analytics_adid_collection_enabled=false` and removes the `AD_ID` permission; the app's
`Info.plist` carries `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (scaffold). The hub switches
collection on only after consent.
