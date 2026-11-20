# Gates and how to run them locally

"Green" in stapel-kmp means the gates below passed on the commit, not that
someone says so. Every gate has an inversion test (a fixture that breaks its
rule and must fail) in `build-logic/convention/src/functionalTest`.

## Lifecycle tasks

| Command | Runs | Where |
|---|---|---|
| `./gradlew stapelCheckFast` | jvm tests, Android host tests, every `check*` gate, `checkKotlinAbi`, `stapelGenerateCheck` | any OS (CI: Linux) |
| `./gradlew stapelCheck` | `stapelCheckFast` + `iosSimulatorArm64Test` + Dokka gate + staging publish | macOS |
| `./gradlew -p build-logic check` | TestKit inversion tests of the gates | any OS |
| `./gradlew publishAllPublicationsToStagingRepository && scripts/verify-artifacts.sh && ./gradlew -p consumer-smoke check` | staging repo, artifact layout, a separate build consuming by coordinates | macOS (all targets) |
| `./gradlew :<module>:stapelCheckFast` | the same for one module | |

Task-name selection runs a task in every project that has it, so
`./gradlew iosSimulatorArm64Test` or `./gradlew dokkaGeneratePublicationHtml`
covers all modules.

## Gates

| Gate (task) | Rule | Fails when | Data it reads |
|---|---|---|---|
| explicit API (compiler) | D12 | a public declaration lacks an explicit visibility / return type | — |
| warnings as errors (compiler) | | any Kotlin warning in main or test code | — |
| `checkPackageRoot` | R2 | a `.kt` file's package is not the module's derived package (or below) | module directory |
| `checkSourceSets` (UI families) | D2 | a UI library has `src/{android,ios,jvm}Main` without `stapel { platformSubject("…") }` | — |
| `checkModuleDocs` (published) | §13 | `README.md` or `MODULE.md` missing | — |
| `checkPublishedDescription` (published) | Central | `stapel { description }` missing | — |
| `checkKoinLeak` | R6 | `org.koin` appears in main sources outside `<pkg>.di` | — |
| `checkWasmReady` (pure families) | D2 | a `commonMain` external dependency is not in `gradle/wasm-ready.txt` | `gradle/wasm-ready.txt` |
| `checkSourceRules` | G-R1/G-R2/G-T1 | library main sources outside `stapel-runtime` use `Dispatchers.*`, `Clock.System`, `System.currentTimeMillis`, `Random.Default` (or the `Random` companion as a value), `Uuid.random()`, platform locale or time zone (G-R1); any library main source uses `runCatching`, a `catch (Throwable/Exception)` not preceded by a rethrowing `CancellationException` clause (and not rethrowing itself), or a `CancellationException` clause that does not rethrow (G-R2); a JUnit 5 test (tools, e2e) with an expression body lacks `: Unit` (G-T1). Comments and strings are ignored. | `gradle/source-rules-baseline.txt` (dated exceptions; a stale line fails) |
| `checkLibraryLines` (KMP modules) | one library line | a JetBrains module and its androidx twin resolve on one classpath: on klib/metadata classpaths always (duplicate `unique_name` breaks intermediate source sets), on jar classpaths unless the JetBrains artifact is a redirect | `REDIRECT_SHIMS` in build-logic, catalog `androidx-*` versions |
| `checkDependencyDirection` sensor 1 | D6 | a declared main project edge breaks the layer table, the same-brick rule or the substrate rank order | `gradle/substrate-ranks.properties` |
| `checkDependencyDirection` sensor 2 | D6/D9/R9/R1 | a resolved main compile/runtime classpath (any target) holds Compose / koin-compose / navigation in a pure module, Room/SQLite outside `*-room`, Material outside the skin, Coil on a brick-UI compile classpath, or a pre-release coordinate not on the allow-list in a published module | `gradle/material-allowed.txt`, `gradle/prerelease-allowlist.txt` |
| `checkCatalog` (root) | R1 | a catalog version is a range/`+`/pre-release not on the allow-list; a depcheck exception expired | `gradle/libs.versions.toml`, allow-list, `gradle/depcheck-exceptions.toml` |
| `checkKotlinAbi` | D12 | the public ABI differs from the committed dump | `api/` dumps in the module |
| verifyTestsRan (inside every test task) | D12 | a test task of a module with `commonTest` sources executed 0 tests | JUnit XML |
| `dokkaGeneratePublicationHtml` | D12/R11 | a public/protected symbol has no KDoc (except packages in `ratchetedPackages`), or any Dokka warning (e.g. an unresolved KDoc link) | — |
| `checkBomMembers` (`stapel-bom`) | feature/consumer | the BOM constrains a consumer module or an unpublished one | — |
| `checkNavManifest` (brick UI with a descriptor) | scaffold | the committed `nav-manifest.json` differs from what the library's `StapelBrick` exports | the descriptor class |
| `checkNavParity` (brick UI) | D11 / §4.4 | a destination or embed of `nav-manifest.json` is unknown to the registry, or a shared field (label key, icon, path, level, parent, menu visibility, auth, surface, order; slot, opens, scope, props, breakpoints) differs; or the brick's `platforms.kmp` block (artifact, entries, embeds — what the scaffold's KMP emitter mounts) is not exactly what the library exports; or a vendored file's sha256 differs from `gradle/composition/pin.json` | `gradle/composition/` (stapel-tools wheel, pinned) |
| `checkKmpRegistry` (root) | scaffold | `kmp-registry.json` differs from what the build exports | published modules, nav manifests, `gradle/released-artifacts.txt` |
| `checkPublishedCatalog` (`stapel-catalog`) | R1 | the generated catalog TOML carries a pre-release version or a consumer module | — |
| `checkReleaseCoordinates` (release path) | R1 | a generated POM / Gradle module file carries a pre-release version — the allow-list does not apply | generated publication metadata |
| `checkDemoCoverage` (`:showcase:gallery`) | showcase | a brick UI module has no `bricks/<brick>/demo` catalogue; a member of a brick's screen table (`interface <Brick>Screens` in its jvm ABI dump) is covered by no story of that brick's catalogue; a `StapelSkin` slot (stapel-ui's dump) is covered by no story; two catalogues share an id | `build/showcase/catalog-index.json` (written by `exportCatalogIndex` from the discovered catalogues), the committed ABI dumps |
| `verifyScreenshots` (`:showcase:gallery`, in `stapelCheck`, macOS only) | D12 | a story × state × {Material3, Foundation} × {light, dark} render of a catalogue that has a `snapshots/` directory differs from its baseline by any pixel, a baseline is missing, or an orphan baseline has no story; actual / expected / diff PNGs go to `showcase/gallery/build/showcase/screenshot-report` | `<catalogue module>/snapshots/*.png` |
| `stapelGenerateCheck` (brick domain, once CODEGEN lands) | D4 | regenerated output differs from the committed generated tree | `contracts/` |

### Stories, baselines, docs export

One source, three uses: the story catalogues (`stapel-showcase`; one
`val storyCatalog` per `bricks/<brick>/demo` and `showcase/stories/<x>`) feed
the gallery, the screenshot baselines and the docs export.

- `./gradlew :showcase:gallery:recordScreenshots [-Pstapel.showcase.catalogs=ui]`
  (macOS) renders every story × state × skin × mode at the pinned policy
  (phone frame 390 dp, density 1, `en`, UTC, reduced motion, default preset,
  fixed frame time, the platform's system font) into `<module>/snapshots/`;
  unchanged pixels keep their file, stale files are removed. A state declared
  `StoryState(…, rtl = true, locale = "ar")` is recorded right to left in that
  locale (the RTL baselines: top bar, list item, search field, code field,
  banner, chip, stepper, menu, navigation bar; auth sign-in, code entry,
  sessions). A story whose content opens a popup declares
  `CanvasOptions(height = …)`: an overlay cannot be measured.
- `verifyScreenshots` compares pixel for pixel. Baselines are recorded and
  verified on macOS only: measured, a macOS CI runner and a local Mac render
  identical pixels, Linux differs in ~4.6 % of pixels (text rasterisation).
  The task is skipped on other systems with that reason.
- `./gradlew :showcase:gallery:exportCatalog` writes
  `showcase/gallery/build/showcase/catalog/` — `catalog.json` (catalogues,
  stories, states with image paths, knobs, coverage, anatomy, snippets) and
  a PNG per story / state / mode (Material 3, density 2) for a docs site.

### Layer table (sensor 1)

Families are **feature** families (substrate, substrate-ui, capability,
capability-ui, brick domain, brick UI, brick adapter, tool, BOM) or
**consumer** families (showcase, demo, e2e — end products, never published as
libraries; samples and `consumer-smoke` are separate builds of the same kind).
A consumer may depend on every feature family of any brick and on other
consumers; a feature module never depends on a consumer (the gate says so by
name); the "a brick never depends on another brick" rule applies to feature
families only. `checkBomMembers` (on `stapel-bom`) fails when the BOM
constrains a consumer or an unpublished module.

| From \ may depend on | substrate | substrate-ui | capability | capability-ui | brick domain | brick ui | brick adapter |
|---|---|---|---|---|---|---|---|
| substrate | lower rank | | | | | | |
| substrate-ui | ✓ | lower rank | | | | | |
| capability | ✓ | | | | | | |
| capability-ui | ✓ | ✓ | its own | | | | |
| brick domain | ✓ | | | | | | |
| brick ui | ✓ | ✓ | | | same brick | | |
| brick adapter | ✓ | | | | same brick | | |
| demo / e2e / showcase (consumers) | ✓ | ✓ | ✓ | ✓ | any brick | any brick | any brick |
| tool | tools only | | | | | | |

Test configurations are not edges. There is no per-module escape hatch; the
only exception file is `gradle/depcheck-exceptions.toml` (empty, each entry
with an expiry date).

### One library line (lifecycle, savedstate, navigationevent)

Compose Multiplatform 1.12.2 asks for both the androidx libraries
(`androidx.lifecycle` 2.11.0, `androidx.savedstate` 1.4.0, …) and older
JetBrains "redirect" artifacts (`org.jetbrains.androidx.lifecycle` 2.9.6,
`org.jetbrains.compose.annotation-internal` 1.10.0, …). The redirects carry no
code, but their metadata klibs share names with the androidx ones, which fails
any `iosMain`/`skikoMain` compilation under `-Werror`. Every configuration
substitutes them by their androidx targets (`REDIRECT_SHIMS`, versions from
the catalog, taken from the published `.module` files), and
`checkLibraryLines` fails when a twin pair shows up anyway. When Compose
Multiplatform moves, re-read the `.module` files and move the catalog's
`androidx-*` versions with it.

### Tests that did not run

- `verifyTestsRan` (in every test task) fails a module whose `commonTest` has
  tests but whose target executed none.
- JUnit 4 (Kotlin multiplatform tests on jvm/Android) fails a non-void test
  method loudly; JUnit 5 (tools, e2e) skips it silently, which G-T1 prevents
  statically. Counting declared `@Test` functions against executed ones per
  target is not done: source sets map to targets differently (`skikoTest`
  never runs on Android), so the count would need a per-target source map; the
  static rule covers the one silent mode we know.

### What the gates cannot see

- Reflection / service-loader / string-keyed coupling between modules (covered by
  the end-to-end run of a brick, not here).
- Whether a KDoc is *true* — Dokka only proves it exists.
- Ambient state reached indirectly (a third-party library reading the clock),
  or through an alias the source scan does not know (`import … as X`).
- Whether `wasm-ready.txt` is honest: the line is a reviewed claim with an
  evidence URL; there is no `wasmJs` compile in v0.
- Android device behaviour: there is no emulator image; device tests are not
  enabled and report "not run".
- The exposure lint (private names) runs in the pre-push hook on the pushing
  machine, not in CI (CI has an empty list).

## Reading a failure

Every gate message names the module, the rule and the fix. Examples:

```
checkDependencyDirection failed:
  - stapel-auth (BRICK_DOMAIN) -> stapel-chat (BRICK_DOMAIN) via commonMainApi: forbidden by the layer table
    (BRICK_DOMAIN may depend on SUBSTRATE). Fix: declare the interface in the substrate and wire the two in the app.
  - stapel-auth: org.jetbrains.compose.runtime:runtime:1.12.2 on jvmCompileClasspath (via project :stapel-network)
    — D6: domain and pure substrate libraries have no Compose.
```

## The reference app (`samples/shop-mobile`)

A separate Gradle build consuming everything by coordinates (staging repo or
mavenLocal), built the way the scaffold's KMP emitter builds an app. CI job
`sample` (after `publish-smoke`): `stapelGenerateCheck`, `checkStapelTokens`,
`NavGateTest` on JVM and the iOS simulator, desktop jar, Android
`assembleDebug`, the iOS framework, then `scripts/ios-smoke.sh` (xcodebuild,
simulator launch, log markers `stapel.app.ready` / `stapel.nav <id>`, a link
handed to the app → `stapel.link <uri> opened`, screenshots). The smoke reads the
markers from the app's own marker file in its data container (launch argument
`-stapelLogFile`) as well as from the unified log, whose filtering differs between
simulator runtimes; an app that exits or crashes while a marker is awaited fails
at once, with `diagnose.log` (marker file, crash report, `log show`) and
`failure.png` uploaded. The job brings up
the auth stand (`bricks/auth/e2e/stand/stand.sh up`, Python 3.12, 127.0.0.1:18731)
first, so the sign-in walk (`SignInWalkTest`, compiled in with
`-Pstapel.walk.baseUrl=<url>`) runs on JVM and on the simulator, and
`ios-smoke.sh --uitest` runs the XCUITest suite against it; the stand goes down
in an `always()` step. Uploads: screenshots, `markers.log`, `diagnose.log`, `uitest.log`, `uitest.xcresult`,
the stand's logs.

## CI capacity and the `ci-ok` verdict

Linux jobs (`build-logic`, `fast`) run for every commit. Each macOS job (`ios`,
`publish-smoke`, `sample`) has a job-level concurrency group shared by every
push to `main`: a newer commit's job replaces an older one that is still
waiting for the group (GitHub keeps one pending job per group); a job that has
the group (running, or queued for a runner) is never cancelled. `ci-ok` runs
`scripts/ci-verdict.sh` over the run's jobs: `GREEN`; `SUPERSEDED by <sha>`
(no job failed, a macOS job was cancelled before it got a runner and `main`
moved on; red, the commit is unverified, the newer one carries the verdict);
or `FAILED`. Rules and inversions: `CiWorkflowTest`.

## ABI dumps

`./gradlew :<module>:updateKotlinAbi` writes the reference dump; commit it with
the change that moved the public surface. `checkKotlinAbi` is part of `check`
and `stapelCheckFast`. Types and members annotated with `@InternalStapelApi`
are excluded.

Compiler-generated Compose singletons (`ComposableSingletons$<File>Kt`, whose
`getLambda$<hash>$<module>` members change hash whenever a callee's signature
in another module changes) are excluded from every dump by the class-name
filter `**.ComposableSingletons**` (KGP `abiValidation.filters.exclude.byNames`,
shared by the JVM and klib dumps; klib dumps never listed them). `$stable`
fields and `$stableprop` members stay: their names do not move with callees.
TestKit fixture: a callee's signature change leaves a dependent's dump
unchanged.
