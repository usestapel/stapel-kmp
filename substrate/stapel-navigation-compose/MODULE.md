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
  dialogs use the library's `DialogSceneStrategy`, sheets our own `OverlayScene`, list-detail our
  own `Scene` (stable `Scene`/`SceneStrategy` API of `navigation3-ui` 1.2.0, no opt-in; the
  Material `adaptive-navigation3` list-detail strategy is not used — this module is Material-free). Falling back to
  `navigation-compose` means rewriting `engine/` only.
- **List-detail:** `StapelNavHost(width = …)`; from `WidthClass.Tablet` up a `PaneRole.List` entry is
  drawn beside the `PaneRole.Detail` entry directly above it (or beside `detailPlaceholder`). The
  stack is identical in both layouts, so back, deep links and entry state agree and survive a width
  change (UI tests on jvm and the iOS simulator). The list-detail scene exists at every width and
  its key ignores the width: crossing the breakpoint re-lays the same scene out instead of switching
  scenes (measured: switching scenes on a width change while the stack stays put left the display
  blank after a later back, intermittently).
- **Flows:** `Navigator.finishFlow(WithResult)` drops every entry of the flow; the shared owner is
  cleared once, when the last entry of the flow leaves every stack (UI test counts the clears).
- **Do not:** add Material here (the shell skins); depend on another brick from a contribution;
  collect `ExternalUris` elsewhere when `deepLinks` is given (one collector).
