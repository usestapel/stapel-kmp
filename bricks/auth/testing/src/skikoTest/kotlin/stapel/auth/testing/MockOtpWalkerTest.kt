package stapel.auth.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The walker against a stand-in screen carrying the auth tags (the real screens against a real backend
 * are walked by the sample app's SignInWalkTest): it finishes on the happy path and names the step it
 * stopped at when the backend is not in prototype posture or rejects the code.
 */
@OptIn(ExperimentalTestApi::class)
class MockOtpWalkerTest {
    private var submitted: String? = null
    private var codeEntered: String? = null

    @Composable
    private fun FakeSignIn(mockHint: Boolean, acceptedCode: String) {
        var step by remember { mutableStateOf(0) }
        var id by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        when (step) {
            0 -> Column(Modifier.testTag(AuthTags.SIGN_IN)) {
                BasicTextField(id, { id = it }, Modifier.testTag(AuthTags.IDENTIFIER))
                Box(Modifier.testTag(AuthTags.SEND_CODE).clickable { submitted = id; step = 1 }) { BasicText("send") }
            }
            1 -> Column(Modifier.testTag(AuthTags.SIGN_IN)) {
                if (mockHint) BasicText("mock", Modifier.testTag(AuthTags.MOCK_HINT))
                BasicTextField(code, {
                    code = it
                    codeEntered = it
                    step = if (it == acceptedCode) 2 else 3
                }, Modifier.testTag(AuthTags.CODE_FIELD))
            }
            2 -> BasicText("home", Modifier.testTag("home"))
            else -> Column(Modifier.testTag(AuthTags.SIGN_IN)) { BasicText("wrong", Modifier.testTag(AuthTags.CODE_ERROR)) }
        }
    }

    @Test
    fun theWalkTypesTheIdentifierAndTheMockCodeAndEndsSignedIn() = runComposeUiTest {
        setContent { FakeSignIn(mockHint = true, acceptedCode = PROTOTYPE_MOCK_CODE) }
        signInWithMockOtp("walker@example.com", timeoutMillis = 5_000)
        awaitTag("home", 5_000)
        assertEquals("walker@example.com", submitted)
        assertEquals(PROTOTYPE_MOCK_CODE, codeEntered)
    }

    @Test
    fun aLivePostureBackendStopsTheWalkAtTheMockHint() = runComposeUiTest {
        setContent { FakeSignIn(mockHint = false, acceptedCode = PROTOTYPE_MOCK_CODE) }
        val e = assertFailsWith<AuthWalkException> { signInWithMockOtp("a@example.com", timeoutMillis = 5_000) }
        assertTrue("prototype posture" in e.message.orEmpty(), e.message)
        assertEquals(null, codeEntered)
    }

    @Test
    fun aRejectedCodeIsNamed() = runComposeUiTest {
        setContent { FakeSignIn(mockHint = true, acceptedCode = "9999") }
        val e = assertFailsWith<AuthWalkException> { signInWithMockOtp("a@example.com", timeoutMillis = 5_000) }
        assertTrue("rejected the code" in e.message.orEmpty(), e.message)
    }

    @Test
    fun aStepThatNeverHappensIsNamedNotAnUntypedTimeout() = runComposeUiTest {
        setContent { BasicText("no sign-in here", Modifier.testTag("elsewhere")) }
        val e = assertFailsWith<AuthWalkException> { signInWithMockOtp("a@example.com", timeoutMillis = 300) }
        assertTrue("identifier field did not happen" in e.message.orEmpty(), e.message)
        val tag = assertFailsWith<AuthWalkException> { awaitTag("never.there", 300) }
        assertTrue("'never.there' appears did not happen" in tag.message.orEmpty(), tag.message)
    }
}
