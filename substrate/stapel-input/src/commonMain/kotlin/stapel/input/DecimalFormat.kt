package stapel.input

import stapel.wire.Decimal

/**
 * A decimal number: raw is the canonical text `-?(0|[1-9][0-9]*)(\.[0-9]*)?` (or `""`, or `"-"` while
 * typing a negative number) — the `.` is always the raw decimal point, whatever the locale shows.
 *
 * Display: the locale's group separator every [InputLocale.primaryGroupSize] /
 * [InputLocale.secondaryGroupSize] integer digits, the locale's decimal separator, optional affixes
 * (`$`, ` €`) shown when there is a value.
 *
 * Input: digits of any script; the locale's decimal separator, `٫`, and `.` (unless `.` is the locale's
 * group separator) mark the decimal point; group separators, spaces, affixes and other characters are
 * dropped. A paste holding both `.` and `,` takes the last one as the decimal point
 * (`"1.234,56"` and `"1,234.56"` both read as 1234.56). A minus sign anywhere makes the value negative
 * when [allowNegative]. Leading zeros are dropped; fraction digits past [maxFractionDigits] and
 * integer digits past [maxIntegerDigits] are dropped.
 */
public class DecimalFormat(
    /** Separators and digit shapes. */
    public val locale: InputLocale = InputLocale.Neutral,
    maxFractionDigits: Int = DEFAULT_MAX_DIGITS,
    maxIntegerDigits: Int = DEFAULT_MAX_DIGITS,
    /** Accept a minus sign. */
    public val allowNegative: Boolean = false,
    /** Literal shown before the number (after the sign), e.g. a currency symbol. */
    public val prefix: String = "",
    /** Literal shown after the number, e.g. a unit or a currency symbol. */
    public val suffix: String = "",
) : InputFormat {
    /** Maximum digits after the decimal point (0: integers only). */
    public val maxFractionDigits: Int = maxFractionDigits.coerceIn(0, DEFAULT_MAX_DIGITS)

    /** Maximum digits before the decimal point (at least 1). */
    public val maxIntegerDigits: Int = maxIntegerDigits.coerceIn(1, DEFAULT_MAX_DIGITS)

    private val decimalSeparator: Char = locale.decimalSeparator.let {
        if (it.isDigit() || InputChars.isMinus(it) || InputChars.isSpace(it) || it.isLetter()) '.' else it
    }

    override val keyboard: InputKeyboard get() = if (maxFractionDigits > 0) InputKeyboard.Decimal else InputKeyboard.Number

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val clean = canonical(raw, raw.length).raw
        val (s, e) = clampRange(start, end, clean.length)
        val prefixText = clean.cut(0, s)
        val ins = mapInserted(foldInput(inserted))
        return canonical(prefixText + ins + clean.cut(e, clean.length), prefixText.length + ins.length)
    }

    override fun format(raw: String): Formatted {
        val value = canonical(raw, raw.length).raw
        val b = Formatted.Builder()
        if (value.isEmpty()) return b.build()
        var at = 0
        if (value[0] == '-') {
            b.raw('-')
            at = 1
        }
        if (at == value.length) return b.build()
        b.literal(prefix)
        val dot = value.indexOf('.')
        val intEnd = if (dot < 0) value.length else dot
        val n = intEnd - at
        val primary = locale.primaryGroupSize.coerceAtLeast(1)
        val secondary = locale.secondaryGroupSize.coerceAtLeast(1)
        for (i in at until intEnd) {
            val fromRight = n - (i - at)
            val boundary = i > at && (fromRight == primary || (fromRight > primary && (fromRight - primary) % secondary == 0))
            if (boundary) b.literal(locale.groupSeparator)
            b.raw(value[i], InputChars.shapeDigit(value[i], locale.zeroDigit))
        }
        if (dot >= 0) {
            b.raw('.', decimalSeparator)
            for (i in dot + 1 until value.length) b.raw(value[i], InputChars.shapeDigit(value[i], locale.zeroDigit))
        }
        b.literal(suffix)
        return b.build()
    }

    override fun isComplete(raw: String): Boolean = toDecimal(raw) != null && canonical(raw, raw.length).raw == raw

    /** The number [raw] spells (a trailing `.` ignored), or null for `""` and `"-"`. */
    public fun toDecimal(raw: String): Decimal? {
        val value = canonical(raw, raw.length).raw.removeSuffix(".")
        if (value.isEmpty() || value == "-") return null
        return Decimal(value)
    }

    /** The raw value for [value] (fraction digits past [maxFractionDigits] dropped, not rounded). */
    public fun fromDecimal(value: Decimal): String = canonical(value.text, value.text.length).raw

    /** Inserted text with the decimal point as `.`, signs as `-`, everything else but digits dropped. */
    private fun mapInserted(text: String): String {
        var t = text
        if (prefix.isNotEmpty()) t = t.replace(prefix, "")
        if (suffix.isNotEmpty()) t = t.replace(suffix, "")
        val marks = t.filter { isDecimalMark(it) || it == ',' || it == '.' }.toSet()
        val lastMark: Int = if (marks.size > 1) t.indexOfLast { it == '.' || it == ',' || isDecimalMark(it) } else -1
        val out = StringBuilder(t.length)
        for ((i, c) in t.withIndex()) {
            when {
                c in '0'..'9' -> out.append(c)
                InputChars.isMinus(c) -> if (allowNegative) out.append('-')
                lastMark >= 0 -> if (i == lastMark) out.append('.')
                isDecimalMark(c) -> out.append('.')
            }
        }
        // Integers only: a decimal point in pasted text ends the number ("12.7" reads as 12).
        return if (maxFractionDigits == 0) out.toString().substringBefore('.') else out.toString()
    }

    private fun isDecimalMark(c: Char): Boolean =
        c == decimalSeparator || c == '٫' || (c == '.' && locale.groupSeparator != ".")

    /** The canonical raw text of [text] (raw grammar: digits, `.`, `-`) and where [cursor] lands in it. */
    private fun canonical(text: String, cursor: Int): RawEdit {
        val negative = allowNegative && text.any { it == '-' }
        val intPart = StringBuilder()
        val frac = StringBuilder()
        var seenDot = false
        var cur = -1
        val cursorAt = cursor.coerceIn(0, text.length)
        for ((i, c) in text.withIndex()) {
            if (i == cursorAt) cur = intPart.length + (if (seenDot) 1 + frac.length else 0)
            when {
                c in '0'..'9' && !seenDot -> if (intPart.length < maxIntegerDigits) intPart.append(c)
                c in '0'..'9' -> if (frac.length < maxFractionDigits) frac.append(c)
                c == '.' && !seenDot && maxFractionDigits > 0 -> seenDot = true
            }
        }
        if (cur < 0) cur = intPart.length + (if (seenDot) 1 + frac.length else 0)
        var zeros = 0
        while (zeros < intPart.length - 1 && intPart[zeros] == '0') zeros++
        if (zeros > 0) {
            intPart.deleteRange(0, zeros)
            cur = (cur - zeros).coerceAtLeast(0)
        }
        if (intPart.isEmpty() && seenDot) {
            intPart.append('0')
            cur += 1
        }
        val body = intPart.toString() + (if (seenDot) ".$frac" else "")
        if (body.isEmpty() && !negative) return RawEdit("", 0)
        return if (negative) RawEdit("-$body", cur + 1) else RawEdit(body, cur)
    }

    private companion object {
        const val DEFAULT_MAX_DIGITS = 18
    }
}

