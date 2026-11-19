package stapel.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneFormatTest {
    private val nanpLike = PhoneFormat(SampleMetadata, defaultCallingCode = "1")

    @Test
    fun genericFormatterKnowsNoRegionAndShowsDigitsUngrouped() {
        val m = MaskedText.of(PhoneFormat()).type("+15551234567")
        assertEquals("+15551234567", m.raw)
        assertEquals("+15551234567", m.text)
    }

    @Test
    fun genericFormatterGroupsTheNationalPartOnceTheCallingCodeIsConfigured() {
        val m = MaskedText.of(PhoneFormat(defaultCallingCode = "998")).type("901234567")
        assertEquals("+998901234567", m.raw)
        assertEquals("+998 901 234 567", m.text)
    }

    @Test
    fun providerGroupsTheNumber() {
        val m = MaskedText.of(PhoneFormat(SampleMetadata)).type("15551234567")
        assertEquals("+15551234567", m.raw)
        assertEquals("+1 555 123 4567", m.text)
    }

    @Test
    fun nationalTypingGetsTheDefaultCallingCode() {
        val m = MaskedText.of(nanpLike).type("5551234567")
        assertEquals("+15551234567", m.raw)
        assertEquals("+1 555 123 4567", m.text)
        assertEquals(m.text.length, m.selectionEnd)
    }

    @Test
    fun typingDigitByDigitGivesTheSameValue() {
        var m = MaskedText.of(nanpLike)
        for (c in "5551234567") m = m.type(c.toString())
        assertEquals("+15551234567", m.raw)
        assertEquals("+1 555 123 4567|", m.toString())
    }

    @Test
    fun pasteOfAFormattedOrDirtyNumber() {
        for (paste in listOf("+1 (555) 123-4567", "tel: 555.123.4567 ", "(555) 123‑4567", "+1-555-123-4567\n")) {
            assertEquals("+15551234567", MaskedText.of(nanpLike).type(paste).raw, paste)
        }
    }

    @Test
    fun pasteOfAFullNumberReplacesTheValue() {
        val m = MaskedText.of(nanpLike, "+15550000000").select(5).type("+1 555 123 4567")
        assertEquals("+15551234567", m.raw)
    }

    @Test
    fun replacingTheWholeValueWithANationalNumberKeepsTheCallingCode() {
        val m = MaskedText.of(nanpLike, "+15550000000")
        assertEquals("+15551234567", m.select(0, m.text.length).type("555 123 4567").raw)
        assertEquals("+15550000001", m.select(m.text.length - 1, m.text.length).type("1").raw, "a partial edit stays partial")
    }

    @Test
    fun internationalPrefixStandsForPlus() {
        val m = MaskedText.of(PhoneFormat(SampleMetadata)).type("0044 20 7946 0000")
        assertEquals("+442079460000", m.raw)
        assertEquals("+44 2079 460000", m.text)
        var typed = MaskedText.of(PhoneFormat())
        for (c in "0044") typed = typed.type(c.toString())
        assertEquals("+44", typed.raw)
    }

    @Test
    fun lockedCallingCodeSurvivesDeletionAndRefusesAnotherCountry() {
        val locked = PhoneFormat(SampleMetadata, defaultCallingCode = "44", lockCallingCode = true)
        var m = MaskedText.of(locked).type("020 7946 0000")
        assertEquals("+442079460000", m.raw, "the provider drops the trunk prefix")
        m = m.type("+1 555 123 4567")
        assertEquals("+442079460000", m.raw, "a number of another country is refused")
        m = m.select(0, 3).type("9")
        assertEquals("+4492079460000", m.raw, "an edit over the calling code lands after it")
        assertEquals(m.raw, m.select(3).backspace().backspace().raw, "backspace cannot eat into the calling code")
        m = m.select(m.text.length)
        repeat(20) { m = m.backspace() }
        assertEquals("", m.raw, "no national digits left = empty, never a dangling +44")
    }

    @Test
    fun backspaceAcrossAGroupSpaceDeletesTheDigitBeforeIt() {
        val m = MaskedText.of(nanpLike, "+15551234567").select(7)
        assertEquals("+1 555| 123 4567", m.toString(), "a cursor placed after the space snaps before it")
        val after = m.backspace()
        assertEquals("+1551234567", after.raw)
        assertEquals("+1 55|1 234 567", after.toString())
    }

    @Test
    fun arabicIndicAndRtlMarkedInputIsNormalised() {
        assertEquals("+15551234567", MaskedText.of(nanpLike).type("٥٥٥١٢٣٤٥٦٧").raw)
        assertEquals("+15551234567", MaskedText.of(nanpLike).type("‏+1 555 123 4567‏").raw)
        assertEquals("+15551234567", MaskedText.of(nanpLike).type("‫+١ ٥٥٥ ١٢٣ ٤٥٦٧‬").raw)
        assertEquals("+15551234567", MaskedText.of(nanpLike).type("＋１５５５１２３４５６７").raw)
    }

    @Test
    fun digitsCanBeShownInTheLocaleFamily() {
        val f = PhoneFormat(SampleMetadata, zeroDigit = '٠').format("+15551234567")
        assertEquals("+١ ٥٥٥ ١٢٣ ٤٥٦٧", f.text)
        assertEquals("+15551234567", PhoneFormat(SampleMetadata, zeroDigit = '٠').normalize(f.text))
    }

    @Test
    fun imeCompositionReplacingItsRegionSettlesOnTheCommittedText() {
        var m = MaskedText.of(nanpLike).type("５")
        assertEquals("+1 5", m.text)
        m = m.replace(m.text.length - 1, m.text.length, "５５")
        assertEquals("+1 55", m.text)
        m = m.replace(m.text.length - 2, m.text.length, "５５５")
        assertEquals("+1 555", m.text)
        assertEquals("+1555", m.raw)
    }

    @Test
    fun anyTextIsTotalNeverAnException() {
        for (bad in listOf("", "abc", "+", "++++", "12", "+" + "9".repeat(40), "\uD83D", "٠٠٠")) {
            nanpLike.format(bad)
            nanpLike.normalize(bad)
            nanpLike.isComplete(bad)
        }
        assertEquals("+" + "9".repeat(15), PhoneFormat().normalize("+" + "9".repeat(40)), "E.164 caps at 15 digits")
        assertEquals("+15551234567", nanpLike.normalize("+1555123456789"), "the provider's maximum applies")
    }

    @Test
    fun completenessFollowsTheProvider() {
        assertTrue(nanpLike.isComplete("+15551234567"))
        assertFalse(nanpLike.isComplete("+1555"))
        assertFalse(nanpLike.isComplete("15551234567"), "raw without + is not canonical")
    }
}
