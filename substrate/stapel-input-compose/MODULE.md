# stapel-input-compose — module map

- **Provides:** `InputMask` (`inputTransformation`, `outputTransformation`, `keyboardOptions`,
  `formatted(state)`, `setValue(state, text)`), `InputFormat.asInputMask()`,
  `rememberMaskedTextFieldState(format, initial)`, `InputKeyboard.toKeyboardOptions()`.
- **Depends on:** `stapel-input`, Compose foundation. Not on `stapel-ui`.

## Mechanism

Built on the current text-input standard of Compose foundation (commonMain on every target):
`TextFieldState` with `InputTransformation` / `OutputTransformation`. The value-based field's
`VisualTransformation` is not used.

- The state holds the **raw** value. `InputTransformation.transformInput` reads the buffer's
  original and new text, reduces the difference to one replaced range anchored at the new cursor
  (so typing `5` into `55` is placed where the user typed it), calls `InputFormat.edit`, and writes
  the result and cursor back only when they differ — an IME composition the format accepts as it
  is stays untouched.
- `OutputTransformation.transformOutput` applies `Formatted.pieces` (inserts and one-character
  substitutions) from the end. Inserted literals are the field's wedges: the field keeps the
  cursor out of them and maps selection and accessibility text for us.
- A value written to the state around the input transformation (`state.edit`,
  `setTextAndPlaceCursorAtEnd`) that is not canonical is shown unformatted rather than guessed at;
  write values with `InputMask.setValue`, which normalises.

## The skin seam (dependency direction)

`stapel-ui` must not depend on this artifact. Its `TextFieldSpec` / `CodeFieldSpec` carry the two
foundation types as optional parameters; a skin forwards them to its `BasicTextField`. A brick or
app builds the `InputMask` and puts its transformations into the spec. A skin never formats on its
own (the "never transforms the value" duty): the transformation is the caller's.

## Known behaviour

- Backspace with the cursor right after a literal (only reachable by tapping there): the field
  removes nothing for a range of inserted characters and moves the cursor before it; the next
  backspace removes a digit. A cursor placed by the field itself sits before literals, so typing
  and deleting from the end never meet this.
- IME composition: the transformation runs on every composition update; formats accept partial
  text, so the composing text is not rewritten unless it holds characters the format drops. The
  Compose UI test API cannot set a composing region, so this path is proven in the pure model
  (`MaskedText` replace sequences) and by commit-path UI tests, not by a composing UI test.

## Do not

- Depend on `stapel-ui` here, or make `stapel-ui` depend on this.
- Put formatting into a skin.
- Read `state.text` as display text: it is raw; use `formatted(state).text` for the display.
