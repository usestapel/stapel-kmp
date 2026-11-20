package stapel.profiles.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import stapel.profiles.ui.ProfilesTestTags

/** The test tags of the profiles screens (`stapel-profiles-ui`'s [ProfilesTestTags]), under the name an app's tests use. */
public typealias ProfilesTags = ProfilesTestTags

/** Why a walk could not finish, with the step it stopped at. */
public class ProfilesWalkException(message: String) : AssertionError(message)

/**
 * On the settings screen, opens the section [destinationId] (`profiles.language`, `auth.security`, …)
 * and waits until a screen tagged [expectTag] is shown.
 */
@ExperimentalTestApi
public fun ComposeUiTest.openSettingsSection(destinationId: String, expectTag: String, timeoutMillis: Long = 20_000) {
    val row = "${ProfilesTags.SECTION}.$destinationId"
    step("the settings screen lists '$destinationId'", timeoutMillis) { exists(hasTestTag(row)) }
    onNode(hasTestTag(row)).performScrollTo().performClick()
    step("'$expectTag' opens from settings", timeoutMillis) { exists(hasTestTag(expectTag)) }
}

/** On the settings screen, replaces the display name with [name] and saves it; waits until the save settles. */
@ExperimentalTestApi
public fun ComposeUiTest.renameInSettings(name: String, timeoutMillis: Long = 20_000) {
    step("the settings screen shows the display name", timeoutMillis) { exists(hasTestTag(ProfilesTags.DISPLAY_NAME)) }
    val field = onNode(hasTestTag(ProfilesTags.DISPLAY_NAME) and hasSetTextAction())
    field.performTextClearance()
    field.performTextInput(name)
    onNode(hasTestTag(ProfilesTags.DISPLAY_NAME_SAVE)).performClick()
    step("the name '$name' is stored (save button idle again)", timeoutMillis) {
        onAllNodes(hasTestTag(ProfilesTags.DISPLAY_NAME_SAVE) and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
            .fetchSemanticsNodes().isNotEmpty() || exists(hasTestTag(ProfilesTags.ERROR))
    }
    if (exists(hasTestTag(ProfilesTags.ERROR))) throw ProfilesWalkException("the backend refused the name '$name'")
}

/** On the language screen, chooses [code] (or the device language for null). */
@ExperimentalTestApi
public fun ComposeUiTest.chooseLanguage(code: String?, timeoutMillis: Long = 20_000) {
    val tag = "${ProfilesTags.LANGUAGE_CHOICE}.${code ?: "device"}"
    step("the language '$tag' is offered", timeoutMillis) { exists(hasTestTag(tag)) }
    onNode(hasTestTag(tag)).performScrollTo().performClick()
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
    } catch (e: Throwable) {
        // ComposeTimeoutException is a Throwable, not an Exception.
        throw ProfilesWalkException("profiles walk stopped: $name did not happen within $timeoutMillis ms (${e.message})")
    }
}
