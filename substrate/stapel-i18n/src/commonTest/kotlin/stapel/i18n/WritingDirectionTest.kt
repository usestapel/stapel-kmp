package stapel.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class WritingDirectionTest {
    @Test
    fun rightToLeftLanguagesByTheirDefaultScript() {
        for (tag in listOf("ar", "ar-EG", "he", "iw", "fa-IR", "ur", "ps", "yi", "ckb", "ug", "dv", "sd")) {
            assertEquals(WritingDirection.Rtl, writingDirectionOf(tag), tag)
        }
        for (tag in listOf("en", "en-US", "ru", "es", "zh-Hans", "ja", "tr", "ku", "")) {
            assertEquals(WritingDirection.Ltr, writingDirectionOf(tag), tag)
        }
    }

    @Test
    fun anExplicitScriptWinsOverTheLanguage() {
        assertEquals(WritingDirection.Rtl, writingDirectionOf("az-Arab"))
        assertEquals(WritingDirection.Rtl, writingDirectionOf("pa_Arab_PK"))
        assertEquals(WritingDirection.Ltr, writingDirectionOf("ku-Latn"))
        assertEquals(WritingDirection.Ltr, writingDirectionOf("uz-Cyrl"))
        // A region is not a script: "ar-SA" stays the language's default.
        assertEquals(WritingDirection.Rtl, writingDirectionOf("ar-SA"))
        assertEquals(WritingDirection.Ltr, writingDirectionOf("sd-Deva"))
    }
}
