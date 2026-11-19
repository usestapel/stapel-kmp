package stapel.auth.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ThreeMethods = MethodZones(main = listOf(SignInMethod.Email, SignInMethod.Phone, SignInMethod.Password))

@OptIn(ExperimentalTestApi::class)
class SignInScreenTest {
    private fun editable(tag: String) = hasTestTag(tag) and hasSetTextAction()

    @Test
    fun tabsFollowServerOrderAndSelectingEmitsTheMethod() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { SignInScreen(SignInState(ThreeMethods), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithTag(AuthTestTags.METHOD_TABS).assertExists()
            onNodeWithText("Phone").performClick()
            onNodeWithText("Password").performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.SelectMethod(SignInMethod.Phone), SignInEvent.SelectMethod(SignInMethod.Password)), events.all)
        }
    }

    @Test
    fun sendCodeIsDisabledUntilSomethingIsTypedThenRequestsTheCode() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { SignInScreen(SignInState(ThreeMethods), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithTag(AuthTestTags.SEND_CODE).assertIsNotEnabled()
            onNode(editable(AuthTestTags.IDENTIFIER)).performTextInput(" a@example.com ")
            onNodeWithTag(AuthTestTags.SEND_CODE).assertIsEnabled().performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.RequestCode(SignInMethod.Email, "a@example.com")), events.all)
        }
    }

    @Test
    fun requestErrorIsLocalizedAndCarriedByTheField() = runComposeUiTest {
        val state = SignInState(
            MethodZones(main = listOf(SignInMethod.Phone)),
            step = SignInStep.Form(error = refusal("error.400.invalid_phone_format", 400), identifier = "+1"),
        )
        val h = harness { SignInScreen(state, {}) }
        eachSkin(h) {
            onAllNodes(editable(AuthTestTags.IDENTIFIER) and hasErrorSemantics).fetchSemanticsNodes().let { assertEquals(1, it.size) }
            onNode(fieldError(AuthTestTags.IDENTIFIER, "Invalid phone number format")).assertExists()
        }
        h.locale = "ru"
        waitForIdle()
        onNode(fieldError(AuthTestTags.IDENTIFIER, "Неверный формат номера телефона")).assertExists()
        onNodeWithTag(AuthTestTags.TITLE).assertTextEquals("Вход")
    }

    @Test
    fun passwordFormSubmitsCredentialsAndShowsTheRefusal() = runComposeUiTest {
        val events = Events<SignInEvent>()
        var state by mutableStateOf(SignInState(ThreeMethods, selected = SignInMethod.Password))
        val h = harness { SignInScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithTag(AuthTestTags.SUBMIT).assertIsNotEnabled()
            onNode(editable(AuthTestTags.LOGIN)).performTextInput("user@example.com")
            onNode(editable(AuthTestTags.PASSWORD)).performTextInput("s3cret")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.SignInWithPassword("user@example.com", "s3cret")), events.all)
        }
        state = state.copy(step = SignInStep.Form(error = refusal("error.401.invalid_credentials", 401)))
        waitForIdle()
        onNodeWithTag(AuthTestTags.FORM_ERROR, useUnmergedTree = true).assertExists()
        onNodeWithText("Invalid credentials", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun registrationRefusesMismatchedPasswordsLocally() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val state = SignInState(MethodZones(main = listOf(SignInMethod.Password)), variant = SignInVariant.Register, canRegister = true)
        val h = harness { SignInScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithTag(AuthTestTags.TITLE).assertTextEquals("Create account")
            onNode(editable(AuthTestTags.LOGIN)).performTextInput("new@example.com")
            onNode(editable(AuthTestTags.PASSWORD)).performTextInput("one-password")
            onNode(editable(AuthTestTags.PASSWORD_CONFIRM)).performTextInput("other-password")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            onNode(fieldError(AuthTestTags.PASSWORD_CONFIRM, "Passwords don't match.")).assertExists()
            assertTrue(events.all.isEmpty())
        }
    }

    @Test
    fun registrationWithMatchingPasswordsEmitsRegister() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val state = SignInState(MethodZones(main = listOf(SignInMethod.Password)), variant = SignInVariant.Register, canRegister = true)
        harness { SignInScreen(state, events.sink) }
        onNode(editable(AuthTestTags.LOGIN)).performTextInput("new@example.com")
        onNode(editable(AuthTestTags.PASSWORD)).performTextInput("pw-123456")
        onNode(editable(AuthTestTags.PASSWORD_CONFIRM)).performTextInput("pw-123456")
        onNodeWithTag(AuthTestTags.SUBMIT).performClick()
        assertEquals(listOf<SignInEvent>(SignInEvent.RegisterWithPassword("new@example.com", "pw-123456")), events.all)
    }

    @Test
    fun guestEntryAndRegisterSwitchExistOnlyWhenOffered() = runComposeUiTest {
        val events = Events<SignInEvent>()
        var state by mutableStateOf(SignInState(ThreeMethods))
        val h = harness { SignInScreen(state, events.sink) }
        onNodeWithTag(AuthTestTags.GUEST).assertDoesNotExist()
        onNodeWithTag(AuthTestTags.VARIANT_SWITCH).assertDoesNotExist()
        state = state.copy(guest = GuestEntry(), canRegister = true)
        eachSkin(h) {
            events.clear()
            onNodeWithTag(AuthTestTags.GUEST).performClick()
            onNodeWithTag(AuthTestTags.VARIANT_SWITCH).performClick()
            assertEquals(listOf(SignInEvent.ContinueAsGuest, SignInEvent.SwitchVariant(SignInVariant.Register)), events.all)
        }
    }

    @Test
    fun revokedNoticeIsShownAndDismissible() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val h = harness { SignInScreen(SignInState(ThreeMethods, notice = AuthNotice.SignedOutElsewhere), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("You were signed out on another device.").assertExists()
            onNode(hasClickAction() and (hasText("Dismiss") or hasContentDescription("Dismiss")), useUnmergedTree = true).performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.DismissNotice), events.all)
        }
    }

    @Test
    fun failedCapabilitiesOfferRetryAndNoMethodsIsNamed() = runComposeUiTest {
        val events = Events<SignInEvent>()
        var state by mutableStateOf(SignInState(ThreeMethods, loadError = refusal("error.500.internal", 500)))
        val h = harness { SignInScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("Try again").performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.Retry), events.all)
        }
        state = SignInState(MethodZones(main = emptyList()))
        waitForIdle()
        onNodeWithText("No sign-in method is available", substring = true).assertExists()
        onNodeWithTag(AuthTestTags.SEND_CODE).assertDoesNotExist()
    }

    @Test
    fun overflowMethodsOpenFromMoreWays() = runComposeUiTest {
        val events = Events<SignInEvent>()
        val zones = MethodZones(main = listOf(SignInMethod.Email), bottom = listOf(SignInMethod.Phone), overflow = listOf(SignInMethod.Password))
        val h = harness { SignInScreen(SignInState(zones), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithTag("${AuthTestTags.METHOD_BUTTON}.phone").performClick()
            onNodeWithTag(AuthTestTags.MORE_METHODS).performClick()
            onNodeWithTag("${AuthTestTags.METHOD_BUTTON}.password").performClick()
            assertEquals(listOf<SignInEvent>(SignInEvent.SelectMethod(SignInMethod.Phone), SignInEvent.SelectMethod(SignInMethod.Password)), events.all)
            onNodeWithTag(AuthTestTags.METHOD_TABS).assertDoesNotExist()
        }
    }

    @Test
    fun stepsReplaceTheForm() = runComposeUiTest {
        var step by androidx.compose.runtime.mutableStateOf<SignInStep>(SignInStep.Code(OtpCodeState(SignInMethod.Email, "a***@example.com", 4)))
        harness { SignInScreen(SignInState(ThreeMethods, step = step), {}) }
        onNodeWithTag(AuthTestTags.CODE_STEP).assertExists()
        onNodeWithTag(AuthTestTags.SEND_CODE).assertDoesNotExist()
        onNodeWithTag(AuthTestTags.GUEST).assertDoesNotExist()
        step = SignInStep.Totp(TotpChallengeState())
        waitForIdle()
        onNodeWithTag(AuthTestTags.TOTP_STEP).assertExists()
        step = SignInStep.ForcedPasswordChange(ForcedPasswordChangeState())
        waitForIdle()
        onNodeWithTag(AuthTestTags.FORCED_CHANGE).assertExists()
        step = SignInStep.Locked(LockedState(refusal("error.423.account_locked", 423, "retry_after_minutes" to "5"), kotlin.time.Duration.parse("5m")))
        waitForIdle()
        onNodeWithTag(AuthTestTags.LOCKED).assertExists()
        step = SignInStep.MfaEnrollRequired
        waitForIdle()
        onNodeWithText("Set it up on the web", substring = true).assertExists()
        onNodeWithTag(AuthTestTags.BACK_TO_FORM).assertExists()
    }
}
