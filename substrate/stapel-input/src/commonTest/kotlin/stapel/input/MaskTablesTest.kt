package stapel.input

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternFormatTest {
    private val us = PatternFormat("(###) ###-####")

    @Test
    fun typedDigitsFillTheSlotsAndLiteralsAppearLazily() {
        assertEquals("(555", MaskedText.of(us).type("555").text)
        assertEquals("(555) 0", MaskedText.of(us).type("5550").text)
        val full = MaskedText.of(us).type("5550100199")
        assertEquals("(555) 010-0199", full.text)
        assertEquals("5550100199", full.raw)
        assertTrue(us.isComplete(full.raw))
        assertFalse(us.isComplete("555"))
    }

    @Test
    fun pasteOfFormattedOrDirtyText() {
        for (paste in listOf("(555) 010-0199", "555.010.0199", "555 010 0199 ext", "‎(555) 010-0199")) {
            assertEquals("5550100199", us.normalize(paste), paste)
        }
    }

    @Test
    fun typedLiteralsAreSwallowed() {
        val m = MaskedText.of(us).type("555").type(")").type(" ").type("0")
        assertEquals("(555) 0", m.text)
        assertEquals("5550", m.raw)
    }

    @Test
    fun deletionAcrossLiterals() {
        assertEquals("(555", MaskedText.of(us, "5550").backspace().text, "deleting the digit after a literal hides the literal")
        val m = MaskedText.of(us, "555010").select(6)
        assertEquals("(555|) 010", m.toString(), "a cursor after ') ' snaps before it")
        val back = m.backspace()
        assertEquals("(55|0) 10", back.toString())
        assertEquals("(55|1) 0", back.deleteForward().toString())
        val range = MaskedText.of(us, "555010").replace(4, 6, "")
        assertEquals("555010", range.raw, "a range of literals only deletes nothing")
    }

    @Test
    fun optionalPartIsNotNeededForCompleteness() {
        val card = PatternFormat("#### ####[ ####]")
        assertTrue(card.isComplete("12345678"))
        assertFalse(card.isComplete("1234567"))
        assertEquals("1234 5678 9012", card.format("123456789012").text)
    }

    @Test
    fun slotKindsAndUpperCase() {
        val plate = PatternFormat("AA-##-**", upperCase = true)
        assertEquals("AB-12-CD", MaskedText.of(plate).type("ab12cd").text)
        assertEquals("A", MaskedText.of(plate).type("1a").raw, "a digit has no place in a letter slot")
        assertEquals("ЖЯ-12", MaskedText.of(plate).type("жя12").text, "letters of any script")
    }

    @Test
    fun literalPrefixIsReadFromAPasteWithOrWithoutSpaces() {
        val p = PatternFormat("+1 (###) ###")
        assertEquals("555123", p.normalize("+1 (555) 123"))
        assertEquals("555123", p.normalize("+1(555)123"))
        assertEquals("+1 (555", MaskedText.of(p).type("555").text)
    }

    @Test
    fun foreignDigitsAreFolded() {
        assertEquals("5550100199", us.normalize("٥٥٥٠١٠٠١٩٩"))
        assertEquals("5550100199", us.normalize("５５５０１００１９９"))
    }

    @Test
    fun patternSyntaxIsTotal() {
        assertEquals(listOf(SlotKind.Digit), MaskPattern.parse("\\#-#\\").slots)
        assertEquals("#-", MaskPattern.parse("\\#-#").literalsBefore[0])
        assertEquals(2, MaskPattern.parse("##[##").requiredSlots)
        assertEquals(0, MaskPattern.parse("abc").slots.size)
        assertEquals("", PatternFormat("abc").format("123").text)
    }
}

class GroupedDigitsAndCodeTest {
    @Test
    fun cardNumberGroupsInFoursAndCapsAt19() {
        val card = GroupedDigitsFormat.paymentCard()
        assertEquals("4111 1111 1111 1111", MaskedText.of(card).type("4111111111111111").text)
        assertEquals("4111111111111111", card.normalize("4111-1111-1111-1111"))
        assertEquals(19, card.normalize("1".repeat(25)).length)
        val full = MaskedText.of(card, "1".repeat(19)).select(2).type("9")
        assertEquals("1".repeat(19), full.raw, "typing into a full value is refused")
    }

