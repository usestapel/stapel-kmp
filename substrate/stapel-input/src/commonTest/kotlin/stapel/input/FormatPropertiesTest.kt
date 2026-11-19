package stapel.input

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** A fixed seed, injected: every run explores the same edits, a failure reproduces. */
private const val SEED = 20261119L

/** A provider used only by tests: calling code "1", national groups 3-3-4; and "44" with 4-6. */
internal object SampleMetadata : PhoneMetadata {
    override fun callingCodeLength(digits: String): Int? = when {
        digits.startsWith("1") -> 1
        digits.startsWith("44") -> 2
        else -> null
    }

    override fun nationalGroups(callingCode: String, national: String): List<Int>? = when (callingCode) {
        "1" -> listOf(3, 3, 4)
        "44" -> listOf(4, 6)
        else -> null
    }

    override fun maxDigits(callingCode: String?): Int = if (callingCode == "1") 11 else PhoneMetadata.E164_MAX_DIGITS

    override fun nationalToE164(callingCode: String, nationalDigits: String): String =
        callingCode + if (callingCode == "44") nationalDigits.removePrefix("0") else nationalDigits
}

private val arabic = InputLocale(decimalSeparator = '٫', groupSeparator = "٬", zeroDigit = '٠', dateOrder = DateOrder.DayMonthYear, dateSeparator = '/')
private val spaced = InputLocale(decimalSeparator = ',', groupSeparator = " ", dateOrder = DateOrder.DayMonthYear, dateSeparator = '.')
private val dotted = InputLocale(decimalSeparator = ',', groupSeparator = ".", dateOrder = DateOrder.MonthDayYear, dateSeparator = '/')
private val indian = InputLocale(primaryGroupSize = 3, secondaryGroupSize = 2)

internal val allFormats: List<Pair<String, InputFormat>> = listOf(
    "plain" to PlainFormat(),
    "plain capped digits" to PlainFormat(maxLength = 5) { it.isDigit() },
    "grouped 4" to GroupedDigitsFormat.paymentCard(),
    "grouped 3-2 dash" to GroupedDigitsFormat(listOf(3, 2), "-", maxDigits = 9),
    "grouped none" to GroupedDigitsFormat(emptyList(), maxDigits = 4),
    "grouped arabic digits" to GroupedDigitsFormat(listOf(2), " ", zeroDigit = '٠'),
    "code 6" to CodeFormat(6),
    "code 4" to CodeFormat(4),
    "code 0 configured" to CodeFormat(0),
    "pattern phone-like" to PatternFormat("(###) ###-####"),
    "pattern optional" to PatternFormat("#### ####[ ####]"),
    "pattern letters upper" to PatternFormat("AA-##-**", upperCase = true),
    "pattern digit literal" to PatternFormat("+1 (###) ###"),
    "pattern escapes" to PatternFormat("\\#-#\\A-A"),
    "pattern unclosed" to PatternFormat("##[##"),
    "pattern no slots" to PatternFormat("abc"),
    "pattern empty" to PatternFormat(""),
    "phone generic" to PhoneFormat(),
    "phone sample" to PhoneFormat(SampleMetadata),
    "phone default cc" to PhoneFormat(SampleMetadata, defaultCallingCode = "1"),
    "phone locked cc" to PhoneFormat(SampleMetadata, defaultCallingCode = "44", lockCallingCode = true),
    "phone generic default" to PhoneFormat(defaultCallingCode = "998"),
    "date iso" to DateFormat(),
    "date dmy" to DateFormat(spaced),
    "date mdy" to DateFormat(dotted),
    "date arabic" to DateFormat(arabic),
    "decimal neutral" to DecimalFormat(maxFractionDigits = 3, allowNegative = true),
    "decimal integer" to DecimalFormat(maxFractionDigits = 0, maxIntegerDigits = 6),
    "decimal spaced" to DecimalFormat(spaced, maxFractionDigits = 2),
    "decimal dotted" to DecimalFormat(dotted, maxFractionDigits = 2, allowNegative = true),
    "decimal indian" to DecimalFormat(indian, maxFractionDigits = 2),
    "decimal arabic" to DecimalFormat(arabic, maxFractionDigits = 2),
    "money prefix" to MoneyFormat(minorUnits = 2, prefix = "$", allowNegative = true),
    "money suffix dotted" to MoneyFormat(dotted, minorUnits = 2, suffix = " €"),
    "money affix with dot" to MoneyFormat(minorUnits = 2, prefix = "Rs. "),
    "money zero units" to MoneyFormat(spaced, minorUnits = 0, suffix = " ¥"),
)

