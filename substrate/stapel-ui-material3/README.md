# stapel-ui-material3

The default skin of Stapel UI: the §68 colour roles mapped onto a Material 3 `ColorScheme`,
typography and shapes, and `Material3Skin`, which draws the `StapelSkin` slots with Material 3
components coloured from the roles. The only Stapel artifact that depends on Material; brick UI
libraries never see it — the app chooses it.

Coordinates: `io.github.usestapel:stapel-ui-material3`.

```kotlin
StapelMaterialTheme(tokens = AppTokens, mode = rememberThemeMode(prefs)) { App() }

// one component restyled project-wide
object AppSkin : StapelSkin by Material3Skin {
    @Composable override fun Button(spec: ButtonSpec, modifier: Modifier) { BrandButton(spec, modifier) }
}
StapelMaterialTheme(tokens = AppTokens, skin = AppSkin) { App() }
```
