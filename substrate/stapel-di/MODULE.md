# stapel-di — module map

Rank 0 substrate. Depends on `koin-core` and coroutines only.

## The convention (R6) every library follows

1. Constructors are plain: collaborators are constructor parameters of interface type. No
   `KoinComponent`, `inject()` or `GlobalContext` in library code.
2. One public factory per library, in `<module package>.di`, named `<module>Module(...)`, returning a
   Koin `Module`. Options are a plain data class parameter.
3. Nothing is registered implicitly: no initializer, provider or service loader.
4. `org.koin` appears only in the `di` package (`checkKoinLeak`). In this module the whole package
   `stapel.di` is that package.
5. Libraries never start Koin; the app calls `stapelKoin(...)` once.
6. Bind interfaces, not implementations: `single<StapelHttp> { … }`.
7. Multi-bindings (`ErrorCatalog`, `NetworkPlugin`, `LogoutHook`) use `contribute<T>(key)` and are
   read with `getAll<T>()`.

## Seams

| Seam | Who |
|---|---|
| `stapelKoin` | the app's composition root (scaffold output), graph tests (`global = false`) |
| `StapelQualifiers.AppScope` | bound by `runtimeModule`; long-lived objects take it |
| `sessionScoped` / `bindSessionScope` / `SessionScopeHandle` | `stapel-session`'s module binds the handle from its generation stream; per-user objects are declared `sessionScoped` |
| `sessionScopeId(generation)` | scope id format `stapel.session.<generation>` (generation = `Long` from `SessionState`) |

## Invariants

- A Koin `Module` value keeps its `single` instances inside its definitions, so the same value
  loaded into two applications shares them (`aSharedModuleValueSharesItsSingletons`). Factories are
  therefore functions called once per application, never `val`s.
- A duplicate binding among `modules` is a start-time failure, never a silent last-wins.
- A new session generation closes the previous session scope (running `onClose`) before the new
  one opens; `null` closes it.

## Not here

- `KoinPresenterFactory` (asked by NAV): it needs `stapel-mvi`'s `PresenterFactory`, which ranks above
  this module; it belongs in the `di` package of the module that owns `PresenterFactory`.
