# Adding a module

This is the entry point for anyone (human or agent) creating a module in
stapel-kmp. You create a directory with a `build.gradle.kts`; settings discovers
it. You never edit `settings.gradle.kts`, `gradle/libs.versions.toml` or
`build-logic/` — those are owned by the build. If you need a library that is not
in the catalog, or a gate data change (a substrate rank, a wasm-ready line), ask
the build owner for a one-line change.

## 1. Pick the family

| You are writing | Directory | Gradle project = Maven artifact | Kotlin package | First line of `build.gradle.kts` |
|---|---|---|---|---|
| Pure substrate (no Compose) | `substrate/stapel-<x>/` | `stapel-<x>` | `stapel.<x>` (dashes → dots) | `id("stapel.substrate")` |
| Compose substrate | `substrate/stapel-<x>/` | `stapel-<x>` | `stapel.<x>` | `id("stapel.substrate-ui")` |
| Optional backend capability | `capabilities/stapel-<x>/` | `stapel-<x>` | `stapel.<x>` | `id("stapel.capability")` |
| Compose half of a capability | `capabilities/stapel-<x>-ui/` | `stapel-<x>-ui` | `stapel.<x>.ui` | `id("stapel.capability-ui")` |
| Brick domain library | `bricks/<brick>/domain/` | `stapel-<brick>` | `stapel.<brick>` | `id("stapel.brick-domain")` |
| Brick UI library | `bricks/<brick>/ui/` | `stapel-<brick>-ui` | `stapel.<brick>.ui` | `id("stapel.brick-ui")` |
| Platform adapter of a brick | `bricks/<brick>/adapters/<x>/` | `stapel-<brick>-<x>` | `stapel.<brick>.<x>` | `id("stapel.brick-adapter")` |
| Demos of a brick's UI (unpublished) | `bricks/<brick>/demo/` | `stapel-<brick>-ui-demo` | `stapel.<brick>.ui.demo` | `id("stapel.brick-demo")` |
| End-to-end tests (unpublished) | `bricks/<brick>/e2e/` | `stapel-<brick>-e2e` | `stapel.<brick>.e2e` | `id("stapel.brick-e2e")` |
| JVM tool / generator (unpublished in v0) | `tools/<a>/<b>/` | `:tools:<a>:<b>` | `stapel.tools…` | `id("stapel.jvm-tool")` |
| Showcase library (unpublished) | `showcase/<a>/` | `:showcase:<a>` | `stapel.showcase…` | `id("stapel.showcase")` |
| Room storage (only `*-room` artifacts) | `substrate/stapel-<x>-room/` | `stapel-<x>-room` | `stapel.<x>.room` | `id("stapel.substrate")` + `id("stapel.room")` |

Add-on plugin for any Kotlin module: `id("stapel.serialization")`
(serialization compiler plugin + `kotlinx-serialization-json` as `api`;
`stapel.brick-domain` already includes it).

The Maven group (`stapel.group` in `gradle.properties`, provisionally
`io.github.usestapel`) is **not** part of the package: packages are rooted at
`stapel` (ruling R2). `checkPackageRoot` fails any file outside the derived
package.

## 2. Minimal module

```kotlin
// substrate/stapel-result/build.gradle.kts
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Result and error types for Stapel clients." // POM description, required when published
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelRuntime)            // other modules: typesafe accessors
            implementation(libs.kotlinx.coroutines.core) // libraries: catalog aliases only
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
```

Files every published module needs: `README.md` (human entry: what, coordinates,
ten-line usage) and `MODULE.md` (seams, invariants, what not to do) —
`checkModuleDocs`. Sources go to `src/commonMain/kotlin/stapel/<x>/…`, tests to
`src/commonTest/kotlin/…`.

What the family plugin gives you, so you do not write it:

- targets `android` (Android-KMP library, compileSdk 37, minSdk 24, host tests on),
  `jvm`, `iosArm64`, `iosSimulatorArm64`; JVM bytecode 17;