class FormatPropertiesTest {
    @Test
    fun everyBuiltInFormatKeepsTheLaws() {
        val random = Random(SEED)
        val failures = allFormats.flatMap { (name, format) ->
            FormatLaws.check(format, random).map { "$name: $it" }
        }
        assertTrue(failures.isEmpty(), failures.take(10).joinToString("\n"))
    }

    @Test
    fun randomPatternsKeepTheLaws() {
        val random = Random(SEED + 1)
        val parts = listOf("#", "A", "*", "-", " ", "(", ")", "[", "]", "\\#", "\\A", "/", ".", "x", "1")
        val failures = (1..60).flatMap {
            val pattern = buildString { repeat(random.nextInt(1, 14)) { append(parts[random.nextInt(parts.size)]) } }
            FormatLaws.check(PatternFormat(pattern, upperCase = random.nextBoolean()), random, steps = 120).map { "pattern \"$pattern\": $it" }
        }
        assertTrue(failures.isEmpty(), failures.take(10).joinToString("\n"))
    }

    @Test
    fun maskedTextNeverParksTheCursorAfterALiteral() {
        val random = Random(SEED + 2)
        for ((name, format) in allFormats) {
            var m = MaskedText.of(format)
            repeat(300) {
                m = when (random.nextInt(6)) {
                    0 -> m.type(FormatLaws.garbage(random, 3))
                    1 -> m.backspace()
                    2 -> m.deleteForward()
                    3 -> m.select(random.nextInt(-1, m.text.length + 2), random.nextInt(-1, m.text.length + 2))
                    4 -> m.replace(random.nextInt(-1, m.text.length + 2), random.nextInt(-1, m.text.length + 2), FormatLaws.garbage(random, 12))
                    else -> m.type(FormatLaws.garbage(random, 20))
                }
                for (p in listOf(m.selectionStart, m.selectionEnd)) {
                    assertTrue(p == 0 || !m.formatted.isLiteralAt(p - 1), "$name: cursor after a literal in $m")
                }
                assertTrue(m.selectionStart <= m.selectionEnd && m.selectionEnd <= m.text.length, "$name: selection out of range in $m")
            }
        }
    }
}

/** Inversions of the law harness: formats that break one law each must be caught. */
class FormatLawsInversionTest {
    /** Throws on unexpected input, like a mask that `require`s a fixed number of digits. */
    private object Throwing : InputFormat {
        override val keyboard: InputKeyboard get() = InputKeyboard.Number

        override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
            val next = raw.substring(0, start) + inserted + raw.substring(end)
            return RawEdit(next, start + inserted.length)
        }

        override fun format(raw: String): Formatted {
            require(raw.all { it.isDigit() })
            return Formatted.identity(raw)
        }
    }

    /** Displays something it cannot read back. */
    private object Lossy : InputFormat {
        override val keyboard: InputKeyboard get() = InputKeyboard.Number

        override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit =
            GroupedDigitsFormat(emptyList()).edit(raw, start, end, inserted)

        override fun format(raw: String): Formatted {
            val b = Formatted.Builder()
            for (c in raw.filter { it in '0'..'9' }) b.raw(c, 'x')
            return b.build()
        }
    }

    @Test
    fun aThrowingFormatIsCaught() {
        val broken = FormatLaws.check(Throwing, Random(SEED))
        assertTrue(broken.any { "threw" in it }, broken.toString())
    }

    @Test
    fun aCursorAfterALiteralIsCaught() {
        val f = PatternFormat("(###) ###").format("5550")
        val endAffinity = f.text.indexOf('0')
        assertTrue(FormatLaws.parkedAfterLiteral(f, endAffinity), "the predicate must flag \"${f.text}\" at $endAffinity")
        assertTrue(!FormatLaws.parkedAfterLiteral(f, f.rawToDisplay(3)))
    }

    @Test
    fun aLossyDisplayIsCaught() {
        val broken = FormatLaws.check(Lossy, Random(SEED))
        assertTrue(broken.any { "round trip" in it }, broken.toString())
    }
}
