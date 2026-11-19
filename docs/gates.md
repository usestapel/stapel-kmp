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
| `checkDependencyDirection` sensor 1 | D6 | a declared main project edge breaks the layer table, the same-brick rule or the substrate rank order | `gradle/substrate-ranks.properties` |
| `checkDependencyDirection` sensor 2 | D6/D9/R9/R1 | a resolved main compile/runtime classpath (any target) holds Compose / koin-compose / navigation in a pure module, Room/SQLite outside `*-room`, Material outside the skin, Coil on a brick-UI compile classpath, or a pre-release coordinate not on the allow-list in a published module | `gradle/material-allowed.txt`, `gradle/prerelease-allowlist.txt` |
| `checkCatalog` (root) | R1 | a catalog version is a range/`+`/pre-release not on the allow-list; a depcheck exception expired | `gradle/libs.versions.toml`, allow-list, `gradle/depcheck-exceptions.toml` |
| `checkKotlinAbi` | D12 | the public ABI differs from the committed dump | `api/` dumps in the module |
| verifyTestsRan (inside every test task) | D12 | a test task of a module with `commonTest` sources executed 0 tests | JUnit XML |
| `dokkaGeneratePublicationHtml` | D12/R11 | a public/protected symbol has no KDoc (except packages in `ratchetedPackages`), or any Dokka warning (e.g. an unresolved KDoc link) | — |
| `checkReleaseCoordinates` (release path) | R1 | a generated POM / Gradle module file carries a pre-release version — the allow-list does not apply | generated publication metadata |
| `stapelGenerateCheck` (brick domain, once CODEGEN lands) | D4 | regenerated output differs from the committed generated tree | `contracts/` |

### Layer table (sensor 1)

| From \ may depend on | substrate | substrate-ui | capability | capability-ui | brick domain | brick ui | brick adapter |
|---|---|---|---|---|---|---|---|
| substrate | lower rank | | | | | | |
| substrate-ui | ✓ | lower rank | | | | | |
| capability | ✓ | | | | | | |
| capability-ui | ✓ | ✓ | its own | | | | |
| brick domain | ✓ | | | | | | |
| brick ui | ✓ | ✓ | | | same brick | | |
| brick adapter | ✓ | | | | same brick | | |
| demo / e2e | ✓ | ✓ | ✓ | ✓ | same brick | same brick | same brick |
| tool | tools only | | | | | | |
| showcase | anything | | | | | | |

Test configurations are not edges. There is no per-module escape hatch; the
only exception file is `gradle/depcheck-exceptions.toml` (empty, each entry
with an expiry date).

### What the gates cannot see

- Reflection / service-loader / string-keyed coupling between modules (covered by
  the end-to-end run of a brick, not here).
- Whether a KDoc is *true* — Dokka only proves it exists.
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

## ABI dumps

`./gradlew :<module>:updateKotlinAbi` writes the reference dump; commit it with
the change that moved the public surface. `checkKotlinAbi` is part of `check`
and `stapelCheckFast`. Types and members annotated with `@InternalStapelApi`
are excluded.
