# stapel-profiles-testing — module notes

## Seams

- `openSettingsSection(destinationId, expectTag)`, `renameInSettings(name)`, `chooseLanguage(code)`:
  each step waits with its own budget and fails with `ProfilesWalkException` naming the step.
- `ProfilesTags` = `stapel.profiles.ui.ProfilesTestTags`; `ProfilesSamples` (profile, catalogue, people,
  phone numbers) for previews and stories.

## Invariants

- The walkers never fake a backend answer: "stored" means the save button went idle (disabled for an
  unchanged name) after the server answered, or the walk fails on the error banner.

## What not to do

- Do not depend on this artifact from main source sets; it carries the Compose UI test API.
