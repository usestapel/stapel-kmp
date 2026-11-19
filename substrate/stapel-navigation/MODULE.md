# stapel-navigation — module map

- **Provides:** route/manifest types (`Route`, `DestinationSpec`, `EmbedSpec`, `NavManifest`,
  `toJson`), `resolveNav` → `ResolvedNav` (paths, container roots, dropped-with-reason, menus, tabs,
  synthetic stacks), `NavState` + `NavStateCodec`, `reduceNav` / `initialNavState`, `DefaultRouteGuard`,
  `DeepLinkResolver`, `ExternalUris`, `Navigator` / `StapelNavigator`, `ResultKey`.
- **Seams:** `NavAuthSource` (the app adapts its session; navigation knows no auth library),
  `RouteGuard` (replace the default matrix), `NavPolicy` (the app names its sign-in and home
  destinations), `NavOverrides` (project overrides by id), `Navigator` (tests record calls).
- **Invariants:** `reduceNav` is pure (uids come from `NavState.nextUid`, never reused after a reset);
  a guard never says "no" for "unresolved" (`Wait`); a refused target is kept in `pendingReturn` and
  replayed after sign-in with its arguments; an orphan is dropped with a reason, never silently;
  contract errors throw `NavContractException`; paths equal the web route plan (held by the shared
  fixtures under `src/commonTest/resources/route_plan`, produced by stapel-tools).
- **Children of an absolute-path top get no route.** A destination whose parent's path is absolute
  (`"/login"`) is dropped with the reason "parent '<id>' has an absolute path; children of an
  absolute-path entry get no route". This is deliberate parity with the web: the scaffold's route
  plan (stapel-tools `build_nav_route_plan`) mounts an absolute top as its own sibling route and
  never nests its children, so the web app has no URL for them either; the web skips them
  silently, here the drop is listed in `ResolvedNav.dropped`. Give such a screen its own absolute
  path, or hang it from a relative-path parent, to route it on both platforms.
- **Do not:** add Compose, lifecycle or Koin here (dependency-direction gate); depend on another brick
  — name foreign parents by id string; mutate `NavState` outside `reduceNav`; collect
  `ExternalUris.incoming` or a result flow from two places.
