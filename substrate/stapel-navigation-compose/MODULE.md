# stapel-navigation-compose — module map

- **Provides:** `NavContribution` / `navContribution { destination<R>(…); embed(…) }` /
  `overriding`, `ScreenScope` (navigator, destination, entry, `PageSlot`), `EmbedScope`,
  `EmbedSlot` (chrome slots, for the shell), `LocalNavigator`, `rememberStapelNavigator`,
  `StapelNavHost`, `OverlayHost` + `DefaultOverlay`, `ResultEffect`, `NavBackInterceptor`.
- **Seams:** `OverlayHost` (the shell draws sheets with its skin), `unavailable` / `waiting` slots
  of the host, `NavContribution.overriding` (replace one screen of an installed library).
- **Invariants:** the engine is hidden (`EngineHiddenTest` reads the committed ABI dumps); every
  entry has its own saveable state and `ViewModelStoreOwner`, destinations sharing a `FlowId` share
  one owner; state lives while the entry is on *any* stack (tab switches keep it) and is released
  when it leaves the last one; the navigator's state is saved with the process (`NavStateCodec`);
  platform back (Android back, iOS edge swipe, desktop Esc — all via navigationevent) ends in
  `navigator.back()`; a sheet handles back itself.
- **Engine notes:** `NavDisplay` takes our own `StackEntry` as its key type (measured: the
  JetBrains `navigation3-ui` 1.2.0 `NavDisplay` is generic over `T : Any`, no `NavKey` needed);
  dialogs use the library's `DialogSceneStrategy`, sheets our own `OverlayScene`. Falling back to
  `navigation-compose` means rewriting `engine/` only.
- **Do not:** add Material here (the shell skins); depend on another brick from a contribution;
  collect `ExternalUris` elsewhere when `deepLinks` is given (one collector).
