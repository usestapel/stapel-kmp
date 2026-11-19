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
- **Do not:** add Compose, lifecycle or Koin here (dependency-direction gate); depend on another brick
  — name foreign parents by id string; mutate `NavState` outside `reduceNav`; collect
  `ExternalUris.incoming` or a result flow from two places.
