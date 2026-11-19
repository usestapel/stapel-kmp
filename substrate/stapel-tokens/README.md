# stapel-tokens

The Stapel design tokens — the §68 neutral colour-role dictionary (33 roles, light and dark), type
scale, spacing, radii, control heights, breakpoints, the responsive page gutter and elevation — as a
pure-Kotlin value (`StapelTokens`). No Compose dependency; `stapel-ui` turns it into a theme.

Coordinates: `io.github.usestapel:stapel-tokens` (all targets).

```kotlin
val tokens: StapelTokens = StapelDefaultTokens            // the library default
val ocean = StapelPresetTokens.ocean                      // a scaffold preset
tokens.light.brand.toHex()                                // "#4657d9"
tokens.colors(ThemeMode.Dark)[ColorRole.TextMuted]
tokens.responsive.pageGutter.phone                        // 4f — THE horizontal page inset on a phone
tokens.light.status("error")?.bg
```

An app theme is the same type, generated from the app's `stapel.theme.json` by `tools/tokens-gen`
(`stapel-tokens-kt app --theme stapel.theme.json --package app.theme --name AppTokens --out …`).
