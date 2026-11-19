package stapel.input

/**
 * An immutable editing model of one masked field in display coordinates — what a presenter, a
 * custom keypad or a test drives without Compose. Every operation is total and returns a new value.
 *
 * Deletion across literals: [backspace] and [deleteForward] with a collapsed cursor always remove a
 * raw character (skipping any literal next to the cursor); [replace] over a display range that holds
 * only literals changes nothing and snaps the cursor, which is what a Compose field with an output
 * transformation does for the same range.
 */
public class MaskedText private constructor(
    /** The format. */
    public val format: InputFormat,
    /** The current display and mapping. */
    public val formatted: Formatted,
    private val rawSelStart: Int,
    private val rawSelEnd: Int,
) {
    /** The raw value. */
    public val raw: String get() = formatted.raw

    /** The displayed text. */
    public val text: String get() = formatted.text

    /** Display offset of the selection start (equal to [selectionEnd] for a cursor). */
    public val selectionStart: Int get() = formatted.rawToDisplay(rawSelStart)

    /** Display offset of the selection end. */
    public val selectionEnd: Int get() = formatted.rawToDisplay(rawSelEnd)

    /** Replaces display range [displayStart]..[displayEnd] with [text] (typing, paste, IME commit). */
    public fun replace(displayStart: Int, displayEnd: Int, text: CharSequence): MaskedText {
        val a = formatted.displayToRaw(minOf(displayStart, displayEnd))
        val b = formatted.displayToRaw(maxOf(displayStart, displayEnd))
        return applyRaw(a, b, text)
    }

    /** Types (or pastes) [text] over the current selection. */
    public fun type(text: CharSequence): MaskedText = applyRaw(rawSelStart, rawSelEnd, text)

    /** Deletes the selection, or the raw character before the cursor (skipping literals). */
    public fun backspace(): MaskedText = when {
        rawSelStart != rawSelEnd -> applyRaw(rawSelStart, rawSelEnd, "")
        rawSelStart == 0 -> this
        else -> applyRaw(rawSelStart - 1, rawSelStart, "")
    }

    /** Deletes the selection, or the raw character after the cursor (skipping literals). */
    public fun deleteForward(): MaskedText = when {
        rawSelStart != rawSelEnd -> applyRaw(rawSelStart, rawSelEnd, "")
        rawSelStart >= raw.length -> this
        else -> applyRaw(rawSelStart, rawSelStart + 1, "")
    }

    /** Moves the selection to display range [displayStart]..[displayEnd], snapped off literal runs. */
    public fun select(displayStart: Int, displayEnd: Int = displayStart): MaskedText {
        val a = formatted.displayToRaw(minOf(displayStart, displayEnd))
        val b = formatted.displayToRaw(maxOf(displayStart, displayEnd))
        return MaskedText(format, formatted, a, b)
    }

    private fun applyRaw(start: Int, end: Int, inserted: CharSequence): MaskedText {
        val edit = format.edit(raw, start, end, inserted)
        val next = format.format(edit.raw)
        val cursor = edit.cursor.coerceIn(0, next.raw.length)
        return MaskedText(format, next, cursor, cursor)
    }

    /** For diagnostics: the display with the selection marked. */
    override fun toString(): String =
        if (selectionStart == selectionEnd) {
            text.substring(0, selectionStart) + "|" + text.substring(selectionStart)
        } else {
            text.substring(0, selectionStart) + "[" + text.substring(selectionStart, selectionEnd) + "]" + text.substring(selectionEnd)
        }

    /** Factories. */
    public companion object {
        /** A field showing [value] (any text, normalised) with the cursor at the end. */
        public fun of(format: InputFormat, value: CharSequence = ""): MaskedText {
            val f = format.format(format.normalize(value))
            return MaskedText(format, f, f.raw.length, f.raw.length)
        }
    }
}
