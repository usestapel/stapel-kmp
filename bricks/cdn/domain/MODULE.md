# stapel-cdn — module map

- **Generated (do not edit):** `src/commonMain/generated/` — packages `stapel.cdn.api` (`CdnApi`,
  `CdnApiClient`, `CdnKeys`, `CdnInvalidation`, `CdnContract`), `stapel.cdn.api.model`,
  `stapel.cdn.api.error`; `api-names.lock.json` and `codegen-report.json`. Inputs: `contracts/cdn/`
  (pin to stapel-cdn 0.31.2, contract profile 2, ratchet; no overlay, no cache plan: uploads are
  mutations, nothing is cached). Regenerate with `./gradlew :stapel-cdn:stapelGenerate`;
  `stapelGenerateCheck` fails on any drift.
- **Seams:** provides `stapel.wire.MediaUploader` (declared in the substrate so that no brick depends
  on this one); the wire only through `CdnApi`.

## Hand-written (`src/commonMain/kotlin`)

| Package | What |
|---|---|
| `stapel.cdn.media` | `CdnMediaUploader`: `MediaPurpose.Avatar` → `upload_avatar`, the stored reference is the answer's `image.prefix` with source `cdn`, progress passed to the transport |
| `stapel.cdn.di` | `cdnModule()` (the only Koin import): `CdnApi` on the authenticated transport, `MediaUploader` |

## Invariants (each has a test that fails when broken)

- The avatar upload is an authenticated multipart `POST` to module `cdn` (`GeneratedContractTest`);
  the caller's progress listener and file reach the call unchanged (`CdnMediaUploaderTest`).
- A refusal comes back as the typed stapel-cdn error; a purpose without an endpoint is refused
  before anything is sent.
- Every named example of the pinned contract round-trips through the generated models.

## Not here (v0) — stated, not hidden

- Image, video, audio and file uploads other than the avatar: generated, not offered through
  `MediaUploader` yet. `upload_image` cannot be offered as generated: stapel-cdn 0.31.2 declares its
  `file` as `format: uri` (a string), not `binary` — a contract defect, reported upstream.
- No UI library and no `StapelBrick`: the app installs `cdnModule()` itself.

## Backend facts the contract does not state

- `Image.prefix` is documented as `<type>/<hash>` and served so; the contract's examples show the
  bare type (`"avatar"`).
