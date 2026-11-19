# stapel-navigation — module map

- **Provides:** route/manifest types (`Route`, `DestinationSpec`, `EmbedSpec`, `NavManifest`,
  `toJson`), `resolveNav` → `ResolvedNav` (paths, container roots, dropped-with-reason, menus, tabs,
  synthetic stacks), `NavState` + `NavStateCodec`, `reduceNav` / `initialNavState`, `DefaultRouteGuard`,
  `DeepLinkResolver`, `ExternalUris`, `Navigator` / `StapelNavigator`, `ResultKey`,
  `NavCommand.FinishFlow` / `Navigator.finishFlow(WithResult)`, `DestinationSpec.aliases`,
  `MOBILE_ONLY_ENTRY_FIELDS`.
- **Seams:** `NavAuthSource` (the app adapts its session; navigation knows no auth library),
  `RouteGuard` (replace the default matrix), `NavPolicy` (the app names its sign-in and home
  destinations), `NavOverrides` (project overrides by id), `Navigator` (tests record calls).
- **Invariants:** `reduceNav` is pure (uids come from `NavState.nextUid`, never reused after a reset);
  a guard never says "no" for "unresolved" (`Wait`); a refused target is kept in `pendingReturn` and
  replayed after sign-in with its arguments; an orphan is dropped with a reason, never silently;
  contract errors throw `NavContractException`; paths equal the web route plan (held by the shared
  fixtures under `src/commonTest/resources/route_plan`, produced by stapel-tools).
- **Flows.** `FinishFlow` removes every entry of a `FlowId` from every stack (plus what was opened
  above its lowest visible entry), keeps `pendingReturn` (completion, unlike `Back` out of the last
  sign-in step, which abandons it), and refills an emptied shown stack with its default route — the
  tab root, else home / the first other tab when the tab root belongs to the flow, else the start
  state. The sign-in destination's flow counts as sign-in: every step of it leaves on sign-in.
  Results go through `finishFlowWithResult`, delivered only when the flow was on a stack.
- **Aliases.** Extra absolute patterns per destination, matched by `DeepLinkResolver` after more
  literal patterns and after a canonical path of equal weight, opened through the same guard.
  `addressOf` / `uriFor` / the manifest's `route.path` stay canonical; `aliases` is a mobile-only
  manifest field. Two aliases, or an alias and a routed path, matching the same links are a
  `NavContractException` naming both owners (artifacts).
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
