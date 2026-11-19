package stapel.input.compose

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import stapel.input.CodeFormat
import stapel.input.InputFormat
import stapel.input.InputLocale
import stapel.input.MoneyFormat
import stapel.input.PatternFormat
import stapel.input.PhoneFormat
import stapel.input.PhoneMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

private const val FIELD = "field"

/** Test-only region knowledge: calling code "1", groups 3-3-4. */
private object OneMetadata : PhoneMetadata {
    override fun callingCodeLength(digits: String): Int? = if (digits.startsWith("1")) 1 else null

    override fun nationalGroups(callingCode: String, national: String): List<Int> = listOf(3, 3, 4)
}

/**
 * A stand-in for a skin's text-field slot: it takes the two transformations as plain foundation
 * types, which is all a `TextFieldSpec` needs to carry — the slot never sees this artifact.
 */
@androidx.compose.runtime.Composable
private fun SlotLikeField(state: TextFieldState, input: InputTransformation?, output: OutputTransformation?) {
    BasicTextField(state, Modifier.testTag(FIELD), inputTransformation = input, outputTransformation = output)
}

@OptIn(ExperimentalTestApi::class)
class InputMaskUiTest {
    private fun ComposeUiTest.field(format: InputFormat, masked: Boolean = true): TextFieldState {
        val state = TextFieldState()
        val mask = format.asInputMask()
        setContent { SlotLikeField(state, mask.inputTransformation.takeIf { masked }, mask.outputTransformation.takeIf { masked }) }
        return state
    }

    private val phone = PhoneFormat(OneMetadata, defaultCallingCode = "1")

    @Test
    fun typingAPhoneNumberKeepsRawInStateAndShowsTheFormat() = runComposeUiTest {
        val state = field(phone)
        for (c in "5551234567") onNodeWithTag(FIELD).performTextInput(c.toString())
        assertEquals("+15551234567", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("+1 555 123 4567")
        assertEquals(TextRange(12), state.selection)
    }

    @Test
    fun pasteOfAFormattedNumberAndAFullReplacement() = runComposeUiTest {
        val state = field(phone)
        onNodeWithTag(FIELD).performTextInput("+1 (555) 123-4567")
        assertEquals("+15551234567", state.text.toString())
        onNodeWithTag(FIELD).performTextReplacement("tel: 555.000.1111")
        assertEquals("+15550001111", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("+1 555 000 1111")
    }

    @Test
    fun withoutTheMaskTheFieldKeepsDirtyText() = runComposeUiTest {
        val state = field(phone, masked = false)
        onNodeWithTag(FIELD).performTextInput("+1 (555) 123-4567")
        assertEquals("+1 (555) 123-4567", state.text.toString(), "inversion: the normalisation comes from the binding")
    }

    @Test
    fun foreignDigitsAreFoldedInTheField() = runComposeUiTest {
        val state = field(phone)
        onNodeWithTag(FIELD).performTextInput("٥٥٥١٢٣٤٥٦٧")
        assertEquals("+15551234567", state.text.toString())
        onNodeWithTag(FIELD).performTextReplacement("‏+١ ٥٥٥ ١٢٣ ٠٠٠٠‏")
        assertEquals("+15551230000", state.text.toString())
    }

    @Test
    fun backspaceDeletesDigitsAndHidesTheLiteralsBehindThem() = runComposeUiTest {
        val state = field(PatternFormat("(###) ###-####"))
        onNodeWithTag(FIELD).performTextInput("5550")
        onNodeWithTag(FIELD).assertTextEquals("(555) 0")
        onNodeWithTag(FIELD).performKeyInput { pressKey(Key.Backspace) }
        assertEquals("555", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("(555")
        onNodeWithTag(FIELD).performKeyInput { pressKey(Key.Backspace) }
        assertEquals("55", state.text.toString())
    }

    @Test
    fun backspaceInTheMiddleNextToAGroupSpace() = runComposeUiTest {
        val state = field(phone)
        onNodeWithTag(FIELD).performTextInput("5551234567")
        // Raw cursor after "+1555": shown before the space that follows.
        onNodeWithTag(FIELD).performTextInputSelection(TextRange(5))
        onNodeWithTag(FIELD).performKeyInput { pressKey(Key.Backspace) }
        assertEquals("+1551234567", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("+1 551 234 567")
        assertEquals(TextRange(4), state.selection)
        onNodeWithTag(FIELD).performTextInput("9")
        assertEquals("+15591234567", state.text.toString())
    }

    @Test
    fun moneyInALocaleWithCommaDecimals() = runComposeUiTest {
        val state = field(MoneyFormat(InputLocale(decimalSeparator = ',', groupSeparator = " "), minorUnits = 2, suffix = " €"))
        onNodeWithTag(FIELD).performTextInput("1234,567")
        assertEquals("1234.56", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("1 234,56 €")
    }

    @Test
    fun aCodeFieldTakesTheCodeOutOfAPastedMessage() = runComposeUiTest {
        val state = field(CodeFormat(6))
        onNodeWithTag(FIELD).performTextInput("Your code: 123 456")
        assertEquals("123456", state.text.toString())
        onNodeWithTag(FIELD).performTextInput("7")
        assertEquals("123456", state.text.toString(), "the configured length caps the code")
    }

    @Test
    fun setValueNormalisesProgrammaticText() = runComposeUiTest {
        val state = field(phone)
        val mask = phone.asInputMask()
        runOnIdle { mask.setValue(state, "(555) 123-4567") }
        assertEquals("+15551234567", state.text.toString())
        onNodeWithTag(FIELD).assertTextEquals("+1 555 123 4567")
        assertEquals("+1 555 123 4567", mask.formatted(state).text)
    }
}
