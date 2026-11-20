# stapel-profiles-e2e

The profiles brick against a real backend: `stand/stand.sh up` scaffolds a project with stapel-tools
0.86.0 (`--type minimal --modules auth profiles cdn --posture prototype`), pins stapel-profiles 0.23.3,
stapel-auth 0.54.1, stapel-cdn 0.31.3 and stapel-core 0.99.5 (the pinned contracts), migrates, fills
the language catalogue (`manage.py sync_languages`; a fresh deployment has none) and serves on
127.0.0.1:18761. The suite wires the app the way a generated app does:
`stapelApp { bricks(AuthBrick(), ProfilesBrick()); modules(cdnModule()) }`.

```sh
bricks/profiles/e2e/stand/stand.sh up
./gradlew :stapel-profiles-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18761/
bricks/profiles/e2e/stand/stand.sh down
```

| Scenario | Proves |
|---|---|
| P-1 | sign-in (mock OTP), the own profile, a rename + theme reach the server (raw GET), the PATCH answer is what observers see |
| P-2 | the language catalogue, the app language stored through the settings presenter, then handed back to the device |
| P-3 | two notification switches flipped at once both reach the server (red before the presenter queued its saves and kept pending switches over a revalidation) |
| P-4 | follow / lists / counters / block across two installations; oneself reads as Self; a signed-out viewer reads the profile with location and relationship withheld |
| P-5 | a phone number added, confirmed with the mock SMS code (a wrong code refused), restricted, deleted |
| P-7 | choosing Arabic switches the app's strings locale and layout direction at once; a restart over the same stored session comes back in Arabic; Russian changes the app's own texts; "follow the device" hands the locale back |
| P-6 | `auth.security` resolves as the first section of `profiles.settings` |
| L-1 | a language chosen signed out (`LocalePreference`) applies at once, a relaunch starts in it before any sign-in, and a new account's profile (no language) is seeded with it (`app_language`, `use_device_language=false`, raw GET); it stays after sign-out (red without the seed) |
| L-2 | an account whose profile has a language: after sign-in the profile's language is shown and the server keeps it; the device's choice is untouched and returns after sign-out |
| A-1 | the avatar: a real multipart upload through the generated stapel-cdn client with upload progress, then one PATCH storing `avatar` + `avatar_source=cdn` (raw GET); the answer decodes with its variant ladder |

## Backend facts the contract does not state (stapel-profiles 0.23.3)

- The language catalogue is empty until `sync_languages` runs (a deployment step the scaffold does not take).
- Fixed in 0.23.3 and proven here: avatar tiers are strings on the wire (A-1 decodes the avatar); a
  signed-out viewer gets the declared shape with withheld fields null and the counters present (P-4
  reads it as `Disclosed.Withheld` / `RelationshipStatus.Withheld`, a member's unset location as
  `Disclosed.NotSet`).
