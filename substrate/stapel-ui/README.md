# stapel-ui

The paradigm-neutral UI substrate of Stapel clients (Compose Multiplatform, no Material): the theme
built from `stapel-tokens`, layout verdicts (size classes from the token breakpoints, THE page gutter,
touch targets, sheet-vs-modal), the 20-slot `StapelSkin` seam with a Material-free `FoundationSkin`,
the `Stapel*` wrappers bricks call, and the composites that state UI rules once (`ErrorState`,
`EmptyState`, `LoadBoundary`, `LoadList`, `ConfirmDialog`, `StapelSnackbarHost`, `StapelPage`).

Coordinates: `io.github.usestapel:stapel-ui`. Brick UI libraries compile against this module only;
the app picks the skin (`stapel-ui-material3` for Material 3, or its own).

```kotlin
StapelTheme(skin = FoundationSkin, tokens = AppTokens) {
    StapelPage(measure = PaneMeasure.Narrow) {
        StapelTextField(email, label = stapelString(AuthStrings.email), kind = TextFieldKind.Email)
        StapelButton(stapelString(AuthStrings.signIn), onClick = onSubmit, loading = busy, fillWidth = true)
        LoadBoundary(sessions, onRetry = reload) { list -> /* … */ }
    }
}
```

Replace one component project-wide: `object AppSkin : StapelSkin by Material3Skin { @Composable override fun Button(…) }`.
