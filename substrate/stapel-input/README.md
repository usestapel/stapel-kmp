# stapel-input

Input masks and validation for Stapel clients, pure Kotlin (no Compose): a total transformation
between a field's raw value and its displayed text, with cursor mapping both ways, paste
normalisation, phone (E.164), dates, decimals and money, one-time codes, grouped digits and pattern
masks; a validator algebra whose messages are i18n keys; rule sets built from data. Presenters and
tests use it directly; `stapel-input-compose` binds it to text fields.

Coordinates: `io.github.usestapel:stapel-input` (group provisional until the first publish).

```kotlin
val phone = PhoneFormat(metadata = appPhoneMetadata, defaultCallingCode = "1")
MaskedText.of(phone).type("(555) 123-4567").raw          // "+15551234567"

val price = MoneyFormat(InputLocale(decimalSeparator = ',', groupSeparator = " "), minorUnits = 2, suffix = " €")
price.format(price.normalize("1234,5")).text              // "1 234,5 €"
price.toMinorUnits("1234.5")                               // 123450

val birthday = DateFormat(InputLocale(dateOrder = DateOrder.DayMonthYear, dateSeparator = '.'))
val check = Validators.required() then Validators.date(birthday, max = today)
check.validate("31022026").issues                          // [stapel.input.invalid_date]
```

Seams, invariants and what not to do: [MODULE.md](MODULE.md).
