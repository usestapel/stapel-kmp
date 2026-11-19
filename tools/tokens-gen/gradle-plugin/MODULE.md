# stapel-tokens-gradle-plugin — module notes

- Bundles `tools/tokens-gen` (unpublished) with its resources (default theme, standard ramps,
  presets, SOURCE.json) into its jar; KotlinPoet and kotlinx-serialization-json are its own runtime
  dependencies.
- The generated value is `internal` by default (one per app module); `public.set(true)` for a module
  that shares it.
- Not committed: the theme JSON is the source of truth. `checkStapelTokens` re-validates it on every
  `check` (contrast errors fail; documented exceptions warn) and proves the generated file is current.
- Brands (one value per extra theme file) are not in v0.
