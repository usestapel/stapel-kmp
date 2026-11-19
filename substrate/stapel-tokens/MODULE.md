# stapel-tokens — seams and invariants

- **Source of truth** is `tokens-source/` (pinned copies of `@stapel/tokens`' `theme.default.json`,
  `ramps.standard.json`, the scaffold presets). `src/commonMain/generated` is written by
  `./gradlew :stapel-tokens:stapelGenerate` and drift-gated by `stapelGenerateCheck`; never edit it.
- **Resolver parity:** the Kotlin resolver in `tools/tokens-gen` must reproduce the Node resolver's
  `tokens.resolved.json` byte for byte; generation refuses otherwise.
- **Tokens are a value**, not constants: an app theme, a brand and a test theme are all `StapelTokens`.
  Add a role only to the §68 source first; a Kotlin-only token is a twin the canon forbids.
- **Units:** every length is the source's px number; `stapel-ui` reads px as dp and font px as sp.
- **Gutter canon:** `responsive.pageGutter` is 4 (phone) / 8 (tablet) / 24 (desktop) by default and is
  the only horizontal page inset (`StapelDefaultTokensTest`).
- No Compose, no platform code: `commonMain` only, wasm-ready.
