# stapel-image — seams and invariants

- **Provides:** `StapelImage` / `VariantMeta` / `Branch` / `PreviewKind` (the wire DTO, snake_case on the
  wire, unknown keys ignored by the shared `Json`), `Fit`, `pickTier`, `limitingAxis`, `chooseVariant`,
  `ImageRequest`, `ImagePlaceholder` (`None`, `Preview`), `ImageError` (`Http`, `Network`, `Decode`,
  `Refused`, `NoLoader`), `decodePreview`, `StapelImage.requestFor`, `StapelImage.placeholder`, `upgradeOnly`.
- **Seam:** this is what brick UI compiles against. A loader (Coil in `stapel-image-compose`, a test
  fake, an app's own) receives `ImageRequest`s through `stapel-ui`'s `LocalStapelImages`; no loader
  type ever reaches a brick.
- **Invariants (each a test):** the tier math picks what the web picks for the web's own vectors
  (`TiersTest`); only a `data:image/<type>;base64,` URI becomes a placeholder, never fetched; a medium
  that is not a picture loads its poster, never itself, and nothing without one; a zero-sided slot
  loads nothing; the on-screen variant is only ever upgraded (`upgradeOnly`).
- **Pure:** rank 0 in the substrate table, no project dependencies; `commonMain` uses only
  kotlinx.serialization and the standard library (wasm-ready).
- **Not here:** SVG rendering (`StapelSvg` of ARCH-UI §7 is not built), pixel decoding (the loader's).
