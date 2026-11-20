# stapel-ui-material3 — seams and invariants

- **Material lives only here** (R9, `gradle/material-allowed.txt`). Never make a brick depend on it.
- **Roles first:** `Material3Skin` colours every slot from `StapelTheme.colors`; `toColorScheme` is the
  fallback for Material components an app uses directly. `secondary`/`tertiary` collapse onto `brand`,
  inverse slots take the other mode's roles, tonal elevation is off (shadows come from the tokens).
- **Contrast:** every on-X / X pair the mapping creates, plus brand/error text on every surface,
  brand on brand-subtle, subtle text on sunken and status text on surface/overlay, is computed for the
  default theme and every preset in both modes (`MappingTest`, 81 pairs per mode).
- **Slots:** every slot is a Material 3 component (`Switch` / `TriStateCheckbox` / `RadioButton` in a
  row that owns the interaction, `PrimaryTabRow` / `PrimaryScrollableTabRow` / segmented buttons,
  `FilterChip` / `InputChip`, `Badge`, `NavigationBarItem` / `NavigationRail`, `PullToRefreshBox`) except
  where Material 3 has none: CodeField, Avatar and ChipGroup are the token-styled foundation bodies
  (the group's chips are Material chips); Stepper is built from `OutlinedIconButton`s; Menu is `DropdownMenu`.
- **Large text:** Material's `NavigationBar` and `TopAppBar` containers have fixed heights; the skin uses
  their items inside a container that grows instead (`assertSlotsSurviveLargeText`,
  `assertTopBarSurvivesLargeText`).
- **Dynamic colour** is never used; an app that insists passes its own `ColorScheme` inside
  `StapelMaterialTheme` and owns the consequences.
- Conformance: `assertSkinContract(Material3Skin)` (from `showcase/skin-contract`) runs in the tests.
