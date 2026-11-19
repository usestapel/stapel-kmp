# stapel-mvi-compose — module map

- **Provides:** `rememberPresenter` (reified and `KClass` forms), `PresenterFactory` +
  `LocalPresenterFactory`, `collectState`, `CollectEffects`, and `di.KoinPresenterFactory`.
- **Seams:** `PresenterFactory` (Koin through `KoinPresenterFactory`, or a hand-written one);
  the ambient `LocalViewModelStoreOwner` decides the presenter's lifetime (`stapel-navigation-compose`
  provides an entry- or flow-scoped owner; pass `owner =` for an app-wide presenter).
- **Invariants:** one presenter per (owner, type, key, args); clearing the owner cancels
  `PresenterContext.scope`; effects are collected only while the lifecycle is at least STARTED and
  are buffered (not lost, not replayed) meanwhile; without a saved-state registry the saver falls
  back to `StateSaver.None` instead of failing.
- **Measured (2026-11-19, CMP 1.12.2 / lifecycle 2.11.0):** an owner from `ViewModelStoreProvider`
  inside `SaveableStateHolder.SaveableStateProvider` supplies a working multiplatform
  `SavedStateHandle` on jvm and the iOS simulator: values written through `StateSaver` come back after
  the saveable registry is saved and restored with every `remember`ed object (ViewModel stores
  included) recreated. `StateRestorationTester.emulateSaveAndRestore` throws `NotImplementedError` on
  skiko targets, so the tests drive a `SaveableStateRegistry` directly. The registry's content is not
  encoded to bytes on skiko in this test (in-memory hand-over).
- **Do not:** construct presenters with `remember {}` (lost on configuration change); collect
  `effects` outside `CollectEffects`; import Koin outside the `di` package.
