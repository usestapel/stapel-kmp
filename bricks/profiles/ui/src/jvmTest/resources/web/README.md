Fixtures copied from `usestapel/stapel-react` `packages/profiles-react` at `6f7cd6c` (origin/main, 2026-11-20):

- `nav-manifest.json` — verbatim.
- `i18n-keys.json` — the values of `PROFILES_I18N_KEYS` in `src/i18n/keys.ts` (sorted).

Refresh both when the web pair moves (and regenerate `ProfilesWebStrings.kt` with
`scripts/gen_strings.py`); `WebParityTest` checks the mobile manifest and bundle against them.