    @Test
    fun backspaceAcrossASeparator() {
        val m = MaskedText.of(GroupedDigitsFormat.paymentCard(), "4111111111111111").select(5)
        assertEquals("4111| 1111 1111 1111", m.toString())
        assertEquals("411|1 1111 1111 111", m.backspace().toString())
    }

    @Test
    fun extraDigitsContinueInTheLastGroupSize() {
        assertEquals("123-45-67-89", GroupedDigitsFormat(listOf(3, 2), "-").format("123456789").text)
        assertEquals("1234", GroupedDigitsFormat(listOf(0, -1)).format("1234").text)
    }

    @Test
    fun codeLengthComesFromConfiguration() {
        assertEquals("1234", CodeFormat(4).normalize("123456"))
        assertEquals("123456", CodeFormat(6).normalize("123456"))
        assertEquals("12345678", CodeFormat(8).normalize("1234 5678"))
        assertTrue(CodeFormat(4).isComplete("1234"))
        assertFalse(CodeFormat(6).isComplete("1234"))
        assertEquals(1, CodeFormat(0).length, "a nonsensical length is clamped, not a crash")
    }

    @Test
    fun codePasteOfAWholeMessageFindsTheCode() {
        val six = CodeFormat(6)
        assertEquals("123456", six.normalize("Your code is 123 456. Valid for 10 minutes."))
        assertEquals("482913", six.normalize("482913 is your code"))
        assertEquals("1234", six.normalize("code 1234"), "no run of the right length: digits as they come")
        assertEquals("123456", six.normalize("١٢٣٤٥٦"))
        assertEquals("123456", MaskedText.of(six, "99").type("code: 123-456").raw, "a paste of the code replaces a partial value")
    }
}

class DateFormatTest {
    private val dmy = DateFormat(DateOrder.DayMonthYear, '.')

    @Test
    fun typingFillsFieldsInLocaleOrder() {
        val m = MaskedText.of(dmy).type("19112026")
        assertEquals("19.11.2026", m.text)
        assertEquals(LocalDate(2026, 11, 19), dmy.toDate(m.raw))
        assertEquals("11/19/2026", DateFormat(DateOrder.MonthDayYear, '/').format("11192026").text)
        assertEquals("2026-11-19", DateFormat().format("20261119").text)
    }

    @Test
    fun aTypedSeparatorPadsAOneDigitField() {
        var m = MaskedText.of(dmy).type("1").type(".")
        assertEquals("01", m.text)
        m = m.type("1").type("/")
        assertEquals("01.01", m.text)
        assertEquals(LocalDate(2026, 1, 1), dmy.toDate(m.type("2026").raw))
    }

    @Test
    fun pasteInIsoOrLocaleFormIsUnderstood() {
        assertEquals("19112026", dmy.normalize("2026-11-19"))
        assertEquals("19112026", dmy.normalize("19/11/2026"))
        assertEquals("05012026", dmy.normalize("2026-1-5"))
        assertEquals("20261119", DateFormat().normalize("2026-11-19"))
    }

    @Test
    fun impossibleDatesAreNotDates() {
        assertNull(dmy.toDate("31022026"))
        assertNull(dmy.toDate("29022025"))
        assertEquals(LocalDate(2024, 2, 29), dmy.toDate("29022024"))
        assertNull(dmy.toDate("1911"))
        assertEquals("05012026", dmy.fromDate(LocalDate(2026, 1, 5)))
    }

    @Test
    fun backspaceAcrossTheSeparator() {
        val m = MaskedText.of(dmy, "1911").select(3)
        assertEquals("19|.11", m.toString())
        assertEquals("1|1.1", m.backspace().toString())
    }

    @Test
    fun arabicLocaleShowsItsDigitsAndReadsAnyDigits() {
        val ar = DateFormat(InputLocale(zeroDigit = '٠', dateOrder = DateOrder.DayMonthYear, dateSeparator = '/'))
        assertEquals("١٩/١١/٢٠٢٦", ar.format("19112026").text)
        assertEquals("19112026", ar.normalize("١٩/١١/٢٠٢٦"))
        assertEquals("19112026", ar.normalize("‏١٩/١١/٢٠٢٦‏"))
    }
}

