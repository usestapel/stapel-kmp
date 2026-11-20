# stapel-profiles-e2e

The profiles brick against a real backend: `stand/stand.sh up` scaffolds a project with stapel-tools
0.84.0 (`--type minimal --modules auth profiles cdn --posture prototype`), pins stapel-profiles 0.23.1,
stapel-auth 0.53.1 and stapel-core 0.99.0 (the pinned contracts), migrates, fills the language
catalogue (`manage.py sync_languages`; a fresh deployment has none) and serves on 127.0.0.1:18741.
The suite wires the app the way a generated app does: `stapelApp { bricks(AuthBrick(), ProfilesBrick()) }`.

```sh
bricks/profiles/e2e/stand/stand.sh up
./gradlew :stapel-profiles-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18741/
bricks/profiles/e2e/stand/stand.sh down
```

| Scenario | Proves |
|---|---|
| P-1 | sign-in (mock OTP), the own profile, a rename + theme reach the server (raw GET), the PATCH answer is what observers see |
| P-2 | the language catalogue, the app language stored through the settings presenter, then handed back to the device |
| P-3 | two notification switches flipped at once both reach the server (red before the presenter queued its saves and kept pending switches over a revalidation) |
| P-4 | follow / lists / counters / block across two installations; oneself reads as Self; the anonymous read (see below) |
| P-5 | a phone number added, confirmed with the mock SMS code (a wrong code refused), restricted, deleted |
| P-6 | `auth.security` resolves as the first section of `profiles.settings` |
| A-1 | the avatar: a real multipart upload to stapel-cdn, then one PATCH storing `avatar` + `avatar_source=cdn` (raw GET) |

## Backend facts the contract does not state (stapel-profiles 0.23.1), pinned by the suite

- **Avatar tiers are integers on the wire.** `VariantMetaDTO.tier` is declared a string ("320",
  "original"); `/me` (GET and PATCH) and `/{user_id}` serve `"tier": 120`. The profile's
  `avatar_image` is built by `serializers.avatar_image()` straight from `build_image()`, bypassing
  stapel-core's `StapelImageSerializer` / `_TierField`. Consequence: once a person has an avatar, the
  client refuses their profile as a contract violation (it is not coerced). A-1 fails when fixed.
- **Anonymous public profile drops required fields.** `GET /{user_id}` without a session answers 200
  with `location_*`, `followers_count`, `following_count` and `relationship_status` omitted, all
  `required` in `ProfilePublicResponse`. A signed-out viewer cannot open a public profile. P-4 fails
  when fixed.
- The language catalogue is empty until `sync_languages` runs (a deployment step the scaffold does not take).
