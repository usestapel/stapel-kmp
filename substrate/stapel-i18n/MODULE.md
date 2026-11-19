# stapel-i18n — seams and invariants

- **Precedence:** within one locale, bundles in registration order (floor first, bricks next), then the
  `LocaleLoader`'s remote copy, then the app's `override` — the last layer wins. Across locales the
  chain is tag (`ru-RU`) → language (`ru`) → `defaultLocale`; a more specific locale always beats a
  higher layer of a less specific one.
- **Plural lookup:** category key → `key.other` → flat `key` → the key itself. Rules are CLDR cardinal
  rules for integers; en/ru/es are built in, `PluralRules.register` adds more.
- **Missing text never throws:** a missing key renders as the key.
- **Not thread-safe for writes:** call `setLocale` from one place (the UI thread / a single coroutine).
- `I18nBundle.audit` is the completeness gate a UI library runs in its tests (keys, params, plural
  categories per language).
- Error codes belong to `stapel-result`'s `ErrorTextResolver`; do not duplicate them here.
- Bundles are hand-written Kotlin until the bundle generator (`src/i18n/*.json` → Kotlin) exists in
  `tools/codegen`.
