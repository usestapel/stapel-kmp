# shop-mobile — the reference app

What the stapel-tools KMP emitter generates, written by hand once and kept building: a separate Gradle
build that consumes stapel-kmp only by coordinates (the published `stapel-catalog`, `stapel-bom` and the
`io.github.usestapel.codegen` / `.tokens` plugins), never this repository's `build-logic`.

| Path | What |
|---|---|
| `shared/` | KMP library (android, iosArm64, iosSimulatorArm64, jvm): `createShopApp` = one `stapelApp { bricks(AuthBrick(), ProfilesBrick()) }`, the shop's own screens, the generated `orders` client, `AppTokens` from `stapel.theme.json` |
| `desktopApp/`, `androidApp/` | platform hosts (a `main`, an `Application` + `Activity`) |
| `iosApp/iosAppUITests/` | XCUITest suite (shared scheme `iosApp`): fresh install without a notice, mock-code sign-in against a stand, a deep link through Safari's "Open in …?" prompt, the edge-swipe back, the Keychain vault across relaunches and its wipe on sign-out |
| push | `stapel-push` + `stapel-push-firebase` + `stapel-notifications-push`: `createShopApp(pushProvider = firebasePushModule(…))`, taps attached to `openUrl`, a "Turn on notifications" button on home (never asked at launch). iOS compiles the Swift bridges in place from `../../ios-bridges` (core + firebase) against `firebase-ios-sdk` 13.2.0 (Swift package, exact version) with `FirebaseMessaging`; `iosApp/iosApp/GoogleService-Info.plist` is a **fixture with fake identifiers**; Android applies no google-services plugin (the provider reports unconfigured) |
| `iosApp/iosAppUITests/PushUITests.swift` | the push walk (`scripts/ios-smoke.sh --push`): sign-in, the person turns push on (system prompt), `simctl push` in the background, the banner tapped in SpringBoard opens the order of `data.url`; a push in the foreground |
| `iosApp/` | Xcode project mirroring the official Kotlin Multiplatform wizard (kmp.jetbrains.com, Kotlin 2.4.21 / CMP 1.12.2 template, objectVersion 77, file-system-synchronized groups, direct integration through `:shared:embedAndSignAppleFrameworkForXcode`; no CocoaPods) |
| `contracts/orders/` | the app's own backend module contract (input of the codegen plugin) |
| `stapel.theme.json`, `stapel.environments.json`, `stapel.composition.json` | the scaffold's inputs/outputs |

```sh
# at the repository root, once per change of the libraries:
./gradlew publishAllPublicationsToStagingRepository
cd samples/shop-mobile
./gradlew :shared:stapelGenerateCheck :shared:checkStapelTokens :shared:jvmTest :shared:iosSimulatorArm64Test
./gradlew :desktopApp:run --args=--smoke :androidApp:assembleDebug
scripts/ios-smoke.sh                                    # xcodebuild + simulator launch + deep link
SHOP_STAND_URL=http://127.0.0.1:18731/ scripts/ios-smoke.sh --uitest   # + the XCUITest suite (fresh install)
SHOP_STAND_URL=http://127.0.0.1:18731/ scripts/ios-smoke.sh --push     # + the push walk (simctl push, banner tap)
# the sign-in walk against a local auth stand (bricks/auth/e2e/stand/stand.sh up):
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test -Pstapel.walk.baseUrl=http://127.0.0.1:18731/
# the profiles walk (sign in, settings, rename, auth.security inside settings, language, avatar) against a
# stand with auth + profiles + cdn (bricks/profiles/e2e/stand/stand.sh up):
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test -Pstapel.walk.profilesBaseUrl=http://127.0.0.1:18761/
```

`-Pstapel.repo=local` resolves from mavenLocal instead of the staging repository.
