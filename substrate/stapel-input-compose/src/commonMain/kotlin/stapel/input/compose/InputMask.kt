package stapel.input.compose

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import stapel.input.FormatPiece
import stapel.input.Formatted
import stapel.input.InputFormat
import stapel.input.InputKeyboard
import stapel.input.normalize

/**
 * An [InputFormat] as the two transformations of a state-based text field — the seam a skin's
 * TextField / CodeField slot takes without depending on this artifact:
 *
 * ```
 * BasicTextField(state, inputTransformation = mask.inputTransformation, outputTransformation = mask.outputTransformation)
 * ```
 *
 * The [TextFieldState] holds the **raw** value (`state.text` is what is validated and sent);
 * [inputTransformation] turns every user edit (typing, paste, IME commit, deletion) into
 * [InputFormat.edit] on it, [outputTransformation] shows [InputFormat.format]'s display. Literals
 * become the field's inserted "wedges": the cursor never stops inside them.
 *
 * Deleting with the cursor right after a literal: the field's own mapping removes nothing for a
 * range of inserted characters and moves the cursor before them; the next deletion removes the raw
 * character. The pure model (`MaskedText.backspace`) removes the raw character at once.
 */
@Stable
public class InputMask(
    /** The format. */
    public val format: InputFormat,
) {
    /** Applies user edits through [InputFormat.edit]; also carries the format's keyboard type. */
    public val inputTransformation: InputTransformation = MaskInputTransformation(format)

    /** Shows [InputFormat.format]'s display over the raw value. */
    public val outputTransformation: OutputTransformation = MaskOutputTransformation(format)

    /** Keyboard options for the format's [InputKeyboard] (also offered by [inputTransformation]). */
    public val keyboardOptions: KeyboardOptions get() = format.keyboard.toKeyboardOptions()

    /** The display of the value [state] holds. */
    public fun formatted(state: TextFieldState): Formatted = format.format(state.text.toString())

    /** Replaces [state]'s value with [value] (any text, normalised), cursor at the end. */
    public fun setValue(state: TextFieldState, value: CharSequence) {
        val raw = format.normalize(value)
        state.edit {
            replace(0, length, raw)
            selection = TextRange(raw.length)
        }
    }
}

/** This format as an [InputMask]. */
public fun InputFormat.asInputMask(): InputMask = InputMask(this)

/** A remembered, saveable [TextFieldState] holding [initial] normalised by [format]. */
@Composable
public fun rememberMaskedTextFieldState(format: InputFormat, initial: CharSequence = ""): TextFieldState {
    val raw = remember(format) { format.normalize(initial) }
    return rememberTextFieldState(initialText = raw)
}

/** Keyboard options for an [InputKeyboard]. */
public fun InputKeyboard.toKeyboardOptions(): KeyboardOptions = when (this) {
    InputKeyboard.Text -> KeyboardOptions.Default
    InputKeyboard.Number -> KeyboardOptions(keyboardType = KeyboardType.Number)
    InputKeyboard.Decimal -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
    InputKeyboard.Phone -> KeyboardOptions(keyboardType = KeyboardType.Phone)
}

private class MaskInputTransformation(private val format: InputFormat) : InputTransformation {
    override val keyboardOptions: KeyboardOptions?
        get() = format.keyboard.takeIf { it != InputKeyboard.Text }?.toKeyboardOptions()

    override fun TextFieldBuffer.transformInput() {
        val before = originalText.toString()
        val after = asCharSequence().toString()
        if (before == after) return
        val change = singleChange(before, after, selection.end)
        val edit = format.edit(before, change.start, change.end, after.substring(change.insertedStart, change.insertedEnd))
        if (edit.raw != after) replace(0, length, edit.raw)
        val cursor = TextRange(edit.cursor)
        if (selection != cursor) selection = cursor
    }
}

private class MaskOutputTransformation(private val format: InputFormat) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val raw = asCharSequence().toString()
        val formatted = format.format(raw)
        // A value set around the input transformation that is not canonical is shown as it is.
        if (formatted.raw != raw) return
        for (piece in formatted.pieces.asReversed()) {
            when (piece) {
                is FormatPiece.Insert -> insert(piece.rawOffset, piece.text)
                is FormatPiece.Substitute -> replace(piece.rawIndex, piece.rawIndex + 1, piece.shownAs.toString())
            }
        }
    }
}

/** A replaced range of the old text and the range of the new text that replaced it. */
internal class Change(val start: Int, val end: Int, val insertedStart: Int, val insertedEnd: Int)

/**
 * The single replaced range that turns [before] into [after], placed so the inserted text ends at
 * [cursor] (where the field put the cursor after the edit) — this resolves repeated characters
 * (typing `5` into `55`) the way the user made the edit.
 */
internal fun singleChange(before: String, after: String, cursor: Int): Change {
    val c = cursor.coerceIn(0, after.length)
    val maxCommon = minOf(before.length, after.length)
    var suffix = 0
    val suffixLimit = minOf(maxCommon, after.length - c)
    while (suffix < suffixLimit && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
    var prefix = 0
    val prefixLimit = minOf(maxCommon - suffix, c)
    while (prefix < prefixLimit && before[prefix] == after[prefix]) prefix++
    return Change(prefix, before.length - suffix, prefix, after.length - suffix)
}
