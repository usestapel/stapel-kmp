package stapel.input

/**
 * Digits shown in groups (`1234 5678 9012 3456`, `12 34 56`). Raw = ASCII digits only.
 *
 * Digits beyond the listed [groupSizes] continue in groups of the last size; non-positive sizes are
 * ignored (an empty list shows the digits ungrouped). Typing past [maxDigits] is refused (the
 * inserted text is cut, the rest of the value is kept).
 */
public class GroupedDigitsFormat(
    groupSizes: List<Int>,
    /** Literal between groups. */
    public val separator: String = " ",
    maxDigits: Int = Int.MAX_VALUE,
    /** Raw length that counts as complete; null means [maxDigits]. */
    public val minDigits: Int? = null,
    /** Zero of the digit family to show ('0' keeps ASCII). */
    public val zeroDigit: Char = '0',
) : InputFormat {
    /** The group sizes in effect (positive only). */
    public val groupSizes: List<Int> = groupSizes.filter { it > 0 }

    /** Maximum raw length (at least 0). */
    public val maxDigits: Int = maxDigits.coerceAtLeast(0)

    override val keyboard: InputKeyboard get() = InputKeyboard.Number

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit =
        editDigits(raw, start, end, digitsOf(inserted), maxDigits)

    override fun format(raw: String): Formatted {
        val digits = digitsOf(raw).take(maxDigits)
        val b = Formatted.Builder()
        var group = 0
        var inGroup = 0
        for (d in digits) {
            val size = groupSizes.getOrNull(group) ?: groupSizes.lastOrNull()
            if (size != null && inGroup == size) {
                group++
                inGroup = 0
                b.literal(separator)
            }
            b.raw(d, InputChars.shapeDigit(d, zeroDigit))
            inGroup++
        }
        return b.build()
    }

    override fun isComplete(raw: String): Boolean {
        val n = digitsOf(raw).length
        return n > 0 && n >= (minDigits ?: maxDigits).coerceAtMost(maxDigits) && n == raw.length
    }

    /** Ready-made layouts. */
    public companion object {
        /** Payment-card style: groups of four, up to 19 digits (the ISO/IEC 7812 maximum), complete at 12. */
        public fun paymentCard(separator: String = " "): GroupedDigitsFormat =
            GroupedDigitsFormat(listOf(4), separator, maxDigits = 19, minDigits = 12)
    }
}

/**
 * A one-time code: exactly [length] digits, length taken from configuration (never a constant).
 *
 * A paste of a whole message (`"Your code: 123 456"`, `"code 123456. Do not share"`) keeps the first
 * run of digits that is exactly [length] long when one exists; otherwise all digits, cut to [length].
 */
public class CodeFormat(length: Int) : InputFormat {
    /** The code length (at least 1). */
    public val length: Int = length.coerceIn(1, MAX_CODE_LENGTH)

    override val keyboard: InputKeyboard get() = InputKeyboard.Number

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val folded = foldInput(inserted)
        val runs = Regex("[0-9]+(?:[ \\-][0-9]+)*").findAll(folded).map { m -> m.value.filter { it in '0'..'9' } }.toList()
        val exact = runs.firstOrNull { it.length == length }
        if (exact != null && folded.length > 1) return RawEdit(exact, exact.length)
        return editDigits(raw, start, end, folded.filter { it in '0'..'9' }, length)
    }

    override fun format(raw: String): Formatted = Formatted.identity(digitsOf(raw).take(length))

    override fun isComplete(raw: String): Boolean = raw.length == length && raw.all { it in '0'..'9' }

    private companion object {
        const val MAX_CODE_LENGTH = 64
    }
}

/** ASCII digits of [text] (any Unicode decimal digit folded). */
internal fun digitsOf(text: CharSequence): String {
    val out = StringBuilder(text.length)
    for (c in text) InputChars.asciiDigit(c)?.let { out.append(it) }
    return out.toString()
}

/** The shared digit-only edit: keep prefix and suffix, insert as many [insertedDigits] as fit. */
internal fun editDigits(raw: String, start: Int, end: Int, insertedDigits: String, max: Int): RawEdit {
    val clean = digitsOf(raw).take(max)
    val (s, e) = clampRange(start, end, clean.length)
    val prefix = clean.cut(0, s)
    val suffix = clean.cut(e, clean.length)
    val room = (max - prefix.length - suffix.length).coerceAtLeast(0)
    val ins = insertedDigits.take(room)
    return RawEdit(prefix + ins + suffix, prefix.length + ins.length)
}
