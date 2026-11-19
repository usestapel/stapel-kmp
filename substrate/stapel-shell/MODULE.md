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
- **Insets:** `StapelShell(windowInsets = ShellDefaults.windowInsets)` (system bars + display
  cutout; the keyboard stays with the screens). Each chrome keeps the sides it touches clear and
  consumes them for the content, so `StapelPage`'s own safe-area padding never doubles: top bar —
  top and both sides; bottom bar — bottom and both sides; rail / drawer — start and vertical (the
  drawer's surface runs to the edge, its items keep clear; the rail is padded whole), the top bar
  beside it then only top and end. A side no chrome touches (the bottom beside a rail or in the
  `Stack` template) stays with the screen. UI tests inject distinct insets per side.
- **Panes:** the shell passes its width class to `StapelNavHost`; list-detail is two-pane from
  `Tablet` up with `ShellTags.DetailPlaceholder` (label key `shell.detail.empty`) in an empty
  detail pane.
- **Not here yet:** screenshot baselines of the three chromes (needs the UI brigade's screenshot
  harness); the skin's bar / rail colour does not extend under the system bars (the padded area
  shows the shell surface); proof that the iOS edge-swipe gesture itself reaches the back channel —
  the UI tests inject back through the same navigationevent dispatcher, the gesture recognizer is
  left to the sample app's XCUITest.
- **Do not:** add Material (dependency gate); add horizontal padding around page content
  (`StapelPage` owns the gutter).
