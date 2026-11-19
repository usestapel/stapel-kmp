# stapel-shell — module map

- **Provides:** `StapelShell` (from a `ShellConfig`, or around an existing navigator),
  `ShellTemplate`, `Slots`, `SlotPlan` (+ `offered`), `ShellConfig`, `BadgeSource`, `SectionScreen`,
  `UnavailableScreen`, `ShellTags`, `shellTemplatesJson()` (committed as `shell-templates.json`).
- **Seams:** the skin (`StapelSkin.NavigationBar` / `NavigationRail`, wrappers for list rows,
  surfaces, banners, progress); `SlotPlan` (the scaffold's slot decision); `BadgeSource`;
  `ShellConfig.label` (i18n).
- **Invariants:** chrome by `StapelTheme.layout.sizeClass` (token breakpoints, window width) — never
  Material's adaptive classes; at most `policy.maxTabs` bar items, the last one "More" on overflow;
  an embed renders only when the plan lists it, its slot is rendered at this width, its `widths`
  allow it and its surface matches the auth state; chrome hides while screens sit above the tab
  host; a refusal (`nav.no_mandate`) and a missing capability are named, never a blank screen;
  `shell-templates.json` equals `shellTemplatesJson()` (drift test).
- **Not here yet:** screenshot baselines of the three chromes (needs the UI brigade's screenshot
  harness); list-detail panes from `PaneRole` (single pane in v0); safe-area/IME insets beyond what
  `StapelPage` applies inside screens.
- **Do not:** add Material (dependency gate); add horizontal padding around page content
  (`StapelPage` owns the gutter).