class DecimalAndMoneyTest {
    private val neutral = DecimalFormat(maxFractionDigits = 3, allowNegative = true)
    private val spaced = InputLocale(decimalSeparator = ',', groupSeparator = " ")
    private val dotted = InputLocale(decimalSeparator = ',', groupSeparator = ".")

    @Test
    fun groupingAndDecimalSeparatorFromTheLocale() {
        assertEquals("1,234.5", MaskedText.of(neutral).type("1234.5").text)
        val de = MaskedText.of(DecimalFormat(spaced, 2)).type("1234,56")
        assertEquals("1234.56", de.raw)
        assertEquals("1 234,56", de.text)
        assertEquals("1234.56", DecimalFormat(spaced, 2).normalize("1234.56"), "'.' is a decimal point where it is not the group separator")
        assertEquals("12,34,567", DecimalFormat(InputLocale(secondaryGroupSize = 2)).format("1234567").text)
    }

    @Test
    fun dotGroupedLocales() {
        val f = DecimalFormat(dotted, 2)
        assertEquals("1234", MaskedText.of(f).type("1.234").raw, "'.' is the group separator here")
        assertEquals("1234.56", f.normalize("1.234,56"))
        assertEquals("1234.56", f.normalize("1,234.56"), "a paste with both marks: the last one is the decimal point")
        assertEquals("1.234,56", f.format("1234.56").text)
    }

    @Test
    fun leadingZerosSignAndCaps() {
        var m = MaskedText.of(neutral)
        for (c in "007") m = m.type(c.toString())
        assertEquals("7", m.raw)
        assertEquals("0.", MaskedText.of(neutral).type(".").raw)
        assertEquals("1.234", neutral.normalize("1.2345"))
        assertEquals("-5", MaskedText.of(neutral).type("5").type("-").raw)
        assertEquals("5", DecimalFormat().normalize("-5"), "no sign where negatives are not allowed")
        assertEquals("12", DecimalFormat(maxFractionDigits = 0).normalize("12.7"))
    }

    @Test
    fun backspaceAcrossAGroupSeparator() {
        val m = MaskedText.of(neutral, "1234").select(2)
        assertEquals("1|,234", m.toString())
        assertEquals("|234", m.backspace().toString())
    }

    @Test
    fun arabicIndicDigitsAndSeparators() {
        val ar = DecimalFormat(InputLocale(decimalSeparator = '٫', groupSeparator = "٬", zeroDigit = '٠'), 2)
        val m = MaskedText.of(ar).type("١٢٣٤٫٥")
        assertEquals("1234.5", m.raw)
        assertEquals("١٬٢٣٤٫٥", m.text)
    }

    @Test
    fun moneyMinorUnitsAndAffixes() {
        val usd = MoneyFormat(minorUnits = 2, prefix = "$", allowNegative = true)
        assertEquals("-$12.5", MaskedText.of(usd).type("-12.5").text)
        assertEquals(-1250L, usd.toMinorUnits("-12.5"))
        assertEquals("12.50", usd.fromMinorUnits(1250))
        assertEquals("$12.50", usd.format("12.50").text)
        assertEquals("1000.00", usd.normalize("$1,000.00 USD"))
        assertEquals("12.34", usd.normalize("12.345"), "minor units cap the fraction")
        val yen = MoneyFormat(minorUnits = 0, suffix = " ¥")
        assertEquals("1,500 ¥", yen.format("1500").text)
        assertEquals("1500", yen.fromMinorUnits(1500))
        val rupee = MoneyFormat(minorUnits = 2, prefix = "Rs. ")
        assertEquals("1000.5", rupee.normalize("Rs. 1,000.5"), "an affix holding a dot is not a decimal point")
    }

    @Test
    fun conversionsToTheWireDecimal() {
        assertEquals("12", neutral.toDecimal("12.")?.text)
        assertEquals("0.5", neutral.toDecimal(".5")?.text)
        assertNull(neutral.toDecimal(""))
        assertNull(neutral.toDecimal("-"))
        assertEquals("19.99", DecimalFormat(maxFractionDigits = 2).fromDecimal(stapel.wire.Decimal("19.990")))
    }
}
