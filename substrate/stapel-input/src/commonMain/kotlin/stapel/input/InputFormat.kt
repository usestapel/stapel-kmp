package stapel.input

/**
 * A total transformation between a raw value and what a field shows.
 *
 * Contract every implementation keeps (the property tests hold all built-in formats to it):
 * - **total** — no input to [edit], [format] or [isComplete] throws, whatever the text or offsets;
 * - **canonical** — `edit(...)` returns a raw value [format] reproduces unchanged
 *   (`format(r).raw == r`), and `normalize(format(r).text) == r` (round trip through the display);
 * - **digits are ASCII in raw** — any Unicode decimal digit typed or pasted is folded, invisible
 *   bidi/zero-width characters are dropped ([InputChars]).
 */
public interface InputFormat {
    /** The keyboard a field for this format should ask for. */
    public val keyboard: InputKeyboard

    /**
     * Applies a user edit on the raw value: the raw range [start]..[end] (clamped and ordered) is
     * replaced by [inserted] — typed characters, a paste (possibly formatted or dirty), or an IME
     * commit. [inserted] is parsed leniently (literals, separators and foreign digits are understood);
     * the rest of [raw] is kept. Returns the new raw value and the raw cursor after the edit.
     */
    public fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit

    /** The display for [raw]. A value this format would not produce is normalised first ([Formatted.raw]). */
    public fun format(raw: String): Formatted

    /** True when [raw] is a finished value of this format (all required positions filled). */
    public fun isComplete(raw: String): Boolean = raw.isNotEmpty()
}

/** Any text (a paste, a stored or server value, a display string) reduced to this format's raw value. */
public fun InputFormat.normalize(text: CharSequence): String = edit("", 0, 0, text).raw

/**
 * The result of [InputFormat.edit].
 *
 * @property raw the new raw value.
 * @property cursor the raw cursor offset after the edit (`0..raw.length`).
 */
public class RawEdit(public val raw: String, cursor: Int) {
    /** The raw cursor offset after the edit, clamped into `0..raw.length`. */
    public val cursor: Int = cursor.coerceIn(0, raw.length)

    /** Value equality. */
    override fun equals(other: Any?): Boolean = other is RawEdit && other.raw == raw && other.cursor == cursor

    /** Consistent with [equals]. */
    override fun hashCode(): Int = raw.hashCode() * 31 + cursor

    /** For diagnostics. */
    override fun toString(): String = "RawEdit(\"$raw\", $cursor)"
}

/** The keyboard a format asks for; the Compose binding maps it onto keyboard options. */
public enum class InputKeyboard {
    /** General text. */
    Text,

    /** Digits only. */
    Number,

    /** Digits with a decimal separator (and a sign where allowed). */
    Decimal,

    /** A phone dial pad. */
    Phone,
}

/** Text shown as it is: an optional length cap and character filter, invisible characters dropped. */
public class PlainFormat(
    /** Maximum raw length; null for none. */
    public val maxLength: Int? = null,
    /** The characters accepted (after digit folding); others are dropped. */
    private val accept: (Char) -> Boolean = { true },
) : InputFormat {
    override val keyboard: InputKeyboard get() = InputKeyboard.Text

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val clean = filter(raw)
        val (s, e) = clampRange(start, end, clean.length)
        val prefix = clean.cut(0, s)
        val suffix = clean.cut(e, clean.length)
        var ins = filter(inserted)
        val cap = maxLength?.coerceAtLeast(0)
        if (cap != null) ins = ins.take((cap - prefix.length - suffix.length).coerceAtLeast(0))
        val out = (prefix + ins + suffix).let { if (cap != null) it.take(cap) else it }
        return RawEdit(out, prefix.length + ins.length)
    }

    override fun format(raw: String): Formatted = Formatted.identity(filter(raw).let { s -> maxLength?.let { s.take(it.coerceAtLeast(0)) } ?: s })

    private fun filter(text: CharSequence): String = foldInput(text).filter(accept)
}
