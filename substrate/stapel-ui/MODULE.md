# stapel-ui — seams and invariants

- **No Material here, ever** (R9, `checkDependencyDirection`): Material 3 lives in `stapel-ui-material3`.
  A brick UI library sees only this module, so it cannot bypass the skin.
- **Skin seam:** `StapelSkin` has twenty slots. The first twelve (Button … Snackbar, what the auth
  screens need) are abstract; the later eight (Toggle, Tabs, Chip, Avatar, Badge, NavigationBar,
  NavigationRail, PullToRefresh) default to `FoundationSkin`, so a new slot never breaks a skin.
  Slot duties are KDoc on each spec and are tested by the skin contract
  (`showcase/skin-contract`, `assertSkinContract(skin)`), which every skin must pass.
- **Wrappers carry defaults**, skin methods take none. Bricks call `StapelButton(…)`, never
  `StapelTheme.skin.Button(…)`.
- **Gutter:** `StapelPage` applies `StapelTheme.layout.pageGutter` (4 / 8 / 24 dp) once. A screen
  never adds its own horizontal root padding.
- **Size classes** come from the token breakpoints and the window width (`LocalWindowInfo`), not
  from Material's adaptive classes.
- **Input:** `Touch` on Android and iOS, `Pointer` on desktop (platform source sets exist only for
  this and the touch floor: 48 dp Android, 44 pt iOS).
- **Colours** only from `StapelTheme.colors`; no colour literal in a brick.
- **Strings:** `stapelString` / `stapelPlural` recompose on a locale switch; error text goes through
  `LocalStapelErrorText` (stapel-result's `ErrorTextResolver`).
- **Icons** are neutral ids (`StapelIconIds`); the default glyphs are hand-drawn path data in
  `Icons.kt` (the SVG → vector generator is not built yet). An unknown id draws a square.
- Not yet here: `PickerSheet`, reduce-motion local, `StapelSvg`/images (`stapel-image-compose`),
  RTL snapshot pass, Inter font artifact.
