# stapel-ui — seams and invariants

- **No Material here, ever** (R9, `checkDependencyDirection`): Material 3 lives in `stapel-ui-material3`.
  A brick UI library sees only this module, so it cannot bypass the skin.
- **Skin seam:** `StapelSkin` has twenty-five slots (`StapelSkinSlots`). The first twelve (Button … Snackbar, what the auth
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
- **Copy comes from the caller** for anything not in the `stapel.ui.*` floor bundle. The wrappers
  default the generic labels to the floor (`stapel.ui.close`, `cancel`, `confirm`, `clear`, `done`,
  `no_matches`, `refresh`, `remove`; `search` is there for callers): `StapelDialog`, `PickerSheet`,
  `StapelSelect` / `StapelMultiSelect`, `ConfirmDialog`, `StapelPullToRefresh`, a search field's clear
  action and an input chip's remove action. The floor's `Dismiss` is the fallback only where a slot
  must name a control.
- **Screen titles, once:** a screen names itself with `StapelScreenTitle(text)`, never a heading of its
  own. When a chrome with a title hosts it (`LocalStapelChromeTitle`: the app shell's top bar, a
  `StapelPage` drawing its bar from `title`) the chrome shows the text and the screen draws nothing;
  hosted bare, the screen draws it as its heading. A `StapelPage(title = …)` hosted under the shell's
  titled bar hands its title to that bar and draws no bar of its own (its actions stay as a row,
  `STAPEL_PAGE_ACTIONS_TAG`; back is the shell's). Dialogs and sheets title themselves through `title`.
- **Gutter:** `StapelPage` applies `StapelTheme.layout.pageGutter` (4 / 8 / 24 dp) once. A screen
  never adds its own horizontal root padding.
- **Size classes** come from the token breakpoints and the window width (`LocalWindowInfo`), not
  from Material's adaptive classes.
- **Responsive type and spacing (seam only):** `responsiveScales` reads `responsive.extra` roles
  `type.<step>`, `type.<step>.line` and `space.<n>` when the tokens carry them and picks the size class's
  value; absent roles keep the single scale. The token source (shared with the web) does not emit them
  yet — no Kotlin-only token is ever added here.
- **Input:** `Touch` on Android and iOS, `Pointer` on desktop (platform source sets exist only for
  this and the touch floor: 48 dp Android, 44 pt iOS).
- **Colours** only from `StapelTheme.colors`; no colour literal in a brick.
- **Strings:** `stapelString` / `stapelPlural` recompose on a locale switch; error text goes through
  `LocalStapelErrorText` (stapel-result's `ErrorTextResolver`).
- **Icons** are neutral ids (`StapelIconIds`); the default glyphs are vector drawables bundled as
  Compose Multiplatform resources (`src/commonMain/composeResources/drawable/stapel_icon_<id>.xml`,
  internal `Res`), the standard carrier for drawables and fonts; strings stay on our own engine
  (`stapel-i18n`). `StapelIconSet.resolve` is composable so a set can load from resources. Proven by a
  consumer build against the staged artifact (jvm and iOS simulator draw the glyph; the Android AAR
  carries `assets/composeResources/…`). An unknown id draws a square.
- **Select is a sheet, never a dropdown:** the `Select` slot draws the field; `PickerSheet` (single /
  multiple, search folding case and accents, a lazy list) is the picker; `StapelSelect` /
  `StapelMultiSelect` pair them.
- **`StapelPage` wires once** what every screen needs: insets (safe drawing, keyboard included — an
  `insets` parameter for tests and special hosts), the gutter, the top bar from `title` (with
  `largeTitle` collapsing on scroll via `TopBarSpec.collapsedFraction`), pull-to-refresh (`refresh`) and
  the snackbar host (`snackbars`) above the bottom bar.
- **Menu is for actions, never values:** the `Menu` slot is an action menu at its anchor (overflow,
  row actions; `StapelOverflowMenu` is the "more" button with its own state); picking a value stays
  `Select` + `PickerSheet`. A tap runs the item and closes the menu; the pane has a dismiss action.
- **Stepper** (quantity) clamps through `stepperValue` (no overflow at `Int.MAX_VALUE`), disables the
  button at each bound, and takes its button names from the caller. **ChipGroup** draws every chip
  through `StapelTheme.skin.Chip`, so a project chip applies inside groups too; wrap or scroll.
- **Right to left:** `assertSlotsMirrorInRtl` (skin contract) holds every skin to mirrored placement
  (top bar, row value, banner dismiss, chip remove, tabs, navigation, pager, stepper, field actions);
  a code field's digit cells stay left to right; an `autoMirror` icon is mirrored whatever its set's
  vector says (`stapelIconVector`). RTL baselines are story states with `rtl = true, locale = "ar"`.
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
- **Layout direction follows the strings locale:** `StapelTheme` provides `LocalLayoutDirection` from the
  active locale of its `StapelStrings` (`layoutDirectionOf` over `stapel-i18n`'s `writingDirectionOf`), so
  `strings.setLocale("ar")` mirrors the UI with no other wiring; the app starts its strings in the
  platform locale (`stapelApp` does, from `LocaleProvider`). A direction provided above the theme is
  ignored. `StapelTheme(layoutDirection = …)` (also on `StapelMaterialTheme`) is the explicit override;
  nested themes that pass none inherit it. Proven by `LayoutDirectionTest`; the skin contract's RTL pass
  reaches RTL through an Arabic strings locale.
- Not yet here: `StapelSvg`/images loader (`stapel-image-compose`; `LocalStapelImages` is the seam),
  Inter font artifact.
