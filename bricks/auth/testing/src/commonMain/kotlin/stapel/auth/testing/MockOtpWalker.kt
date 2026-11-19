package stapel.auth.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import stapel.auth.ui.AuthTestTags

/**
 * The test tags of the auth screens (`stapel-auth-ui`'s [AuthTestTags]), under the name an app's tests
 * use: `AuthTags.SIGN_IN`, `AuthTags.CODE_FIELD`, …
 */
public typealias AuthTags = AuthTestTags

/** The one-time code a stapel-auth backend in prototype posture accepts by default (mock delivery). */
public const val PROTOTYPE_MOCK_CODE: String = "0000"

/** Why a walk could not finish, with the step it stopped at. */
public class AuthWalkException(message: String) : AssertionError(message)

/**
 * Signs in through the real sign-in screen with a one-time code, the way a person would: types
 * [identifier] (an e-mail address or phone number for the method tab that is selected, by default the
 * first one the server offers) into the identifier field, sends the code, checks that the backend says
 * the delivery was mocked (prototype posture: no code was really sent), enters [code], and waits until
 * the sign-in screen is gone.
 *
 * Runs against a real backend (a scaffolded stapel-auth in prototype posture); it never fakes a
 * response. A backend in live posture fails the walk at the mock-hint step instead of waiting for a
 * code that a person would receive.
 *
 * @param requireMockHint false skips the prototype check (a backend whose fixed code you configured).
 * @param timeoutMillis budget per step.
 * @throws AuthWalkException naming the step that did not happen.
 */
@ExperimentalTestApi
public fun ComposeUiTest.signInWithMockOtp(
    identifier: String,
    code: String = PROTOTYPE_MOCK_CODE,
    requireMockHint: Boolean = true,
    timeoutMillis: Long = 20_000,
) {
    step("the sign-in screen shows the identifier field", timeoutMillis) { exists(hasTestTag(AuthTags.IDENTIFIER)) }
    onNode(hasTestTag(AuthTags.IDENTIFIER) and hasSetTextAction()).performTextInput(identifier)
    onNode(hasTestTag(AuthTags.SEND_CODE)).performClick()
    step("the code step appears after sending the code", timeoutMillis) {
        exists(hasTestTag(AuthTags.CODE_FIELD)) || exists(hasTestTag(AuthTags.FORM_ERROR))
    }
    if (!exists(hasTestTag(AuthTags.CODE_FIELD))) throw AuthWalkException("the backend refused to send a code (form error shown)")
    if (requireMockHint && !exists(hasTestTag(AuthTags.MOCK_HINT))) {
        throw AuthWalkException("no mock-delivery hint: the backend is not in prototype posture, the code was really sent")
    }
    onNode(hasTestTag(AuthTags.CODE_FIELD) and hasSetTextAction()).performTextInput(code)
    step("the sign-in screen closes after the code", timeoutMillis) {
        !exists(hasTestTag(AuthTags.SIGN_IN)) || exists(hasTestTag(AuthTags.CODE_ERROR))
    }
    if (exists(hasTestTag(AuthTags.CODE_ERROR))) throw AuthWalkException("the backend rejected the code '$code'")
}

/** Waits until [tag] is on screen. */
@ExperimentalTestApi
public fun ComposeUiTest.awaitTag(tag: String, timeoutMillis: Long = 20_000) {
    step("'$tag' appears", timeoutMillis) { exists(hasTestTag(tag)) }
}

@ExperimentalTestApi
private fun ComposeUiTest.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

@ExperimentalTestApi
private fun ComposeUiTest.step(name: String, timeoutMillis: Long, done: () -> Boolean) {
    try {
        waitUntil(timeoutMillis = timeoutMillis, condition = done)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AuthWalkException("sign-in walk stopped: $name did not happen within $timeoutMillis ms (${e.message})")
    }
}
