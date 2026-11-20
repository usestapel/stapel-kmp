# What an app gets

The artifact map as it stands, what each part is proven on, and what only a device can show. For how
the pieces fit see [architecture.md](architecture.md); for what "green" means see [gates.md](gates.md).
Every artifact targets Android, iOS (arm64, simulator arm64; minimum iOS 15.0) and JVM desktop.

## The artifact map

### Substrate (always there)

| Artifact | What the app gets |
|---|---|
| `stapel-result`, `stapel-wire` | `StapelResult`, `Resource`, the wire types generated clients compile against; `MediaUploader` / `MediaPurpose` (avatar, image) |
| `stapel-runtime`, `stapel-config`, `stapel-di` | platform seams (clock, dispatchers, locale, time zone, lifecycle, connectivity, logger), the environment, Koin factories; `LocalePreference` — a language chosen signed out, stored on the device |
| `stapel-network` | the one recover-once pipeline over Ktor (OkHttp / Darwin): bearer, refresh on 401, verification, plug-ins |
| `stapel-session` | tokens in the platform vault, refresh single-flight, logout and farewell hooks |
| `stapel-cache`, `-cache-room`, `-cache-session` | read modes, hybrid storage, per-account partitions wiped on every session end |
| `stapel-i18n`, `stapel-tokens`, `stapel-ui`, `stapel-ui-material3` | strings per locale, design tokens, the Material-free UI kit and its 20-slot skin, the default Material 3 skin |
| `stapel-mvi(-compose)`, `stapel-navigation(-compose)`, `stapel-shell`, `stapel-app` | presenters, navigation, the shell, `stapelApp { … }` assembling all of it |
| `stapel-image`, `stapel-image-compose` | image ladders and tier math; the Coil 3 loader behind every avatar and picture: bearer only for the app's own hosts, host-relative URLs resolved against the environment, caches emptied on every session end |
| `stapel-input(-compose)` | input fields and masks |

### Capabilities (opt in)

| Artifact | What |
|---|---|
| `stapel-captcha` | headless captcha recovery in the pipeline |
| `stapel-analytics` (hub, substrate) + providers `stapel-analytics-firebase`, `stapel-analytics-appmetrica`, `stapel-analytics-collector` (the stapel-analytics backend) | one event API, consent opt-in before anything leaves the device, typed events generated from each brick's `flows.json`; Firebase is the default provider, AppMetrica instead of or beside it |
| `stapel-push` (controller, substrate) + provider `stapel-push-firebase` | permission asked only on a deliberate act, token lifecycle bound to the session, taps routed to deep links, Android channels; iOS through the app shell's Swift bridge (`ios-bridges/`) |

### Bricks (domain + UI, from the backend contracts)

