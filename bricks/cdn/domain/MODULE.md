# stapel-cdn — module map

- **Generated (do not edit):** `src/commonMain/generated/` — packages `stapel.cdn.api` (`CdnApi`,
  `CdnApiClient`, `CdnKeys`, `CdnInvalidation`, `CdnContract`), `stapel.cdn.api.model`,
  `stapel.cdn.api.error`; `api-names.lock.json` and `codegen-report.json`. Inputs: `contracts/cdn/`
  (pin to stapel-cdn 0.31.3, contract profile 2, ratchet; no overlay, no cache plan: uploads are
  mutations, nothing is cached). Regenerate with `./gradlew :stapel-cdn:stapelGenerate`;
  `stapelGenerateCheck` fails on any drift.
- **Seams:** provides `stapel.wire.MediaUploader` (declared in the substrate so that no brick depends
  on this one); the wire only through `CdnApi`.

## Hand-written (`src/commonMain/kotlin`)

| Package | What |
|---|---|
| `stapel.cdn.media` | `CdnMediaUploader(api, imageType)`: `MediaPurpose.Avatar` → `upload_avatar`, `MediaPurpose.Image` → `upload_image` (binary file part, asset type `imageType` or the deployment's default); the stored reference is the answer's `image.prefix` (`<type>/<sha256>`) with source `cdn`, progress passed to the transport |
| `stapel.cdn.di` | `cdnModule(imageType)` (the only Koin import): `CdnApi` on the authenticated transport, `MediaUploader` |

## Invariants (each has a test that fails when broken)

- The avatar upload is an authenticated multipart `POST` to module `cdn` (`GeneratedContractTest`);
  the caller's progress listener and file reach the call unchanged (`CdnMediaUploaderTest`); an image
  travels as a file part with the configured asset type, none when unset.
- A refusal comes back as the typed stapel-cdn error; a purpose without an endpoint is refused
  before anything is sent.
- Every named example of the pinned contract round-trips through the generated models.

## Not here (v0) — stated, not hidden

- Video, audio, file and typed-image (`images/{image_type}/upload/`) uploads: generated, not offered
  through `MediaUploader` (no purpose names them yet); an app calls `CdnApi` directly.
- No UI library and no `StapelBrick`: the app installs `cdnModule()` itself.
