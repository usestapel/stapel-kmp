package stapel.input

/**
 * A raw value and the text shown for it, with the mapping between the two.
 *
 * The model every format follows: each raw character is shown as exactly one display character
 * (the same one, or a substitute such as a locale decimal separator or a shaped digit), and literals
 * (separators, brackets, affixes) are inserted between them. So `raw` is recoverable from the display,
 * offsets map both ways, and an output transformation can be expressed as [pieces] on the raw text.
 *
 * Cursor convention: a raw offset maps to the display position right after the previous raw
 * character, i.e. before any literals that follow it ([rawToDisplay]). A cursor therefore never sits
 * inside a run of literals — the same side a Compose output transformation picks for a collapsed
 * selection at an inserted "wedge".
 *
 * Build one with [Formatted.Builder].
 */
public class Formatted private constructor(
    /** The raw value (what is stored, validated and sent). */
    public val raw: String,
    /** The displayed text. */
    public val text: String,
    private val displayOfRaw: IntArray,
    private val rawBefore: IntArray,
    /** The display as edits on [raw], in raw order: what an output transformation applies. */
    public val pieces: List<FormatPiece>,
) {
    /** The number of raw characters before display offset [displayOffset] (clamped into the text). */
    public fun displayToRaw(displayOffset: Int): Int = rawBefore[displayOffset.coerceIn(0, text.length)]

    /**
     * The display offset for raw offset [rawOffset] (clamped into the raw value): right after raw
     * character `rawOffset - 1`, or 0 for the start.
     */
    public fun rawToDisplay(rawOffset: Int): Int {
        val r = rawOffset.coerceIn(0, raw.length)
        return if (r == 0) 0 else displayOfRaw[r - 1] + 1
    }

    /** True when display character [displayIndex] is a literal (not a raw character); false out of range. */
    public fun isLiteralAt(displayIndex: Int): Boolean {
        if (displayIndex !in text.indices) return false
        return rawBefore[displayIndex + 1] == rawBefore[displayIndex]
    }

    /** The nearest allowed cursor position for [displayOffset]: never inside a run of literals. */
    public fun snapCursor(displayOffset: Int): Int = rawToDisplay(displayToRaw(displayOffset))

    /** Equality on raw value and display text. */
    override fun equals(other: Any?): Boolean =
        other is Formatted && other.raw == raw && other.text == text && other.pieces == pieces

    /** Consistent with [equals]. */
    override fun hashCode(): Int = raw.hashCode() * 31 + text.hashCode()

    /** For diagnostics: `raw → text`. */
    override fun toString(): String = "Formatted(\"$raw\" → \"$text\")"

    /** Accumulates raw characters and literals in display order. Never throws. */
    public class Builder {
        private val rawText = StringBuilder()
        private val display = StringBuilder()
        private val displayOfRaw = ArrayList<Int>()
        private val pieces = ArrayList<FormatPiece>()
        private var pendingLiteral: StringBuilder? = null

        /** The number of raw characters appended so far. */
        public val rawLength: Int get() = rawText.length

        /** Appends a literal (a separator, bracket, affix): shown, not part of the raw value. */
        public fun literal(text: String): Builder {
            if (text.isEmpty()) return this
            display.append(text)
            (pendingLiteral ?: StringBuilder().also { pendingLiteral = it }).append(text)
            return this
        }

        /** Appends raw character [c], shown as [shownAs]. */
        public fun raw(c: Char, shownAs: Char = c): Builder {
            flushLiteral()
            if (shownAs != c) pieces += FormatPiece.Substitute(rawText.length, shownAs)
            displayOfRaw += display.length
            rawText.append(c)
            display.append(shownAs)
            return this
        }

        private fun flushLiteral() {
            val lit = pendingLiteral ?: return
            pieces += FormatPiece.Insert(rawText.length, lit.toString())
            pendingLiteral = null
        }

        /** The finished value. */
        public fun build(): Formatted {
            flushLiteral()
            val text = display.toString()
            val rawBefore = IntArray(text.length + 1)
            var r = 0
            var next = 0
            for (d in 0..text.length) {
                rawBefore[d] = r
                if (next < displayOfRaw.size && displayOfRaw[next] == d) {
                    r++
                    next++
                }
            }
            return Formatted(rawText.toString(), text, displayOfRaw.toIntArray(), rawBefore, pieces.toList())
        }
    }

    /** Factories. */
    public companion object {
        /** [raw] shown as it is. */
        public fun identity(raw: String): Formatted {
            val b = Builder()
            for (c in raw) b.raw(c)
            return b.build()
        }
    }
}

/** One difference between a raw value and its display, positioned on the raw value. */
public sealed interface FormatPiece {
    /** [text] is shown before raw character [rawOffset] (or at the end when it equals the raw length). */
    public class Insert(
        /** Raw offset the literal precedes. */
        public val rawOffset: Int,
        /** The literal. */
        public val text: String,
    ) : FormatPiece {
        /** Value equality. */
        override fun equals(other: Any?): Boolean = other is Insert && other.rawOffset == rawOffset && other.text == text

        /** Consistent with [equals]. */
        override fun hashCode(): Int = rawOffset * 31 + text.hashCode()

        /** For diagnostics. */
        override fun toString(): String = "Insert($rawOffset, \"$text\")"
    }

    /** Raw character [rawIndex] is shown as [shownAs]. */
    public class Substitute(
        /** Index of the raw character. */
        public val rawIndex: Int,
        /** What is shown instead. */
        public val shownAs: Char,
    ) : FormatPiece {
        /** Value equality. */
        override fun equals(other: Any?): Boolean = other is Substitute && other.rawIndex == rawIndex && other.shownAs == shownAs

        /** Consistent with [equals]. */
        override fun hashCode(): Int = rawIndex * 31 + shownAs.code

        /** For diagnostics. */
        override fun toString(): String = "Substitute($rawIndex, '$shownAs')"
    }
}