- explicit API mode, all warnings as errors, progressive mode;
- `kotlin("test")` + `kotlinx-coroutines-test` in `commonTest`;
- ABI validation, Dokka gate, publishing coordinates/POM, all `check*` gates.

## 3. Rules a module build file must respect

- **No repositories, no versions.** Repositories live in settings
  (`FAIL_ON_PROJECT_REPOS`); versions live in the catalog.
- **Dependency direction** (`checkDependencyDirection`, docs/gates.md): domain →
  substrate only; UI → its own domain + substrate; a brick never depends on
  another brick; substrate edges go downward by rank
  (`gradle/substrate-ranks.properties` — a new substrate module needs a rank
  there, ask the build owner).
- **Koin** only in the package `<module package>.di`, holding the one public
  `<module>Module()` factory (R6, `checkKoinLeak`).
- **Pure libraries' `commonMain`** may only use dependencies listed in
  `gradle/wasm-ready.txt` (`checkWasmReady`).
- **UI libraries are `commonMain`-only** unless
  `stapel { platformSubject("why") }` (`checkSourceSets`). Compose UI tests
  (`runComposeUiTest`) go to `src/skikoTest` (runs on jvm desktop + iOS
  simulator; Android host tests do not run Compose UI).
- **Material** only in `stapel-ui-material3` (R9); brick UI never sees Coil.
- **Room** only in `*-room` artifacts with `id("stapel.room")`; schemas are
  committed under `schemas/`. Proven on every target (Room 3.0.3 + KSP 2.3.14 +
  bundled SQLite 2.7.1) by a build probe, now replaced by
  `substrate/stapel-cache-room` as the reference module. What it showed:
  - keep Room types (`@Database`, `@Dao`, `@Entity`) `internal`; a public
    `RoomDatabase` subclass drags Room's KDoc (unresolvable links) into the
    Dokka gate;
  - the builder is platform API: an `internal expect fun …Builder()` with
    actuals in `androidMain`/`jvmMain`/`iosMain` calling
    `Room.inMemoryDatabaseBuilder<T>()` / `Room.databaseBuilder<T>(path)`;
  - the `@ConstructedBy` `expect object` needs `-Xexpect-actual-classes`, which
    `stapel.room` sets for `*-room` modules only;
  - Android host tests cannot load the bundled SQLite JNI library, so database
    tests go to `src/sqliteTest` (runs on jvm + iOS simulator); `commonTest`
    still runs on all three.
- **Generated code** (brick domain): `src/commonMain/generated/kotlin` is already
  a source root. Generated packages that are under the KDoc ratchet (R11) are
  declared with `stapel { ratchetedPackages.add("stapel\\.auth\\.api(\\..*)?") }`;
  everything else must have zero undocumented public symbols.
- **Internal-but-public** cross-module API carries `@InternalStapelApi`
  (from `stapel-result`); it is excluded from ABI dumps and docs.

## 4. Generators (CODEGEN / tokens)

`stapel.brick-domain` registers `stapelGenerate` and `stapelGenerateCheck` as
soon as the project `:tools:codegen:cli` exists. They run
`stapel.tools.codegen.cli.MainKt` (constant `CODEGEN_MAIN_CLASS`) with:

```
gen [--check] --module stapel-<brick> --brick <brick> --repo-root <abs path>
```

`--check` must regenerate into a temp dir and exit non-zero on any difference.
`stapelGenerateCheck` is part of `check` and `stapelCheckFast`. Any other module
(e.g. `stapel-tokens`) may register its own tasks with these two names in its
own build file; the root lifecycle picks them up by name.

## 5. Before you hand over

```sh
source scripts/env.sh
./gradlew :stapel-<x>:updateKotlinAbi        # first time, and whenever the public API changes on purpose
./gradlew :stapel-<x>:stapelCheckFast        # jvm + android host tests + all gates + ABI
./gradlew :stapel-<x>:stapelCheck            # + iOS simulator tests + Dokka gate + staging publish (macOS)
git status                                   # the ABI dump under api/ is committed with the change
```

See docs/gates.md for what each gate checks and how to read its failure.
