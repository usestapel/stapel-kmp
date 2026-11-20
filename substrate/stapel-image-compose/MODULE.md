# stapel-image-compose — seams and invariants

- **Provides:** `stapelImageLoader(context, httpClient, auth, configure)`, `ImageAuth(authenticator, hosts)`,
  `CoilStapelImages(loader)` (a `stapel.ui.StapelImages`), `ProvideStapelImages(loader) { … }`,
  `ImageLoader.clearStapelCaches()`, `ImageLoader.clearCachesOnSessionEnd(session)` (memory + disk caches
  cleared from the session's logout hook on every session end; `SessionEndClearsImagesTest`).
- **One HTTP stack:** fetches go through the app's Ktor `HttpClient` (`coil-network-ktor3`); Coil's
  service-loaded components are off, so Coil never builds a default client of its own.
- **Bearer only at home (each a wire test, `src/wireTest`, jvm/OkHttp and iOS simulator/Darwin against a
  loopback server reached as `localhost` = the app's host and `127.0.0.1` = a foreign one):** a request
  for an `ImageAuth` host gets `Authorization: Bearer <session token>`; any other host gets none, also when
  the caller put one on the request (stripped); without `ImageAuth` no request carries a token; the
  authenticated requests go through a copy of the client with redirects off, so a redirect never takes
  the token elsewhere (the image fails instead); a 401 refreshes once through the session and retries.
  The harness inversion (both hosts authorized) shows the header is visible when sent.
- **The seam:** `CoilStapelImages.load` maps Coil's painter state onto `StapelImageState` (Ready /
  Loading / Failed with `ImageError.Http(status)`, `Network`, `Decode`); the request carries the slot's
  pixel size so Coil loads without being drawn first. `CoilStapelImagesTest` (desktop + iOS simulator,
  Coil's fake engine): an avatar draws the loaded image and keeps its initials on failure; with no
  loader wired the avatar falls back and `StapelAsyncImage` shows its error arm.
- **Do not:** set Coil's singleton loader from a library; hand the session's authenticated pipeline
  client to Coil (`ImageAuth` decides per host); add a host to `ImageAuth` that the app does not own.
- **Not covered:** Android device behaviour (no emulator; the OkHttp path is the JVM's), SVG decoding
  (`coil-svg` is not wired; ARCH-UI §7's `StapelSvg` is not built), disk-cache encryption (Coil's disk
  cache is plain files in the app's cache directory — clear it on logout).
