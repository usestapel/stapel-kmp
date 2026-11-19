# stapel-ui-material3 — seams and invariants

- **Material lives only here** (R9, `gradle/material-allowed.txt`). Never make a brick depend on it.
- **Roles first:** `Material3Skin` colours every slot from `StapelTheme.colors`; `toColorScheme` is the
  fallback for Material components an app uses directly. `secondary`/`tertiary` collapse onto `brand`,
  inverse slots take the other mode's roles, tonal elevation is off (shadows come from the tokens).
- **Contrast:** every on-X / X pair the mapping creates, plus brand/error text on every surface,
  brand on brand-subtle, subtle text on sunken and status text on surface/overlay, is computed for the
  default theme and every preset in both modes (`MappingTest`, 81 pairs per mode).
- **Slots:** the twelve auth slots are Material (CodeField is the token-styled foundation field:
  Material 3 has no one-time-code component). Toggle, Tabs, Chip, Avatar, Badge, NavigationBar,
  NavigationRail and PullToRefresh are the `FoundationSkin` bodies (basic) until a consumer needs the
  Material version.
- **Dynamic colour** is never used; an app that insists passes its own `ColorScheme` inside
  `StapelMaterialTheme` and owns the consequences.
- Conformance: `assertSkinContract(Material3Skin)` (from `showcase/skin-contract`) runs in the tests.
