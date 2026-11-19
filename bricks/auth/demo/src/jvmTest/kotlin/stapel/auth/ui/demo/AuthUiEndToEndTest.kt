package stapel.auth.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import stapel.auth.api.model.PasswordOtpMethod
import stapel.auth.api.model.PasswordOtpRequest
import stapel.auth.api.model.PasswordOtpVerify
import stapel.auth.model.OtpChannel
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.session.AuthState
import stapel.auth.ui.AuthTestTags
import stapel.mvi.PresenterContext
import stapel.result.StapelResult
import stapel.tokens.ThemeMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The UI end-to-end suite (ARCH-AUTH §9.3): the real composition — the auth screens bound to the
 * domain presenters, the navigation host, the real transport and session — against the auth stand
 * (`bricks/auth/e2e/stand/stand.sh up`). Run with `-Pstapel.e2e.baseUrl=http://127.0.0.1:18731/`;
 * skipped without it. No fake below the UI.
 */
@OptIn(ExperimentalTestApi::class)
class AuthUiEndToEndTest {
    private val baseUrl: String? = System.getProperty("stapel.e2e.baseUrl")
    private fun email() = "e2e-ui+${UUID.randomUUID()}@example.com"

    private fun stand(): String {
        assumeTrue("needs -Pstapel.e2e.baseUrl (bricks/auth/e2e/stand/stand.sh up)", baseUrl != null)
        return baseUrl!!
    }

    private var navigator: stapel.navigation.StapelNavigator? = null

    private fun ComposeUiTest.app(stack: DemoStack, locale: String = "en") {
        setContent { DemoTheme(DemoSkin.Material3, locale, ThemeMode.Light) { LiveApp(stack) { navigator = it } } }
    }

