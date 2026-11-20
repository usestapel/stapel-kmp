# Toolchain and measured versions

## The line

| Piece | Version | Why |
|---|---|---|
| Gradle (wrapper) | 9.7.0 | upper bound of Kotlin 2.4.21's tested range; `distributionSha256Sum` pinned in the wrapper |
| Kotlin (+ serialization, Compose compiler) | 2.4.21 | current stable |
| AGP (`com.android.kotlin.multiplatform.library`) | 9.3.1 | upper bound for Kotlin 2.4.21; compileSdk 37 needs ≥ 9.1.1 |
| compileSdk / minSdk | 37 / 24 | |
| Compose Multiplatform | 1.12.2 | Material 3 for CMP 1.12.x is `org.jetbrains.compose.material3:material3:1.9.0` (what the CMP 1.12.2 plugin maps `material3` to). Move to 1.13.x when stable, before the first publish (R1) |
| JDK running Gradle | 21 | bytecode targets 17 (`-Xjdk-release=17`), no toolchain download |

These move together, in one commit, only when the Kotlin multiplatform
compatibility table lists the new AGP/Gradle pair.

## Measured on the bootstrap workstation (2026-11-19, Apple silicon)

| What | Result |
|---|---|
| Xcode | 27.2 (27B52), selected with `DEVELOPER_DIR`; Kotlin 2.4.21 is only *tested* up to 26.4 |
| iOS klibs (`iosArm64`, `iosSimulatorArm64`) | compile |
| `iosSimulatorArm64Test` | green, including a Compose `runComposeUiTest` on the simulator |
| KGP/AGP "untested combination" warnings | none in the build output |
| Android host tests | `testAndroidHostTest` runs `commonTest` (verified by JUnit XML) |
| Compose desktop UI test (`jvmTest`, `runComposeUiTest`) | green headless on macOS; Linux CI runs it under `xvfb-run` until proven without |
| ABI dumps | `api/jvm/<artifact>.api`, `api/android/<artifact>.api`, `api/<artifact>.klib.api` |

CI uses `macos-26` with `DEVELOPER_DIR=/Applications/Xcode_26.4.app/Contents/Developer`
(the tested Xcode); local runs use whatever `scripts/env.sh` selects.

## iOS floor and simulator runtimes (measured 2026-11-20)

The minimum iOS is 15.0 (`IOS_FLOOR`, see [gates.md](gates.md#the-ios-floor)).
Compose Multiplatform 1.12.2 supports iOS 14+. Simulator runtimes: Xcode 27.2
downloads iOS 26.0 and later only; `macos-15` runners carry iOS 18.5 (with
Xcode 26.3), the oldest runtime CI executes (`sample-oldest-ios`); `macos-14`
(iOS 17) is refused by GitHub as unsupported.

## Environment

`source scripts/env.sh` sets, for the current shell only:

- `JAVA_HOME` → Homebrew `openjdk@21` (keg-only; nothing registered globally);
- `ANDROID_HOME` → `~/Library/Android/sdk` unless already set;
- `DEVELOPER_DIR` → `/Applications/Xcode.app/Contents/Developer` unless already set.

Nothing global is changed: no `xcode-select -s`, no sudo, no `local.properties`.
`scripts/doctor.sh` checks all of it and prints the fix for any miss. There is
no Android emulator image; device tests are not enabled.
