# stapel-profiles — module map

- **Generated (do not edit):** `src/commonMain/generated/` — packages `stapel.profiles.api`
  (`ProfilesApi`, `ProfilesApiClient`, `ProfilesKeys`, `ProfilesInvalidation`, `ProfilesContract`),
  `stapel.profiles.api.model`, `stapel.profiles.api.error`; `api-names.lock.json` and
  `codegen-report.json`. Inputs: `contracts/profiles/` (pin to stapel-profiles 0.23.1, contract
  profile 2, cache plan seeded from the web pair's hooks, ratchet; no overlay). Regenerate with
  `./gradlew :stapel-profiles:stapelGenerate`; `stapelGenerateCheck` fails on any drift.
- **Seams:** the wire only through `ProfilesApi`; the avatar file through `AvatarUploader` (the
  profiles backend stores a reference, a media service produces it); the viewer's account id as a
  plain `() -> String?`.

## Hand-written (`src/commonMain/kotlin`)

| Package | What |
|---|---|
| `stapel.profiles` | `StapelProfiles` façade (`me`, `languages`, `people`, `connections`, `contacts`, `canUploadAvatar`) |
| `stapel.profiles.model` | domain types (`MyProfile`, `PublicProfile`, `Person`, `Language`, `NotificationPreferences`, `ThemePreference`, `RelationshipStatus`, `Contact`, `ContactPolicy`, `AvatarRef`, `ProfileUpdate`); DTOs never reach screens; avatars are `stapel.image.StapelImage` |
| `stapel.profiles.error` | `ProfilesError` (a `StapelError`) with the typed `ProfilesErrorReason` |
| `stapel.profiles.repo` | `MyProfileRepository` (`/me`, SWR, the PATCH answer written to the cache, `setAvatar` = upload + one PATCH), `LanguagesRepository` (app-scoped, cache-first), `PeopleRepository` (public profiles, `/batch` in chunks of 100 in request order, follow/unfollow/block/unblock), `ConnectionsRepository` (followers / following through the batch, blocked), `ContactsRepository` (memory only, add / update / delete / SMS code) |
| `stapel.profiles.avatar` | `AvatarUploader` (seam; `None` = no upload), `CdnAvatarUploader` (stapel-cdn's multipart upload, hand-written until a stapel-cdn client exists) |
| `stapel.profiles.presentation` | `ProfileSettingsPresenter` (settings, language and notifications screens), `LanguageCataloguePresenter`, `PublicProfilePresenter`, `ConnectionsPresenter`, `ContactsPresenter` |
| `stapel.profiles.di` | `profilesModule(avatarUpload)` (the only Koin import) |
| `stapel.profiles.internal` | DTO ↔ domain mapping, read specs from the cache plan |

## Invariants (each has a test that fails when broken)

- An avatar reference never travels without its source: `setAvatar` uploads, then stores
  `avatar` + `avatar_source` in ONE `PATCH`; a failed upload stores nothing.
- A `PATCH /me` sends only the fields it sets (notification switches as all four), and its answer is
  written to the cache: observers see it with no re-read.
- Saves run one at a time in intent order; two notification switches flipped before the first answer
  both reach the server, even when a revalidation lands in between; a refused change goes back to
  what the server holds.
- Phone numbers are memory-only (never on disk); the own profile is durable and revalidated; the
  language catalogue is app-wide and cache-first; relationships are network-first.
- A relationship action invalidates the profile, the three lists and the own counters.
- The viewer's own profile reads as `Self` and offers no action; an anonymous viewer gets none.
- The people batch keeps the requested order, chunks at 100 and marks people without a profile.
- The avatar upload goes multipart (`file`) to module `cdn` with the session's bearer
  (`GraphTest`, real pipeline over a fake wire).

## Not here (v0) — stated, not hidden

- The first-run prompt (`initial_setup_passed`), the field manifest screen, location, "languages I
  understand", seller pages and the phone reveal button for viewers: the operations are generated,
  no presenter yet.
- A stapel-cdn client: `CdnAvatarUploader` reads only `image.prefix` of the upload answer.

## Backend facts the contract does not state

- stapel-profiles 0.23.1 serves the avatar's variant ladder with integer `tier`s while its schema
  says string; a profile with an avatar does not decode until that is fixed upstream (the client is
  not loosened; see `bricks/profiles/e2e/README.md`).
- A signed-out viewer's `GET /{user_id}` omits fields the schema marks required (location, counters,
  `relationship_status`): the public profile does not decode without a session.
- The language catalogue of a fresh deployment is empty until `manage.py sync_languages` runs.
