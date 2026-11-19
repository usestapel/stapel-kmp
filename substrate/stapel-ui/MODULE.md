# stapel-ui — seams and invariants

- **No Material here, ever** (R9, `checkDependencyDirection`): Material 3 lives in `stapel-ui-material3`.
  A brick UI library sees only this module, so it cannot bypass the skin.
- **Skin seam:** `StapelSkin` has twenty-two slots (`StapelSkinSlots`). The first twelve (Button … Snackbar, what the auth
  screens need) are abstract; the later ones (Toggle, Tabs, Chip, Avatar, Badge, NavigationBar,
  NavigationRail, PullToRefresh, and every slot added since) default to `FoundationSkin`, so a new
  slot never breaks a skin. Slot duties are KDoc on each spec and are tested by the skin contract
  (`showcase/skin-contract`: `assertSkinContract(skin)`, `assertTopBarSurvivesLargeText`,
  `assertSlotsSurviveLargeText`), which every skin must pass; each duty has an inversion test (a skin
  breaking it is caught) in `FoundationSkinTest`.
- **Variants are data:** one spec per slot; a variant is an enum or a field (`AvatarSize`,
  `BadgeEmphasis`, `ListItemMedia`, `ListItemAccessory`, `ChipKind.Input`), never another function. Specs
  carry data — strings, enums, `StapelIcon`, other specs — never a design-system handle (no Material
  type, no `Color`, no scroll behaviour). `ListItemSpec.leading`/`trailing` lambdas remain as an escape
  hatch; prefer `media` / `value` / `accessory`.
- **Rules shared by skins** live once in `skin/SlotSupport.kt` (badge cap and visibility, avatar name and
  initials, `stapelToggle` interaction, `refreshAction`, the list value-block width) and
  `ComponentColors` (badge / chip / avatar colours derived from the roles), so two skins cannot drift.
- **Images:** `stapel-ui` loads nothing. `LocalStapelImages` (`StapelImages`) turns a source string into
  a painter; while it answers null every slot draws its fallback (the avatar ladder: image → initials →
  glyph → person glyph).
- **Wrappers carry defaults**, skin methods take none. Every slot has a wrapper (`StapelToggle`,
  `StapelChip`, `StapelAvatar`, `StapelBadge`, `StapelPullToRefresh`, `StapelNavigationBar`,
  `StapelNavigationRail`, …). Bricks call `StapelButton(…)`, never `StapelTheme.skin.Button(…)`.
- **Copy comes from the caller** for anything not in the `stapel.ui.*` floor bundle (`removeLabel`,
  refresh labels); the floor's `Dismiss` is the fallback only where a slot must name a control.
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
- **Select is a sheet, never a dropdown:** the `Select` slot draws the field; `PickerSheet` (single /
  multiple, search folding case and accents, a lazy list) is the picker; `StapelSelect` /
  `StapelMultiSelect` pair them.
- **`StapelPage` wires once** what every screen needs: insets (safe drawing, keyboard included — an
  `insets` parameter for tests and special hosts), the gutter, the top bar from `title` (with
  `largeTitle` collapsing on scroll via `TopBarSpec.collapsedFraction`), pull-to-refresh (`refresh`) and
  the snackbar host (`snackbars`) above the bottom bar.
- **Composites** (no slot of their own, built from slots): `StatusPage` (three `StatusSize`s;
  `EmptyState` is its compact form), `SectionHeader` (five sizes, one `SectionHeaderAction`),
  `AdaptiveGrid` (`adaptiveColumns`, the `GridCells.Adaptive` rule), `PickerSheet`.
- **Reduce motion:** `LocalStapelReduceMotion` (from `StapelTheme(reduceMotion = …)`, the platform setting
  by default: Android animator scale 0, iOS Reduce Motion, desktop false). `shimmerBrush()` is a still fill
  under it; the contract proves both states by pixel capture.
- **Text transformations are the caller's:** `TextFieldSpec.inputTransformation` /
  `outputTransformation` and `CodeFieldSpec.inputTransformation` are forwarded, never invented by a skin;
  `TextFieldSpec.keyboardOptions()` lets a transformation's keyboard type win over the kind's. The code
  field's own filter keeps ASCII digits only and runs after the caller's transformation.
- Not yet here: `StapelSvg`/images loader (`stapel-image-compose`; `LocalStapelImages` is the seam),
  RTL snapshot pass, Inter font artifact, `Menu` and `Stepper` slots, `ChipGroup`, a lazy paged `LoadList`.
