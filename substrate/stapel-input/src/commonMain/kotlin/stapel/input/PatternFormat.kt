package stapel.input

/**
 * A fixed-shape mask such as `###-###-####`, `AA ## ****` or `#### ####[ ####]`.
 *
 * Syntax ([MaskPattern.parse], total — no pattern string throws):
 * - `#` a digit, `A` a letter, `*` a letter or digit (slots, each holds one raw character);
 * - `[` … `]` an optional part: its slots are not needed for [isComplete];
 * - `\` makes the next character a literal; every other character is a literal.
 *
 * Literals are shown lazily: those before a slot appear once that slot is filled; literals after the
 * last slot appear when every slot is filled. A paste may contain the literals (`"(555) 010-0199"`)
 * or not; characters that fit no slot are dropped.
 */
public class PatternFormat(
    /** The parsed pattern. */
    public val pattern: MaskPattern,
    /** Upper-case letters as they are entered. */
    public val upperCase: Boolean = false,
    /** Zero of the digit family to show ('0' keeps ASCII). */
    public val zeroDigit: Char = '0',
) : InputFormat {
    /** Parses [pattern] with [MaskPattern.parse]. */
    public constructor(pattern: String, upperCase: Boolean = false, zeroDigit: Char = '0') :
        this(MaskPattern.parse(pattern), upperCase, zeroDigit)

    override val keyboard: InputKeyboard
        get() = if (pattern.slots.isNotEmpty() && pattern.slots.all { it == SlotKind.Digit }) InputKeyboard.Number else InputKeyboard.Text

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val clean = refit(raw)
        val (s, e) = clampRange(start, end, clean.length)
        val prefix = clean.cut(0, s)
        val suffix = clean.cut(e, clean.length)
        val parsed = parseInserted(foldInput(inserted), s)
        val room = (pattern.slots.size - prefix.length - suffix.length).coerceAtLeast(0)
        val ins = parsed.take(room)
        val result = refit(prefix + ins + suffix)
        return RawEdit(result, prefix.length + ins.length)
    }

    override fun format(raw: String): Formatted {
        val clean = refit(raw)
        val b = Formatted.Builder()
        for ((k, c) in clean.withIndex()) {
            b.literal(pattern.literalsBefore[k])
            b.raw(c, InputChars.shapeDigit(c, zeroDigit))
        }
        if (clean.isNotEmpty() && clean.length == pattern.slots.size) b.literal(pattern.trailingLiterals)
        return b.build()
    }

    override fun isComplete(raw: String): Boolean = raw.length >= pattern.requiredSlots && raw.isNotEmpty() && refit(raw) == raw

    private fun fits(slot: Int, c: Char): Boolean = when (pattern.slots.getOrNull(slot)) {
        SlotKind.Digit -> c in '0'..'9'
        SlotKind.Letter -> c.isLetter()
        SlotKind.LetterOrDigit -> c in '0'..'9' || c.isLetter()
        null -> false
    }

    private fun shape(c: Char): Char = if (upperCase) c.uppercaseChar() else c

    /** Assigns characters to consecutive slots; a character that does not fit its slot is dropped. */
    private fun refit(text: CharSequence): String {
        val out = StringBuilder()
        for (raw in foldInput(text)) {
            if (out.length >= pattern.slots.size) break
            val c = shape(raw)
            if (fits(out.length, c)) out.append(c)
        }
        return out.toString()
    }

    /** Parses inserted text from slot [firstSlot] on, consuming literal runs the text repeats. */
    private fun parseInserted(text: String, firstSlot: Int): String {
        val out = StringBuilder()
        var slot = firstSlot
        var i = 0
        while (i < text.length && slot < pattern.slots.size) {
            i = matchLiteralRun(text, i, pattern.literalsBefore[slot])
            if (i >= text.length) break
            val c = shape(text[i])
            if (fits(slot, c)) {
                out.append(c)
                slot++
            }
            i++
        }
        return out.toString()
    }

    /** When [text] at [from] spells [run] (spaces ignored on both sides), the index after it; else [from]. */
    private fun matchLiteralRun(text: String, from: Int, run: String): Int {
        val wanted = run.filterNot { InputChars.isSpace(it) }
        if (wanted.isEmpty()) return from
        var j = from
        for (w in wanted) {
            while (j < text.length && InputChars.isSpace(text[j])) j++
            if (j >= text.length || text[j] != w) return from
            j++
        }
        return j
    }
}

/** What a mask slot accepts. */
public enum class SlotKind {
    /** An ASCII digit (after folding). */
    Digit,

    /** A letter of any script. */
    Letter,

    /** A digit or a letter. */
    LetterOrDigit,
}

/**
 * A parsed mask: slots, and the literals around them.
 *
 * @property slots the slot kinds in order.
 * @property literalsBefore for each slot, the literal text shown right before it.
 * @property trailingLiterals the literal text after the last slot.
 * @property requiredSlots how many leading slots a complete value fills (slots after the last
 *   required one are optional).
 */
public class MaskPattern private constructor(
    public val slots: List<SlotKind>,
    public val literalsBefore: List<String>,
    public val trailingLiterals: String,
    public val requiredSlots: Int,
) {
    /** Parsers. */
    public companion object {
        /** Parses [pattern] (syntax on [PatternFormat]). Never throws: an unclosed `[` ends at the end. */
        public fun parse(pattern: String): MaskPattern {
            val slots = ArrayList<SlotKind>()
            val before = ArrayList<String>()
            val pending = StringBuilder()
            var optional = false
            var lastRequired = 0
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                val kind = when (c) {
                    '#' -> SlotKind.Digit
                    'A' -> SlotKind.Letter
                    '*' -> SlotKind.LetterOrDigit
                    else -> null
                }
                when {
                    c == '\\' -> {
                        if (i + 1 < pattern.length) pending.append(pattern[i + 1])
                        i++
                    }
                    c == '[' -> optional = true
                    c == ']' -> optional = false
                    kind != null -> {
                        slots += kind
                        before += pending.toString()
                        pending.clear()
                        if (!optional) lastRequired = slots.size
                    }
                    else -> pending.append(c)
                }
                i++
            }
            return MaskPattern(slots, before, pending.toString(), lastRequired)
        }
    }
}
