# stapel-input — module map

The pure input engine. No Compose, no platform locale, no clock: everything a format needs is
passed in, so a domain presenter, a test or a future wasm target uses it as it is.

## Model

- **Raw value** — what is stored, validated and sent: digits for a card or a code, `+` and digits
  (E.164) for a phone, `-?digits(.digits)?` for a decimal (the `.` is the raw point in every
  locale), date digits in the locale's field order.
- **Display** — each raw character shown as exactly one character (itself, a shaped digit, the
  locale decimal separator) with literals inserted between (`Formatted`). So offsets map both ways
  and the display is expressible as edits on the raw text (`Formatted.pieces`), which is exactly
  what a Compose `OutputTransformation` applies.
- **Edit** — `InputFormat.edit(raw, start, end, inserted)`: a raw range replaced by typed, pasted or
  IME-committed text. Only `inserted` is parsed leniently (literals, separators, foreign digits,
  bidi marks); the kept raw text is trusted, so a raw value can never be re-read as a literal.

## Invariants (held by `FormatLaws` over every built-in format, fixed seed)

1. Total: no text, offset or configuration throws.
2. Canonical: `format(edit(...).raw).raw == edit(...).raw`.
3. Round trip: `normalize(format(raw).text) == raw`.
4. Cursor: `rawToDisplay` lands right after a raw character (or at 0), never after a literal;
   `displayToRaw(rawToDisplay(r)) == r`.
5. Raw digits are ASCII; any Unicode decimal digit is accepted; invisible characters are dropped.

Literals are lazy (shown before a slot once it is filled; trailing ones only on a full pattern),
so deleting the last character never strands a separator.

## Seams

- `InputFormat` — implement for a format of your own; build the display with `Formatted.Builder`.
  Run `FormatLaws`-style property tests over it.
- `PhoneMetadata` — region knowledge. **No country tables here**: `GenericPhoneMetadata` only knows
  E.164 limits and the ITU `00` prefix. A full provider (an app's, or an optional artifact generated
  from public numbering-plan metadata) supplies, per calling code: the code's length, national
  grouping by leading digits, possible lengths, the trunk prefix (`nationalToE164`) and the region's
  international prefixes.
- `InputLocale` — separators, group sizes, digit family, date order. The app builds it from its own
  locale data; this library never reads the platform locale (gate G-R1).
- `InputRules` — formats and validators from data (`FieldSpec` or JSON), so contract-declared
  formats and rules are not compiled into the client. A dynamic-forms artifact maps a form schema's
  field config onto this vocabulary. Unreadable parts are skipped and listed in `problems` — the
  server stays the judge.

## Validation

`Validator<T>` returns `ValidationResult` (`Valid` / `Invalid(issues)`); a `FieldIssue` is an i18n
key plus params, never display text. Built-ins accept the empty value (only `required` refuses it);
compose with `and` (all issues) and `then` (first failure). `InputBundle` is the `en` floor copy for
`InputIssueKeys`. Client rules mirror what the contract declares; they never replace the server's
refusal, which the UI shows on the field.

## Do not

- Put country-specific masks, phone tables or document formats in this artifact.
- Hard-code a code length: `CodeFormat(length)` takes it from configuration.
- Let a format throw on input it does not expect — return the nearest canonical value instead.
- Read `Locale`/`NSLocale` here; take an `InputLocale`.
- Compile server rules (patterns, ranges) into a brick: feed them through `InputRules`.

Known limits: a regular expression from data runs on each platform's own engine (keep to the common
subset; there is no backtracking budget); a pattern whose literals contain slot-acceptable characters
reads a paste that starts with those characters as the literal.
