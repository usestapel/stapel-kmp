package stapel.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class StepScreensTest {
    private val code = OtpCodeState(SignInMethod.Email, "a***@example.com", codeLength = 4, isMock = true)
    private fun codeField() = hasTestTag(AuthTestTags.CODE_FIELD) and hasSetTextAction()

    @Test
    fun codeStepShowsTargetAndMockHintAndSubmitsOnceWhenFull() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { OtpCodeScreen(code, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("Code sent to a***@example.com").assertExists()
            onNodeWithTag(AuthTestTags.MOCK_HINT, useUnmergedTree = true).assertExists()
            onNode(codeField()).performTextInput("0000")
            waitForIdle()
            assertEquals(listOf<SignInEvent>(SignInEvent.SubmitCode("0000")), events.all)
        }
    }

    @Test
    fun noMockHintOnARealChannel() = runComposeUiTest {
        harness { OtpCodeScreen(code.copy(isMock = false), {}) }
        onNodeWithTag(AuthTestTags.MOCK_HINT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aRefusedCodeIsLocalizedWithAttemptsClearsTheFieldAndKeepsFocus() = runComposeUiTest {
        val events = Events<SignInEvent>()
        var state by mutableStateOf(code)
        val h = harness { OtpCodeScreen(state, events.sink) }
        onNode(codeField()).performTextInput("1111")
        waitForIdle()
        state = state.copy(error = refusal("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2"))
        waitForIdle()
        val expected = "Invalid verification code. 2 attempts remaining."
        onNode(codeField() and SemanticsMatcher.expectValue(SemanticsProperties.Error, expected)).assertExists()
        onNodeWithTag(AuthTestTags.CODE_ERROR, useUnmergedTree = true).assertExists()
        onNode(codeField() and SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(""))).assertExists()
        onNode(codeField()).assertIsFocused()
        // A fresh error re-arms the auto-submit.
        onNode(codeField()).performTextInput("2222")
        waitForIdle()
        assertEquals(listOf<SignInEvent>(SignInEvent.SubmitCode("1111"), SignInEvent.SubmitCode("2222")), events.all)
        h.locale = "ru"
        waitForIdle()
        onNode(codeField() and SemanticsMatcher.expectValue(SemanticsProperties.Error, "Неверный код подтверждения. Осталось попыток: 2.")).assertExists()
    }

    @Test
    fun resendIsGatedByTheCooldownAndEditGoesBack() = runComposeUiTest {
        val events = Events<SignInEvent>()
        var state by mutableStateOf(code.copy(resendIn = 25.seconds))
        val h = harness { OtpCodeScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            state = code.copy(resendIn = 25.seconds)
            waitForIdle()
            onNodeWithTag(AuthTestTags.RESEND).performClick()
            assertTrue(events.all.isEmpty(), "resend ran during the cooldown")
            onNodeWithText("Resend in 25s", substring = true, useUnmergedTree = true).assertExists()
            state = code.copy(resendIn = 0.seconds)
            waitForIdle()
            onNodeWithTag(AuthTestTags.RESEND).performClick()
            onNodeWithTag(AuthTestTags.EDIT_IDENTIFIER).performClick()
            assertEquals(listOf(SignInEvent.Resend, SignInEvent.EditIdentifier), events.all)
        }
    }

    @Test
    fun totpSubmitsACodeOrABackupCodeAndCanGoBack() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { TotpChallengeScreen(TotpChallengeState(), events.sink) }
        eachSkin(h) {
            events.clear()
            onNode(hasTestTag(AuthTestTags.TOTP_FIELD) and hasSetTextAction()).performTextInput("123456")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            onNodeWithTag(AuthTestTags.TOTP_TOGGLE).performClick()
            onNode(hasTestTag(AuthTestTags.TOTP_FIELD) and hasSetTextAction()).performTextInput("abcd-efgh")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            onNodeWithTag(AuthTestTags.BACK_TO_FORM).performClick()
            assertEquals(
                listOf(SignInEvent.SubmitTotp("123456", false), SignInEvent.SubmitTotp("abcd-efgh", true), SignInEvent.BackToForm),
                events.all,
            )
        }
    }

    @Test
    fun totpRefusalIsOnTheField() = runComposeUiTest {
        harness { TotpChallengeScreen(TotpChallengeState(error = refusal("error.400.invalid_code", 400)), {}) }
        onNode(hasTestTag(AuthTestTags.TOTP_FIELD) and hasErrorSemantics).assertExists()
    }

    @Test
    fun forcedChangeChecksTheRepetitionThenSubmits() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { ForcedPasswordChangeScreen(ForcedPasswordChangeState(), events.sink) }
        eachSkin(h) {
            events.clear()
            onNode(hasTestTag(AuthTestTags.PASSWORD) and hasSetTextAction()).performTextInput("new-pass-1")
            onNode(hasTestTag(AuthTestTags.PASSWORD_CONFIRM) and hasSetTextAction()).performTextInput("new-pass-2")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            onNode(fieldError(AuthTestTags.PASSWORD_CONFIRM, "Passwords don't match")).assertExists()
            assertTrue(events.all.isEmpty())
        }
    }

    @Test
    fun forcedChangeSubmitsAMatchingPassword() = runComposeUiTest {
        val events = Events<SignInEvent>()
        harness { ForcedPasswordChangeScreen(ForcedPasswordChangeState(), events.sink) }
        onNode(hasTestTag(AuthTestTags.PASSWORD) and hasSetTextAction()).performTextInput("new-pass-1")
        onNode(hasTestTag(AuthTestTags.PASSWORD_CONFIRM) and hasSetTextAction()).performTextInput("new-pass-1")
        onNodeWithTag(AuthTestTags.SUBMIT).performClick()
        assertEquals(listOf<SignInEvent>(SignInEvent.SubmitNewPassword("new-pass-1")), events.all)
    }

    @Test
    fun lockoutNamesTheWaitInTheLocaleAndGoesBack() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val state = LockedState(refusal(
            "error.423.account_locked",
            423,
            "retry_after" to "300",
            "retry_after_display" to "5:00",
            "retry_after_minutes" to "5",
        ), retryIn = 5.minutes)
        val h = harness { LockedScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("Sign-in is paused").assertExists()
            onNodeWithText("Try again in 5 minutes.").assertExists()
            onNodeWithTag(AuthTestTags.BACK_TO_FORM).performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.BackToForm), events.all)
        }
        h.locale = "ru"
        waitForIdle()
        onNodeWithText("Попробуйте через 5 минут.", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Повторите попытку через 5:00 (300 с, около 5 мин).", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aRateLimitNamesTheWaitInSecondsInTheLocale() = runComposeUiTest {
        val limit = refusal("error.429.rate_limit", 429, "retry_after" to "30", "retry_after_display" to "0:30", "retry_after_minutes" to "1")
        val h = harness { LockedScreen(LockedState(limit, retryIn = 30.seconds), {}) }
        eachSkin(h) {
            onNodeWithTag(AuthTestTags.LOCKED_WAIT).assertTextEquals("Try again in 30 seconds.")
            onNodeWithText("Try again in 0:30", substring = true, useUnmergedTree = true).assertExists()
        }
        h.locale = "ru"
        waitForIdle()
        onNodeWithTag(AuthTestTags.LOCKED_WAIT).assertTextEquals("Попробуйте через 30 секунд.")
    }

    @Test
    fun lockoutWithoutAKnownEndSaysLater() = runComposeUiTest {
        harness { LockedScreen(LockedState(refusal("error.422.blocked", 422)), {}) }
        onNodeWithText("Try again later.", substring = true, useUnmergedTree = true).assertExists()
    }
}
