# Architecture

One page on how stapel-kmp is put together: the module families, which way
dependencies point, where generated code stops and hand-written code starts,
and how a project changes the look without forking a screen. For the rules a
module build file must follow see [adding-a-module.md](adding-a-module.md);
for what "green" means see [gates.md](gates.md).

## What the repository delivers

For every backend brick (auth, and the ones that follow) two published
libraries:

- **`stapel-<brick>`** — the domain library. Pure Kotlin, no Compose: the
  generated API client, hand-written repositories over the cache, and
  presenters (state holders) for its screens.
- **`stapel-<brick>-ui`** — the UI library. Compose Multiplatform screens that
  render the presenters' state through the skin seam, with no Material
  dependency of their own.

Both sit on a **substrate** of small artifacts, so an app pulls only what it
uses. Optional backend features (captcha today) are **capabilities**: separate
artifacts a project opts into; nothing optional is on the classpath by
default. Targets for every library: Android, iOS (arm64 and simulator arm64)
and JVM desktop.

## Module families

| Family | Directory | Examples | May depend on |
|---|---|---|---|
| Pure substrate | `substrate/stapel-<x>` | result, runtime, config, wire, network, session, cache, cache-room, cache-session, di, mvi, navigation, i18n, tokens, testing | lower-ranked pure substrate |
| Compose substrate | `substrate/stapel-<x>` | ui, ui-material3, mvi-compose, navigation-compose, shell | pure substrate, lower-ranked Compose substrate |
| Capability | `capabilities/stapel-<x>` | captcha | pure substrate |
| Brick domain | `bricks/<brick>/domain` | `stapel-auth` | pure substrate |
| Brick UI | `bricks/<brick>/ui` | `stapel-auth-ui` | substrate, its own brick's domain |
| Demo / end-to-end (consumer) | `bricks/<brick>/{demo,e2e}` | unpublished | any feature module, any brick |
| Tools | `tools/` | codegen, tokens-gen | other tools |
| Showcase (consumer) | `showcase/` | gallery, desktop host, skin contract | any feature module, any brick |