| Brick | Domain / UI / extra | Backend contract pinned |
|---|---|---|
| auth | `stapel-auth`, `stapel-auth-ui`, `stapel-auth-testing` | stapel-auth 0.54.1 |
| profiles | `stapel-profiles`, `stapel-profiles-ui`, `stapel-profiles-testing` | stapel-profiles 0.23.3 |
| cdn | `stapel-cdn` (no UI: the app's `MediaUploader` for avatars and images) | stapel-cdn 0.31.3 |
| notifications | `stapel-notifications` + adapter `stapel-notifications-push` (device registry, mark-read on open, typed push payload) | stapel-notifications 0.31.1 |
| analytics | generated client inside `stapel-analytics-collector` | stapel-analytics 0.8.4 |

Plus `stapel-bom` and `stapel-catalog` (one train version), and the `codegen` / `tokens` Gradle
plugins for the app's own backend modules and theme.

## What is proven where

"Stand" = a backend scaffolded from PyPI by stapel-tools, run natively by the suite's `stand.sh`.
iOS oldest = the oldest hosted simulator runtime (18.5, CI job `sample-oldest-ios`); iOS newest = the
current runtime (26.x on CI, 27.0 locally). No iOS 15/16 runtime can be installed with today's
toolchains: the 15.0 floor is proven by the build gates (`checkIosFloor`, `checkIosBinaries`), not by
execution.

| Area | JVM | iOS sim newest | iOS sim oldest | Android host (JVM, Robolectric where noted) | Android device |
|---|---|---|---|---|---|
| Libraries' own tests (every module) | yes | yes | — | yes | not run |
| Sign-in walk against the auth stand (sample) | yes | yes + XCUITest | yes + XCUITest | — | not run |
| Profiles walk against the auth + profiles + cdn stand (sample): settings, rename, language, avatar upload, the avatar drawn through the app's image loader with the bearer, caches emptied on sign-out | yes | yes | — | — | not run |
| Auth e2e, profiles e2e (incl. language before/after sign-in), notifications push e2e (incl. sign-out order) against stands | yes | — | — | — | not run |
| Image loader on the wire (bearer only at home, redirects, 401 refresh, relative URLs) | yes (OkHttp) | yes (Darwin) | — | — | not run |
| Push walk (sample): prompt, `simctl push` in the background, banner tap → deep link, foreground delivery | — | yes | — | — | not run |
| Android push bridge: tray-tap intent → `PushMessage` → opener, channels, foreground posting and its tap, the permission activity's answer mapping (API 34 and 32) | — | — | — | yes (Robolectric) | not run |
| Swift bridges compiled against the pinned vendor packages (CI `ios-bridges`) | — | build only | — | — | — |
| Android app builds (sample `assembleDebug`, consent manifest check) | — | — | — | build only | not run |

## The owner's device checklist

What no host test can show. Run on a real phone of each platform (and an iOS 15 device for the
floor) with a real Firebase project (`google-services.json` / `GoogleService-Info.plist`) and the
backend's real push sender.

Push (Android):
1. Fresh install: no permission prompt at launch; "Turn on notifications" shows the Android 13+ system
   prompt once; "Allow" registers one device row for the account (check the backend's device list).
2. "Don't allow", then the button again: no second prompt; the state reads denied; turning it on in
   the system settings and returning to the app registers the device.
3. A notification in the background appears in the tray on the channel named by its `group`; tapping
   it opens the app on the linked screen (cold start and while running); it is marked read.
4. A notification while the app is on screen appears as a banner posted by the app; its tap opens the
   link.
5. Sign out: the device row disappears at once (the client's own unregister), the token is deleted;
   the next account on the same phone registers a fresh token.
6. Below Android 13: no prompt, notifications on by default.

Push (iOS):
7. Same steps 1–5 with the iOS prompt; a real APNs token reaches FCM (the simulator walk proves taps
   and foreground delivery, not a real token).
8. On an iOS 15 device: launch, sign-in, the push prompt and a tap open the linked screen.

Analytics:
9. Before consent nothing leaves the device (Firebase DebugView / AppMetrica's event log stay empty);
   "Allow" starts collection; revoking consent stops it.
10. With the Firebase provider: screen views and the generated auth events show in DebugView with the
    mapped names; the account appears only as its hash.
11. With AppMetrica instead of or beside Firebase: the same events in AppMetrica's log.

Images and language:
12. The avatar and other pictures load on mobile data and offline after one view; after sign-out and
    another sign-in, the previous account's pictures are not shown.
13. Choose a language signed out, relaunch: the app starts in it; sign in to a new account: the
    account's profile takes that language; sign in to an account with its own language: that one
    wins; sign out: the signed-out choice is back.

## Not covered yet

- Android emulator / device runs (no AVD on the build host): reported "not run", never "green".
- Execution on iOS 15/16 (no installable runtime): the floor is a build-time proof.
- Video, audio, file and typed-image uploads through `MediaUploader` (the generated `CdnApi` has them).
- A signed-out language picker screen: `LocalePreference` is the API; the app places the picker.
