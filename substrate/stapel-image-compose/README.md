# stapel-image-compose

The Coil 3 image loader behind `stapel-ui`'s image seam (`LocalStapelImages`). The app builds one
loader on its own Ktor `HttpClient` and provides it at its root; brick UI libraries keep calling
`StapelAsyncImage` / `StapelAvatar(image = …)` from `stapel-ui` and never see a Coil type.

Coordinates: `io.github.usestapel:stapel-image-compose` (all targets).

```kotlin
val images = stapelImageLoader(
    context = platformContext,                       // coil3.PlatformContext
    httpClient = appHttpClient,                      // the app's Ktor client (OkHttp / Darwin)
    auth = ImageAuth(session, hosts = setOf(environment.apiHost, environment.mediaHost)),
)
setContent {
    ProvideStapelImages(images) { StapelAppContent(app) }
}
// on logout / account wipe:
images.clearStapelCaches()
```

Requests for the `ImageAuth` hosts carry the session's bearer token (one refresh on a 401) and do not
follow redirects; every other host gets no `Authorization` header. Without this artifact every image
slot shows its fallback (initials, glyph, the error arm).
