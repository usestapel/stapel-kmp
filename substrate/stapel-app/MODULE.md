# stapel-app — module notes

## Seams

- `StapelBrick` (package `stapel.app.di`): the one descriptor a brick UI library exposes, named
  `<Brick>Brick` in its `di` package with a no-argument constructor (all parameters defaulted). It
  carries the brick's Koin modules, navigation, strings, error catalogs, required capabilities, the
  sign-in owner (`BrickSession`) and presenter creation. The scaffold and `kmp-registry.json` name the
  descriptor; the emitter's job is a list of them.
- `stapelApp { }`: `environment` (required), `logger`, `network`, `session`, `offline(storage module)`,
  `bricks`, `screens` (the app's own contributions), `modules` (capabilities, own clients),
  `overrides`, `capabilities`, `navigation`, `theme`, `strings`, `global`.
- `StapelAppContent(app)`: theme → strings/error texts → presenter factories (bricks first, then
  `KoinPresenterFactory`) → navigator → `StapelShell`. Log markers through the app's logger:
  `stapel.app.ready` on first composition, `stapel.nav <id>` on every top-destination change.
- `navManifestJson` and the jvm `stapel.app.di.export.main` are `@InternalStapelApi`: the build's
  `exportNavManifest` runs them; apps do not.

## Invariants

- The builder installs runtime, network, session, cache, the optional storage and
  `cacheSessionModule()` exactly once; bricks never bind the cache to the session. `stapelKoin`
  refuses any duplicate binding, so a brick that does fails at start.
- The app's strings follow `LocaleProvider`: an in-app locale (`setAppLocale`, e.g. the profiles brick's
  app language) switches the strings, and with them the layout direction, at once (`StapelAppTest`).
- At most one installed brick owns sign-in; without one the navigation is a signed-out constant.
- Material never appears here (R9): the skin arrives through `theme { }`.

## What not to do

- Do not start Koin elsewhere when using the builder; use `overrides(...)` for replacements.
- Do not read the graph while building a brick's navigation: lambdas read it when they compose
  (the manifest export builds navigation with an empty graph).
