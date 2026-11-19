package stapel.input

/**
 * The locale facts a format needs, passed in by the app — this library never reads the platform
 * locale (gate G-R1). An app builds one from its own locale data (the platform's number and date
 * formatters, or the CLDR data it ships) for the locale it shows.
 *
 * [Neutral] is no country's convention: `.` decimal, `,` groups of three, ISO date order `YYYY-MM-DD`.
 */
public class InputLocale(
    /** The decimal separator shown. */
    public val decimalSeparator: Char = '.',
    /** The group separator shown (may be a no-break space, or empty for none). */
    public val groupSeparator: String = ",",
    /** Size of the group next to the decimal separator (3 almost everywhere). */
    public val primaryGroupSize: Int = 3,
    /** Size of the further groups (2 in Indian-style grouping, else equal to [primaryGroupSize]). */
    public val secondaryGroupSize: Int = primaryGroupSize,
    /** Zero of the digit family to show ('0' keeps ASCII; input of any family is always accepted). */
    public val zeroDigit: Char = '0',
    /** Order of the date fields. */
    public val dateOrder: DateOrder = DateOrder.YearMonthDay,
    /** The separator shown between date fields. */
    public val dateSeparator: Char = '-',
) {
    /** Built-in descriptors. */
    public companion object {
        /** The neutral descriptor (see the class description). */
        public val Neutral: InputLocale = InputLocale()
    }
}

/** Order of day, month and year in a typed date. */
public enum class DateOrder {
    /** Day, month, year. */
    DayMonthYear,

    /** Month, day, year. */
    MonthDayYear,

    /** Year, month, day (ISO 8601). */
    YearMonthDay,
}
