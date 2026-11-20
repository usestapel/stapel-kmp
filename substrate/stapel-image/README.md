# stapel-image

The pure image model of Stapel clients (no Compose, no Coil): the backend media DTO (`StapelImage`,
`VariantMeta`), the variant-ladder tier math shared with the web client, and the request /
placeholder / error types a loader receives. Brick UI libraries draw images through `stapel-ui`
(`StapelAsyncImage`, `StapelAvatar(image = …)`), which compiles against this module only; the Coil 3
loader lives in `stapel-image-compose`, wired by the app.

Coordinates: `io.github.usestapel:stapel-image` (all targets).

```kotlin
val request = profile.photo.requestFor(slotWidthDp = 48f, slotHeightDp = 48f, density = 3f, fit = Fit.Cover)
// → the smallest ladder variant that does not upscale 144 px, the inline preview as the placeholder
val variant = chooseVariant(400f, 225f, 2f, imgAspect = 0.75, fit = Fit.Cover, image = photo) // "1080" w-branch
decodePreview("data:image/png;base64,…")   // only base64 image data URIs, anything else is null
```
