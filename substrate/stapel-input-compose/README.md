# stapel-input-compose

Binds `stapel-input` formats to Compose's state-based text field: an `InputMask` is a format as an
`InputTransformation` (every user edit goes through the format) plus an `OutputTransformation` (the
display), with the format's keyboard type. The `TextFieldState` holds the raw value.

Coordinates: `io.github.usestapel:stapel-input-compose` (group provisional until the first publish).

```kotlin
val mask = remember { PhoneFormat(appPhoneMetadata, defaultCallingCode = "1").asInputMask() }
val state = rememberMaskedTextFieldState(mask.format)
BasicTextField(
    state,
    inputTransformation = mask.inputTransformation,
    outputTransformation = mask.outputTransformation,
)
// state.text == "+15551234567" while the field shows "+1 555 123 4567"
```

Through a skin: pass `mask.inputTransformation` / `mask.outputTransformation` to the TextField or
CodeField spec's transformation parameters; the skin forwards them to its field.

Seams, invariants and what not to do: [MODULE.md](MODULE.md).
