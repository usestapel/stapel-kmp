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
| Test kit of a brick for apps | `bricks/<brick>/testing/` | `stapel-<brick>-testing` | `stapel.<brick>.testing` | `id("stapel.brick-testing")` |
| Published Gradle plugin for apps | `tools/<a>/gradle-plugin/` | `stapel-<a>-gradle-plugin`, id `<group>.<a>` (`-gen` dropped) | `stapel.tools…` | `id("stapel.gradle-plugin")` |
| The published version catalog | `catalog/` | `stapel-catalog` | — | `id("stapel.catalog")` |
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
  `<module>Module()` factory (R6, `checkKoinLeak`). The factory is a
  **function, never a `val`**: a Koin `Module` value keeps its `single`
  instances, so two applications (or two tests) loading one value share them.
  Constructors stay plain; the factory only calls them with `get()`. Prove the
  factory with a graph test that composes it through `stapelKoin` with the
  modules it needs and resolves every binding (see
  `substrate/stapel-cache-session/src/commonTest/.../GraphTest.kt`).
- **No ambient platform state** in library main sources (`checkSourceRules`,
  G-R1): take `StapelDispatchers`, `kotlin.time.Clock`, `IdGenerator`, a
  seeded `Random`, `LocaleProvider` and `TimeZoneProvider` from
  `stapel-runtime` — never `Dispatchers.*`, `Clock.System`, `Random.Default`,
  `Locale.getDefault()`, `NSLocale.currentLocale`. Not even as a default
  parameter value: derive one from an injected seam instead (e.g.
  `Random(clock.now().toEpochMilliseconds())`).
- **Never swallow cancellation** (G-R2): no `runCatching`; put
  `catch (e: CancellationException) { throw e }` before any
  `catch (e: Exception)` / `Throwable`.
- **Pure libraries' `commonMain`** may only use dependencies listed in
  `gradle/wasm-ready.txt` (`checkWasmReady`).
- **UI libraries are `commonMain`-only** unless
  `stapel { platformSubject("why") }` (`checkSourceSets`). With a subject,
  put iOS code in `src/iosMain` (one set for both iOS targets): intermediate
  source sets compile since the build substitutes the JetBrains lifecycle /
  savedstate / navigationevent redirects by their androidx targets
  (`checkLibraryLines`). If your code calls an API that only arrives through
  such a redirect (for example `NavigationBackHandler` from
  `androidx.navigationevent:navigationevent-compose`), declare the androidx
  coordinate from the catalog: the common metadata compilation does not see a
  module reached only through a substitution.
- **Compose UI tests** (`runComposeUiTest` from
  `androidx.compose.ui.test.v2`) go to `src/skikoTest`: they run on jvm
  desktop (headless locally, under xvfb on Linux CI) and on the iOS simulator;
  Android host tests do not run Compose UI. A skin is held to
  `showcase/skin-contract` (`assertSkinContract`,
  `assertTopBarSurvivesLargeText`); call them from the skin's own tests.
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
- **Room** rules in short: Room types `internal`; one database per partition
  owned by the storage library; `RoomCacheOptions.ioContext` is the app's
  `StapelDispatchers.io` (no default); bump `STORE_FORMAT` whenever the stored
  payload or key encoding changes (old files are recreated empty, the cache
  is disposable).
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

## 5. A brick end to end

1. **Pin the contract.** Copy the brick's committed contract files from the
   backend repository at a released tag into `contracts/<brick>/`
   (`schema.json`, `errors.json`, `flows.json`, `capabilities.json`) and write
   `pin.json` with the repository, tag and the commit sha taken from
   `git rev-parse '<tag>^{commit}'` output — never typed by hand. A contract
   gap is fixed in the backend schema and re-pinned; the generator does not
   paper over it.
2. **Cache plan.** Write `contracts/<brick>/cache.json`: per query operation
   its read policy, persistence, scope (user/app), sensitivity and offline
   use; per mutation the key patterns it invalidates and whether it may be
   queued offline. The generator turns it into `<Brick>Keys`, hints and
   `<Brick>Invalidation`.
3. **Generate.** `./gradlew :stapel-<brick>:stapelGenerate` writes
   `bricks/<brick>/domain/src/commonMain/generated/kotlin`; commit the tree.
   `stapelGenerateCheck` (part of `stapelCheckFast`) fails on any drift.
   Undocumented schema elements are counted against the ratchet in
   `contracts/<brick>/ratchet.json`; the count only goes down.
4. **Domain** (`bricks/<brick>/domain`, hand-written next to the generated
   tree): repositories over `stapel-cache` (`observe` with the generated keys
   and hints, invalidation after mutations), presenters in `.presentation`
   (state + effects, no navigator), the Koin factory `<brick>Module()`.
   Tests use `stapel-testing` (`FakeApiTransport` by operation id,
   `FakeTransport` under the real pipeline) and `stapel-cache-testing`.
5. **UI** (`bricks/<brick>/ui`): stateless screens taking presenter state and
   emitting intents, drawn only through `stapel-ui` wrappers (`StapelButton`,
   `StapelTabs`, `StapelTopBar`, …) so any skin applies; strings in an
   `I18nBundle` per locale; Compose UI tests in `src/skikoTest` under both
   skins, at 200 % text in every shipped locale.
6. **Descriptor and manifest.** In the UI library, one `StapelBrick`
   implementation `stapel.<brick>.ui.di.<Brick>Brick` (no-argument
   constructor; `modules()` must not bind the cache-session binding;
   `navigation(context)` reads the graph only inside composables). Then
   `./gradlew :stapel-<brick>-ui:exportNavManifest` and commit
   `nav-manifest.json`; `checkNavManifest` (drift) and `checkNavParity`
   (shared fields equal the pinned stapel-tools registry) are part of
   `stapelCheckFast`; `./gradlew exportKmpRegistry` and commit
   `kmp-registry.json`. The build generates `internal object
   <pkg>.generated.StapelBuildInfo` (group, artifact id, train version) for
   brick UI libraries. A test kit for apps goes to `bricks/<brick>/testing`.
7. **Demo** (`bricks/<brick>/demo`): stories for the showcase and a desktop
   entry point; never published.
8. **End to end** (`bricks/<brick>/e2e`): JUnit 5 tests against a real
   backend started locally (`-Pstapel.e2e.baseUrl=…`); skipped without it.
   Declare expression-bodied tests `(): Unit` (`fun x(): Unit = runBlocking
   { … }`): JUnit 5 silently skips a test method that returns a value
   (G-T1 catches it).

## 6. Before you hand over

```sh
source scripts/env.sh
./gradlew :stapel-<x>:updateKotlinAbi        # first time, and whenever the public API changes on purpose
./gradlew :stapel-<x>:stapelCheckFast        # jvm + android host tests + all gates + ABI
./gradlew :stapel-<x>:stapelCheck            # + iOS simulator tests + Dokka gate + staging publish (macOS)
git status                                   # the ABI dump under api/ is committed with the change
```

See docs/gates.md for what each gate checks and how to read its failure.
