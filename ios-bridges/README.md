# ios-bridges — the Swift halves of vendor providers

Kotlin/Native cannot import a Swift-only SDK, and no Stapel artifact links a vendor binary (no
cinterop, no SwiftPM import, no CocoaPods — ruling K3). A provider artifact therefore declares a
small Objective-C-exportable Kotlin interface (the *bridge*); the app implements it in Swift against
the vendor's Swift package and hands the instance to Kotlin at start. The Swift sources live once,
here; the scaffold vendors them by the same pin as the rest of the repository and copies them into
the app shell, substituting only the framework name in `import Shared`.

| Path | What | Kotlin side |
|---|---|---|
| `core/StapelAppDelegate.swift` | the app's one `UIApplicationDelegate` + `UNUserNotificationCenterDelegate` (`@UIApplicationDelegateAdaptor`); forwards APNs and notification callbacks to every installed push bridge (`StapelNotificationHandling`) | — |
| `<vendor>/Stapel<Vendor>.swift` | configures the vendor SDK once, only when its configuration file is bundled | — |
| `<vendor>/Stapel<Vendor>Push.swift` | implements `<Vendor>PushBridge` | `capabilities/stapel-push-<vendor>` |
| `<vendor>/Stapel<Vendor>Analytics.swift` | implements `<Vendor>AnalyticsBridge` | `capabilities/stapel-analytics-<vendor>` |

Rules for a bridge:

- Minimum iOS 15.0; an API newer than 15 only behind `if #available` with a working iOS 15 branch.
- A transliteration: only strings, booleans, string maps and single-shot callbacks cross; the Kotlin
  half decides everything (state, retries, naming limits). Completions are called exactly once.
- Swizzling off for every SDK; every OS callback goes through `StapelAppDelegate`.
- Never log or describe a token: errors are passed as `localizedDescription`.
- The bridge's protocol version is the provider artifact's `*_BRIDGE_ABI` constant; a change to the
  Kotlin interface bumps it and changes the Swift file in the same commit.

| Directory | Kotlin protocol (artifact, ABI) | Vendor package (exact) | Products |
|---|---|---|---|
| `firebase/` | `FirebasePushBridge` (`stapel-push-firebase`, 1), `FirebaseAnalyticsBridge` (`stapel-analytics-firebase`, 1) | `firebase-ios-sdk` 13.2.0 | `FirebaseCore`, `FirebaseMessaging`, `FirebaseAnalyticsCore` (no IDFA) |
| `appmetrica/` | `AppMetricaAnalyticsBridge` (`stapel-analytics-appmetrica`, 1) | `appmetrica-sdk-ios` 7.0.0 | `AppMetricaCore` (no `AppMetricaAdSupport`) |

## The compile check (run by hand; CI job `ios-bridges` proposed, not wired yet)

`check/` is a separate Gradle build that makes the Kotlin framework `Shared` (static, iOS 15.0,
exporting the provider artifacts) from the staging repository, the way a scaffolded app's `shared`
module does; `Package.swift` compiles `core/`, `firebase/` and `appmetrica/` together against it and
the vendor packages at the pinned exact versions. A protocol change fails there with the missing
member named.

```sh
./gradlew publishAllPublicationsToStagingRepository                      # repository root
./gradlew -p ios-bridges/check :shared:assembleSharedReleaseXCFramework  # -Pstapel.repo=local for mavenLocal
cd ios-bridges && xcodebuild -scheme StapelBridges -destination 'generic/platform=iOS Simulator' build
```

A new bridge adds its directory to `sources` (if new) and its vendor products to `Package.swift`, and
its artifact to `bridged` in `check/shared/build.gradle.kts`.
