# stapel-cdn

Media domain library: the generated client of the `stapel-cdn` backend (`CdnApi`, models, typed
errors) and `CdnMediaUploader`, stapel-cdn as the app's `stapel.wire.MediaUploader` — the seam other
bricks store file references through (the profiles brick's avatar). No Compose, no platform SDK.

Coordinates: `io.github.usestapel:stapel-cdn` (provisional group).

```kotlin
stapelApp(platform) {
    environment(AppEnvironment)
    bricks(AuthBrick(), ProfilesBrick())
    modules(cdnModule())          // CdnApi + MediaUploader: the profiles avatar upload goes to stapel-cdn
}

val stored = koin.get<MediaUploader>().upload(MediaPurpose.Avatar, file) { sent, total -> /* progress */ }
```

End-to-end proof against a real backend: `bricks/profiles/e2e` (A-1: the avatar upload with progress).