Every family is either a **feature** family or a **consumer** family.
Feature families build the product: substrate, capabilities, brick domain,
brick UI, brick adapters, and the tools that generate them. Consumer families
are end products that only use it: the showcase, brick demos, end-to-end
suites, samples and the consumer smoke build; they are never published as
libraries. A consumer may depend on any feature module of any brick (the
showcase shows every brick's screens); a feature module never depends on a
consumer; consumers are never in the BOM, the published catalog or
`kmp-registry.json`.

The direction is strictly downward: `stapel-<brick>-ui → stapel-<brick> →
substrate`. A brick never depends on another brick; when two bricks need to
talk, the interface lives in the substrate and the app wires both. Within the
substrate every artifact has a rank (`gradle/substrate-ranks.properties`) and
edges go from a higher rank to a lower one, which keeps the substrate acyclic
as it grows. `checkDependencyDirection` enforces all of this on declared edges
and on the resolved classpaths of every target.

### The substrate, bottom to top

- **`stapel-result`** — `StapelResult<T, E>`, the error model
  (`StapelError`, the shared `TransportFailure` family, server error
  envelopes), `Resource<T>` load state and error-text resolution. No
  dependencies.
- **`stapel-runtime`** — the platform seams: dispatchers, clock, id
  generator, locale and time-zone providers, app lifecycle, connectivity,
  logging and telemetry. It is the only module that reads the platform for
  these (gate G-R1); everything else receives them by constructor.
- **`stapel-config`** — environment, service endpoints, posture
  (prototype / live) and the client's identity.
- **`stapel-wire`** — the Ktor-free runtime generated clients compile
  against: `OperationSpec`, `OperationCall`, `ApiTransport`, pagination types,
  `CacheKey`/`KeyPattern` (including their storage form), wire JSON.
- **`stapel-network`** — implements `ApiTransport` over Ktor (OkHttp on
  Android and JVM, Darwin on iOS) with one recover-once pipeline: bearer auth,
  refresh on 401, step-up on a verification challenge, plug-ins (captcha),
  retry policy. No cookies.
- **`stapel-session`** — the token vault (Keystore / Keychain / owner-only
  file), single-flight refresh, `SessionState` (with the account id read from
  the access token), logout hooks.
- **`stapel-cache`** — read policies (network-only, network-first,
  cache-first, stale-while-revalidate), `Resource` flows, partitions per
  account, invalidation by key pattern, refetch on foreground and on
  reconnect. Storage is a seam: memory by default, **`stapel-cache-room`**
  (Room 3, one database per partition) when the app opts in, chosen
  explicitly and never discovered from the classpath.
  **`stapel-cache-session`** keeps the cache's partition on the session's
  account and wipes it when the session ends.
- **`stapel-di`** — Koin conventions: a strict composition root
  (`stapelKoin`), contributions, the session scope.
- **`stapel-mvi`** / **`stapel-navigation`** — headless presenters and flow
  machines; a pure navigation model (route plan, back stack, guards, deep
  links).
- **`stapel-i18n`**, **`stapel-tokens`** — UI strings with plural rules and a
  runtime locale; design tokens as a Kotlin value.
- Compose: **`stapel-ui`** (theme from tokens, layout, the skin seam, the
  Material-free foundation skin), **`stapel-ui-material3`** (the default skin),
  **`stapel-mvi-compose`** (presenter retention and saved state),
  **`stapel-navigation-compose`** (a navigation host over a hidden
  Navigation 3 engine), **`stapel-shell`** (adaptive app chrome).
- **`stapel-app`** — the app-facing composition: `StapelBrick`, `stapelApp { }`,
  `StapelAppContent`.
- **`stapel-testing`**, **`stapel-cache-testing`** — fakes of the seams and
  the storage contract suite, for tests in this repository and in apps.

## Wiring: constructors first, Koin as an option

Every class takes its collaborators in its constructor; no library reaches
for a global. Each library additionally ships one Koin factory function,
`<module>Module()`, in its `di` package (the only package allowed to import
Koin). An app composes them:

```kotlin
stapelKoin(
    listOf(
        runtimeModule(PlatformContext(application)),
        networkModule(environment),
        sessionModule(),
        cacheModule(),
        cacheRoomModule(),        // optional: durable storage
        cacheSessionModule(),     // partitions follow the session
        authModule(bindCacheToSession = false), // the brick; it can bind the same binding itself instead
    ),
)
```

`stapelKoin` refuses a binding declared twice, so the cache-session binding
comes from exactly one place.

The factories are functions, never shared `val`s: a Koin module value keeps
its `single` instances, so two applications loading the same value would
share them. A project that does not use Koin wires the same constructors by
hand.

### The app-facing entry: `stapelApp { }` (`stapel-app`)

A generated app does not assemble the factories above itself. Each brick UI
library exposes one `StapelBrick` descriptor (`stapel.<brick>.ui.di.<Brick>Brick`,
a constructor whose parameters all have defaults) carrying its Koin modules,
navigation, strings, error catalogs, required capabilities, the sign-in owner
and presenter creation; `stapel-app` assembles the substrate around a list of
them:

```kotlin
val app = stapelApp(PlatformContext()) {
    environment(environment)
    offline(cacheRoomModule())          // optional durable cache: the only line offline adds
    bricks(AuthBrick())
    navigation(home = DestinationId("shop.home"))
    theme { content -> StapelMaterialTheme(tokens = AppTokens, content = content) }
}
// StapelAppContent(app); app.openUrl(url); app.close()
```

The builder installs runtime, network, session, cache, the optional storage
and `cacheSessionModule()` exactly once (bricks never bind the cache to the
session), so the duplicate binding cannot happen through it. It is sugar over
the public factories: manual wiring stays possible. The reference app built
this way, by coordinates only, is `samples/shop-mobile`.

## What the scaffold reads from this repository

- `kmp-registry.json` (root, `exportKmpRegistry` / drift gate
  `checkKmpRegistry`): the train, the group, the BOM and catalog ids, the two
  Gradle plugin ids, every published artifact with its family, and per brick
  its domain / UI / testing artifacts, its descriptor class and the nav ids it
  ships (from the committed `nav-manifest.json`).
- `nav-manifest.json` per brick UI library (`exportNavManifest`, drift gate
  `checkNavManifest`, parity gate `checkNavParity` against the digest-pinned
  stapel-tools `composition-registry.json` in `gradle/composition/`).
- `substrate/stapel-shell/shell-templates.json` for the shell templates.
- Published for the generated build: `stapel-bom`, `stapel-catalog` (a Gradle
  version catalog with every stapel coordinate plus the toolchain pins),
  `io.github.usestapel.codegen` (clients for the app's own backend modules)
  and `io.github.usestapel.tokens` (`stapel.theme.json` → tokens, no Node).

## Generated and hand-written code

Everything API-shaped is generated from the brick's committed contract
(`contracts/<brick>/`: the OpenAPI schema, error table, flows, capabilities,
the cache plan `cache.json`, and `pin.json` naming the backend commit it came
from). `tools/codegen` (KotlinPoet) writes it to
`bricks/<brick>/domain/src/commonMain/generated/kotlin`:

- models, enums with an `Unknown(wire)` case, request/response types;
- one `<Brick>Api` with an operation per endpoint returning
  `StapelResult<T, <Op>Error>`; per-operation error types bound to the codes
  the operation can return;
- pagination wrappers, cache keys (`<Brick>Keys`) and the invalidation table
  (`<Brick>Invalidation`) from `cache.json`;
- KDoc from every summary and description.

The generated tree is committed and never edited by hand;
`stapelGenerateCheck` regenerates and fails on any difference. Hand-written
code lives beside it in the same module: repositories that combine operations
with cache policies, the session binding, presenters. When the contract is
wrong the fix goes to the backend schema and is re-pinned, not patched in the
generator.

## The skin seam

Brick UI never draws a Material component. It composes against
`stapel-ui`: the theme (colours, type, spacing and shape roles from
`stapel-tokens`) and `StapelSkin`, a table of 20 component slots (button,
text field, top bar, navigation items, dialog, sheet, list item, …). A skin
fills the slots:

- `FoundationSkin` (in `stapel-ui`) draws with Compose foundation only;
- `Material3Skin` (in `stapel-ui-material3`) maps the roles onto a Material 3
  colour scheme and fills the slots with Material components — the default.

A project changes the look at three levels, none of which forks a screen:
tokens (a theme preset or its own token source), a skin delegation that
replaces individual slots and keeps the rest, or a whole skin. Every skin is
held to the same contract tests (`showcase/skin-contract`), and the gallery
(`showcase/gallery`, hosted by `showcase/desktop`) shows every slot in light
and dark.

## Platform code

`commonMain` holds the logic. Platform source sets exist where a platform API
is the subject: secure storage, the HTTP engines, connectivity, locale, the
Room database builder, the touch-target floor. Pure libraries keep their
`commonMain` free of JVM-only types and of dependencies without a `wasmJs`
variant, so a later web target is not precluded.