/**
 * A money amount: a [DecimalFormat] whose fraction digits are the currency's minor units, which the
 * app passes in (from its currency data or the server's currency list — never a table here).
 */
public class MoneyFormat(
    /** Separators and digit shapes. */
    public val locale: InputLocale = InputLocale.Neutral,
    minorUnits: Int,
    /** Currency symbol or code shown before the amount (after the sign). */
    public val prefix: String = "",
    /** Currency symbol or code shown after the amount. */
    public val suffix: String = "",
    /** Accept negative amounts. */
    public val allowNegative: Boolean = false,
    /** Maximum digits before the decimal point. */
    public val maxIntegerDigits: Int = MAX_INTEGER_DIGITS,
) : InputFormat {
    /** Digits of the minor unit (2 for cents; 0 for currencies without one), clamped into 0..6. */
    public val minorUnits: Int = minorUnits.coerceIn(0, MAX_MINOR_UNITS)

    /** The decimal format underneath. */
    public val decimal: DecimalFormat = DecimalFormat(locale, this.minorUnits, maxIntegerDigits, allowNegative, prefix, suffix)

    override val keyboard: InputKeyboard get() = decimal.keyboard

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit = decimal.edit(raw, start, end, inserted)

    override fun format(raw: String): Formatted = decimal.format(raw)

    override fun isComplete(raw: String): Boolean = decimal.isComplete(raw)

    /** The amount as a [Decimal], or null for an empty value. */
    public fun toDecimal(raw: String): Decimal? = decimal.toDecimal(raw)

    /** The raw value for [amount]. */
    public fun fromDecimal(amount: Decimal): String = decimal.fromDecimal(amount)

    /** The amount in minor units (`"12.5"` → 1250 for two minor units), or null when empty or too large. */
    public fun toMinorUnits(raw: String): Long? {
        val text = toDecimal(raw)?.text ?: return null
        val negative = text.startsWith('-')
        val body = text.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "").padEnd(minorUnits, '0').take(minorUnits)
        val digits = (intPart + frac).trimStart('0').ifEmpty { "0" }
        if (digits.length > MAX_LONG_DIGITS) return null
        val v = digits.toLong()
        return if (negative) -v else v
    }

    /** The raw value for [minor] minor units (1250 → `"12.50"` for two minor units). */
    public fun fromMinorUnits(minor: Long): String {
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE.toString() else kotlin.math.abs(minor).toString()
        val padded = abs.padStart(minorUnits + 1, '0')
        val intPart = padded.dropLast(minorUnits)
        val frac = padded.takeLast(minorUnits)
        val text = (if (negative) "-" else "") + intPart + (if (minorUnits > 0) ".$frac" else "")
        return decimal.fromDecimal(Decimal(text))
    }

    private companion object {
        const val MAX_MINOR_UNITS = 6
        const val MAX_INTEGER_DIGITS = 12
        const val MAX_LONG_DIGITS = 18
    }
}
