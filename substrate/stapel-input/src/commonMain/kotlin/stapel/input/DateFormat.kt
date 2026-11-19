package stapel.input

import kotlinx.datetime.LocalDate

/**
 * A calendar date typed as digits: raw holds up to 8 ASCII digits in the order of [order] (day and
 * month two digits each, year four), display inserts [separator] between fields lazily
 * (`19.11.2026` for `"19112026"` in day-month-year order).
 *
 * Typing a separator after a one-digit field pads it (`1.` → `01.`). A paste in ISO form
 * (`2026-11-19`, four digits first) is reordered into [order]; any other paste is read in [order].
 * Calendar validity is not enforced while typing; [toDate] returns null for an impossible date and
 * the date validator reports it.
 */
public class DateFormat(
    /** Field order. */
    public val order: DateOrder = DateOrder.YearMonthDay,
    /** Separator shown between fields. */
    public val separator: Char = '-',
    /** Zero of the digit family to show ('0' keeps ASCII). */
    public val zeroDigit: Char = '0',
) : InputFormat {
    /** The order and separator of [locale]. */
    public constructor(locale: InputLocale) : this(locale.dateOrder, locale.dateSeparator, locale.zeroDigit)

    private val fields: List<Char> = when (order) {
        DateOrder.DayMonthYear -> listOf('d', 'm', 'y')
        DateOrder.MonthDayYear -> listOf('m', 'd', 'y')
        DateOrder.YearMonthDay -> listOf('y', 'm', 'd')
    }
    private val widths: List<Int> = fields.map { if (it == 'y') YEAR_WIDTH else 2 }

    override val keyboard: InputKeyboard get() = InputKeyboard.Number

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val clean = digitsOf(raw).take(TOTAL)
        val (s, e) = clampRange(start, end, clean.length)
        val prefix = clean.cut(0, s)
        val suffix = clean.cut(e, clean.length)
        val text = foldInput(inserted)
        if (s == 0 && order != DateOrder.YearMonthDay) isoReordered(text)?.let { iso -> return RawEdit(iso, iso.length) }
        val head = parse(prefix + text)
        val room = (TOTAL - suffix.length).coerceAtLeast(0)
        val kept = head.take(maxOf(room, prefix.length))
        val out = (kept + suffix).take(TOTAL)
        return RawEdit(out, kept.length)
    }

    override fun format(raw: String): Formatted {
        val digits = digitsOf(raw).take(TOTAL)
        val b = Formatted.Builder()
        var field = 0
        var inField = 0
        for (d in digits) {
            if (inField == widths[field]) {
                field++
                inField = 0
                b.literal(separator.toString())
            }
            b.raw(d, InputChars.shapeDigit(d, zeroDigit))
            inField++
        }
        return b.build()
    }

    override fun isComplete(raw: String): Boolean = toDate(raw) != null

    /** The date [raw] spells, or null while incomplete or when no such day exists. */
    public fun toDate(raw: String): LocalDate? {
        if (raw.length != TOTAL || raw.any { it !in '0'..'9' }) return null
        var at = 0
        var day = 0
        var month = 0
        var year = 0
        for ((i, f) in fields.withIndex()) {
            val v = raw.substring(at, at + widths[i]).toInt()
            at += widths[i]
            when (f) {
                'd' -> day = v
                'm' -> month = v
                else -> year = v
            }
        }
        if (year < 1 || month !in 1..12 || day < 1 || day > daysIn(year, month)) return null
        return LocalDate(year, month, day)
    }

    /** The raw value for [date] (years outside 1..9999 give an empty value). */
    public fun fromDate(date: LocalDate): String {
        if (date.year !in 1..9999) return ""
        return fields.joinToString("") { f ->
            when (f) {
                'd' -> date.day.toString().padStart(2, '0')
                'm' -> date.month.ordinal.plus(1).toString().padStart(2, '0')
                else -> date.year.toString().padStart(YEAR_WIDTH, '0')
            }
        }
    }

    /** Digits of [text] placed into fields; a separator after a partly filled field pads it with zeros. */
    private fun parse(text: String): String {
        val out = StringBuilder()
        var field = 0
        var inField = 0
        for (c in text) {
            if (field >= fields.size) break
            if (c in '0'..'9') {
                out.append(c)
                inField++
                if (inField == widths[field]) {
                    field++
                    inField = 0
                }
            } else if (isSeparator(c) && inField > 0 && fields[field] != 'y') {
                val missing = widths[field] - inField
                out.insert(out.length - inField, "0".repeat(missing))
                field++
                inField = 0
            }
        }
        return out.toString()
    }

    /** A pasted ISO date (four digits, separator, month, separator, day) as raw in [order]; else null. */
    private fun isoReordered(text: String): String? {
        val m = ISO.matchEntire(text.trim { InputChars.isSpace(it) }) ?: return null
        val y = m.groupValues[1]
        val mo = m.groupValues[2].padStart(2, '0')
        val d = m.groupValues[3].padStart(2, '0')
        return fields.joinToString("") { f -> if (f == 'd') d else if (f == 'm') mo else y }
    }

    private fun isSeparator(c: Char): Boolean = !c.isLetterOrDigit() && !InputChars.isInvisible(c)

    private companion object {
        const val YEAR_WIDTH = 4
        const val TOTAL = 8
        val ISO = Regex("([0-9]{4})[^0-9]([0-9]{1,2})[^0-9]([0-9]{1,2})")

        fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}
