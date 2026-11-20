# stapel-analytics-firebase — module map

- **Provides:** `FirebaseAnalyticsBridge` (the vendor half; Obj-C-exportable: `String`, `Boolean`,
  `Map<String, String>` only), `FIREBASE_ANALYTICS_BRIDGE_ABI = 1`, `firebaseAnalyticsSink(bridge,
  whenMissing, configFile)`; Android `firebaseAnalyticsSink(platform)`; `di.firebaseAnalyticsModule()`
  (Android, desktop) / `di.firebaseAnalyticsModule(bridge)` (iOS). Contribution key `analytics.firebase`.
- **Decisions in Kotlin, not in Swift:** names mapped into Firebase's rules (letters, digits,
  `_`, start with a letter, ≤ 40, `x_` before reserved names and `firebase_`/`google_`/`ga_`),
  ≤ 25 parameters (sorted by key), values ≤ 100, user property names ≤ 24 and values ≤ 36; the user id
  follows each event's `userHash`; a page is a `screen_view` with `screen_name`; a name with no usable
  character is rejected (`Partial`, never re-sent); a vendor exception is `Retry`.
- **Consent in the built artifact:** `src/androidMain/AndroidManifest.xml` merges
  `firebase_analytics_collection_enabled=false`, `google_analytics_adid_collection_enabled=false`,
  `google_analytics_default_allow_ad_personalization_signals=false` and removes
  `com.google.android.gms.permission.AD_ID` into every app that links this artifact.
- **Tests:** `FirebaseSinkConformanceTest` subclasses `AnalyticsSinkConformance` against a recording
  bridge on jvm, Android host and iOS simulator; naming, rejection, missing-bridge and user-id order
  tests. The Android wrapper over the SDK statics is compiled, not unit-tested (no Play services on a
  host); the Swift bridge is compiled against the pinned SDK by the `ios-bridges` CI check.
- **What not to do:** no decision in the Swift bridge; no IDFA (`FirebaseAnalyticsCore`, not
  `FirebaseAnalytics`); no ATT prompt; never a dependency of a facade or brick.