    private fun ComposeUiTest.awaitTag(tag: String, timeoutMs: Long = 20_000) =
        waitUntil(timeoutMillis = timeoutMs) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.awaitText(text: String, timeoutMs: Long = 20_000) =
        waitUntil(timeoutMillis = timeoutMs) { onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.signInByEmailOtp(address: String) {
        awaitTag(AuthTestTags.IDENTIFIER)
        onNode(hasTestTag(AuthTestTags.IDENTIFIER) and hasSetTextAction()).performTextInput(address)
        onNodeWithTag(AuthTestTags.SEND_CODE).performClick()
        awaitTag(AuthTestTags.CODE_FIELD)
        onNode(hasTestTag(AuthTestTags.CODE_FIELD) and hasSetTextAction()).performTextInput(MOCK_CODE)
    }

    /** Signs a second, headless installation in by OTP (the domain flow, no UI). */
    private fun headlessSignIn(stack: DemoStack, address: String) = runBlocking {
        stack.auth.session.restore()
        val p = OtpSignInPresenter(stack.auth, PresenterContext(stack.scope))
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, address))
        var s = withTimeout(20.seconds) { p.state.first { it is OtpSignInState.CodeSent || it is OtpSignInState.RequestFailed || it is OtpSignInState.Locked } }
        if (s is OtpSignInState.Locked) { // the stand's 2 s per-identifier cooldown
            Thread.sleep(2_500)
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, address))
            s = withTimeout(20.seconds) { p.state.first { it is OtpSignInState.CodeSent } }
        }
        p.dispatch(OtpSignInIntent.SubmitCode(MOCK_CODE))
        withTimeout(20.seconds) { p.state.first { it is OtpSignInState.Done } }
    }

    @Test
    fun u1_signedOutShowsExactlyTheCompletableMethodsInServerOrder() = runComposeUiTest {
        DemoStack(stand(), "u1").use { stack ->
            app(stack)
            awaitTag(AuthTestTags.METHOD_TABS)
            // Server: email (main 0), phone (main 1), password (overflow 7); passkey/qr/sso/oauth/magic link are not completable.
            val tabs = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].joinToString() }
            assertEquals(listOf("Email", "Phone"), tabs)
            onNodeWithTag(AuthTestTags.MORE_METHODS).assertExists()
            for (absent in listOf("Use a passkey", "Continue with SSO", "Email me a sign-in link")) onNodeWithText(absent).assertDoesNotExist()
            onNodeWithTag(AuthTestTags.MORE_METHODS).performClick()
            onNodeWithTag("${AuthTestTags.METHOD_BUTTON}.password").assertExists()
        }
    }

    @Test
    fun u2_u5_emailOtpSignsInThenSignOutLeavesOnlySignIn() = runComposeUiTest {
        DemoStack(stand(), "u2").use { stack ->
            app(stack)
            val address = email()
            awaitTag(AuthTestTags.IDENTIFIER)
            onNode(hasTestTag(AuthTestTags.IDENTIFIER) and hasSetTextAction()).performTextInput(address)
            onNodeWithTag(AuthTestTags.SEND_CODE).performClick()
            awaitTag(AuthTestTags.CODE_FIELD)
            val sentTo = onAllNodes(hasText("Code sent to", substring = true)).fetchSemanticsNodes().single()
                .config[SemanticsProperties.Text].joinToString()
            assertTrue('*' in sentTo && address !in sentTo, "masked target: $sentTo")
            onNodeWithTag(AuthTestTags.MOCK_HINT, useUnmergedTree = true).assertExists()
            onNode(hasTestTag(AuthTestTags.CODE_FIELD) and hasSetTextAction()).performTextInput(MOCK_CODE)
            awaitTag(DemoTags.HOME)
            awaitText(address)
            onNodeWithTag(AuthTestTags.SIGN_IN).assertDoesNotExist()
            assertTrue(stack.auth.session.state.value is AuthState.SignedIn)

            // U-5: sign out → the sign-in screen is the only destination; back does not return.
            onNodeWithTag(DemoTags.SIGN_OUT).performClick()
            awaitTag(AuthTestTags.SIGN_IN)
            onNodeWithTag(DemoTags.HOME).assertDoesNotExist()
            onNodeWithText("You have signed out.").assertExists()
        }
    }

    @Test
    fun u3_wrongCodeShowsTheLocalizedTypedErrorAndClearsTheField() = runComposeUiTest {
        DemoStack(stand(), "u3").use { stack ->
            app(stack, locale = "ru")
            awaitTag(AuthTestTags.IDENTIFIER)
            onNode(hasTestTag(AuthTestTags.IDENTIFIER) and hasSetTextAction()).performTextInput(email())
            onNodeWithTag(AuthTestTags.SEND_CODE).performClick()
            awaitTag(AuthTestTags.CODE_FIELD)
            onNode(hasTestTag(AuthTestTags.CODE_FIELD) and hasSetTextAction()).performTextInput("1111")
            val field = hasTestTag(AuthTestTags.CODE_FIELD) and hasSetTextAction()
            waitUntil(timeoutMillis = 20_000) {
                onAllNodes(field and SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).fetchSemanticsNodes().isNotEmpty()
            }
            val error = onNode(field).fetchSemanticsNode().config[SemanticsProperties.Error]
            assertTrue(Regex("""Неверный код подтверждения\. Осталось попыток: \d+\.""").matches(error), "localized typed error: $error")
            assertEquals("", onNode(field).fetchSemanticsNode().config[SemanticsProperties.EditableText].text, "the field is cleared")
            onNodeWithTag(DemoTags.HOME).assertDoesNotExist()
            assertTrue(stack.auth.session.state.value is AuthState.SignedOut)
        }
    }

    @Test
    fun u4_passwordSignInAndAWrongPassword() = runComposeUiTest {
        val url = stand()
        val address = email()
        val password = "e2e-" + UUID.randomUUID()
        DemoStack(url, "u4-setup").use { setup ->
            headlessSignIn(setup, address)
            Thread.sleep(2_500)
            runBlocking {
                assertTrue(setup.api.passwordChangeOtpRequest(PasswordOtpRequest(PasswordOtpMethod.Email)) is StapelResult.Ok)
                val set = setup.api.passwordChangeOtpVerify(PasswordOtpVerify(PasswordOtpMethod.Email, MOCK_CODE, password))
                assertTrue(set is StapelResult.Ok, "password set by OTP: $set")
            }
        }
        DemoStack(url, "u4").use { stack ->
            app(stack)
            awaitTag(AuthTestTags.MORE_METHODS)
            onNodeWithTag(AuthTestTags.MORE_METHODS).performClick()
            onNodeWithTag("${AuthTestTags.METHOD_BUTTON}.password").performClick()
            awaitTag(AuthTestTags.LOGIN)
            onNode(hasTestTag(AuthTestTags.LOGIN) and hasSetTextAction()).performTextInput(address)
            onNode(hasTestTag(AuthTestTags.PASSWORD) and hasSetTextAction()).performTextInput("definitely-wrong-1")
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            awaitText("Invalid credentials")
            onNodeWithTag(DemoTags.HOME).assertDoesNotExist()
            onNode(hasTestTag(AuthTestTags.PASSWORD) and hasSetTextAction()).performTextClearance()
            onNode(hasTestTag(AuthTestTags.PASSWORD) and hasSetTextAction()).performTextInput(password)
            onNodeWithTag(AuthTestTags.SUBMIT).performClick()
            awaitTag(DemoTags.HOME)
            awaitText(address)
        }
    }

    @Test
    fun u6_reloadAfterAccessTokenExpiryStaysSignedIn() = runComposeUiTest {
        DemoStack(stand(), "u6").use { stack ->
            app(stack)
            val address = email()
            signInByEmailOtp(address)
            awaitTag(DemoTags.HOME)
            Thread.sleep(6_500) // the stand's access token lives 5 s
            var sawSignIn by mutableStateOf(false)
            onNodeWithTag(DemoTags.RELOAD).performClick()
            waitUntil(timeoutMillis = 20_000) {
                if (onAllNodes(hasTestTag(AuthTestTags.SIGN_IN)).fetchSemanticsNodes().isNotEmpty()) sawSignIn = true
                onAllNodes(hasText("reloads: 1")).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(!sawSignIn, "the sign-in screen appeared during the refresh")
            onNodeWithTag(DemoTags.PROFILE_ERROR).assertDoesNotExist()
            assertTrue(stack.auth.session.state.value is AuthState.SignedIn, "still signed in")
        }
    }

    @Test
    fun u7_revokedOnAProtectedScreenLandsOnSignInWithTheReasonAndReturnsAfterSignIn() = runComposeUiTest {
        val url = stand()
        val address = email()
        DemoStack(url, "u7").use { stack ->
            app(stack)
            signInByEmailOtp(address)
            awaitTag(DemoTags.HOME)
            onNodeWithTag(DemoTags.SECURITY).performClick()
            awaitTag(AuthTestTags.SESSIONS)
            // A second installation of the same user signs every other session out, this one included.
            Thread.sleep(2_500)
            DemoStack(url, "u7-other").use { other ->
                headlessSignIn(other, address)
                runBlocking { assertTrue(other.auth.devices.revokeOthers() is StapelResult.Ok) }
            }
            Thread.sleep(6_500) // past this device's access token
            onNodeWithTag(AuthTestTags.SESSIONS_REFRESH).performClick()
            awaitTag(AuthTestTags.SIGN_IN)
            awaitText("You were signed out on another device.")
            onNodeWithTag(AuthTestTags.SESSIONS).assertDoesNotExist()
            assertTrue(stack.navBridge.returnTo.value != null, "the protected screen is remembered")

            Thread.sleep(2_500)
            signInByEmailOtp(address)
            // Back where the session ended: the security screen, not the home.
            awaitTag(AuthTestTags.SESSIONS)
            onNodeWithTag(AuthTestTags.SIGN_IN).assertDoesNotExist()
        }
    }

    @Test
    fun u8_sessionsMarkThisDeviceAndSignOutEveryoneElseSparesIt() = runComposeUiTest {
        val url = stand()
        val address = email()
        DemoStack(url, "u8").use { stack ->
            app(stack)
            signInByEmailOtp(address)
            awaitTag(DemoTags.HOME)
            Thread.sleep(2_500) // the stand's per-identifier cooldown
            DemoStack(url, "u8-other").use { other ->
                headlessSignIn(other, address)
                val mine = runBlocking { (stack.auth.devices.load() as StapelResult.Ok).value }
                val current = mine.single { it.isCurrent }
                val theirs = mine.single { !it.isCurrent }

                onNodeWithTag(DemoTags.SECURITY).performClick()
                awaitTag(AuthTestTags.SESSIONS)
                onNodeWithTag(AuthTestTags.SESSIONS_REFRESH).performClick()
                awaitTag("${AuthTestTags.SESSION_ROW}.${theirs.id}")
                val marker = hasText("This device", substring = true)
                val currentRow = hasTestTag("${AuthTestTags.SESSION_ROW}.${current.id}")
                val otherRow = hasTestTag("${AuthTestTags.SESSION_ROW}.${theirs.id}")
                onNode(currentRow and hasAnyDescendant(marker), useUnmergedTree = true).assertExists()
                onNode(otherRow and hasAnyDescendant(marker), useUnmergedTree = true).assertDoesNotExist()
                assertEquals(1, onAllNodes(marker, useUnmergedTree = true).fetchSemanticsNodes().size, "one row is this device")
                // This device has no "Sign out" of its own; the other one has.
                onNode(currentRow and hasAnyDescendant(hasText("Sign out")), useUnmergedTree = true).assertDoesNotExist()
                onNode(otherRow and hasAnyDescendant(hasText("Sign out")), useUnmergedTree = true).assertExists()

                onNodeWithTag(AuthTestTags.SIGN_OUT_OTHERS).performClick()
                awaitTag(AuthTestTags.REVOKE_CONFIRM)
                onNode(
                    hasText("Sign out everyone else") and hasAnyAncestor(hasTestTag(AuthTestTags.REVOKE_CONFIRM)),
                    useUnmergedTree = true,
                ).performClick()
                waitUntil(timeoutMillis = 20_000) { onAllNodes(otherRow).fetchSemanticsNodes().isEmpty() }
                onNode(currentRow).assertExists()
                onNodeWithTag(AuthTestTags.SIGN_OUT_OTHERS).assertDoesNotExist()
                assertTrue(stack.auth.session.state.value is AuthState.SignedIn, "this device stays signed in")
                runBlocking {
                    val left = (stack.auth.devices.load() as StapelResult.Ok).value
                    assertEquals(listOf(current.id), left.map { it.id }, "the server keeps this device only")
                }
            }
        }
    }

    private fun requestCodeHeadless(stack: DemoStack, address: String) = runBlocking {
        stack.auth.session.restore()
        val p = OtpSignInPresenter(stack.auth, PresenterContext(stack.scope))
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, address))
        withTimeout(20.seconds) { p.state.first { it is OtpSignInState.CodeSent } }
    }

    private fun ComposeUiTest.rateLimitedRequestShowsTheWait(locale: String, wait: Regex, title: String) {
        val url = stand()
        val address = email()
        DemoStack(url, "u9-$locale-other").use { other ->
            DemoStack(url, "u9-$locale").use { stack ->
                app(stack, locale = locale)
                awaitTag(AuthTestTags.IDENTIFIER)
                onNode(hasTestTag(AuthTestTags.IDENTIFIER) and hasSetTextAction()).performTextInput(address)
                requestCodeHeadless(other, address) // starts the server's 2 s cooldown for this address
                onNodeWithTag(AuthTestTags.SEND_CODE).performClick()
                awaitTag(AuthTestTags.LOCKED)
                onNodeWithText(title).assertExists()
                val text = onNodeWithTag(AuthTestTags.LOCKED_WAIT, useUnmergedTree = true).fetchSemanticsNode()
                    .config[SemanticsProperties.Text].joinToString()
                assertTrue(wait.matches(text), "wait text: $text")
                onNodeWithTag(AuthTestTags.BACK_TO_FORM).performClick()
                awaitTag(AuthTestTags.IDENTIFIER)
                assertEquals(address, onNode(hasTestTag(AuthTestTags.IDENTIFIER) and hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            }
        }
    }

    @Test
    fun u9_rateLimitedCodeRequestShowsTheLocalizedWaitAndGoesBack_en() = runComposeUiTest {
        rateLimitedRequestShowsTheWait("en", Regex("""Try again in [12] seconds?\."""), "Sign-in is paused")
    }

    @Test
    fun u9_rateLimitedCodeRequestShowsTheLocalizedWaitAndGoesBack_ru() = runComposeUiTest {
        rateLimitedRequestShowsTheWait("ru", Regex("""Попробуйте через [12] секунд[уы]\."""), "Вход временно недоступен")
    }

    private companion object {
        const val MOCK_CODE = "0000"
    }
}

