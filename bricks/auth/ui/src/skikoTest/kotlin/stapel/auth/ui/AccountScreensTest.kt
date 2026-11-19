package stapel.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import stapel.result.state.Freshness
import stapel.result.state.Resource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Now = Instant.parse("2026-11-19T12:00:00Z")

fun sessionRows(): List<SessionRowState> = listOf(
    SessionRowState("s1", "Desktop app", "macOS", "127.0.0.1", Now - 1.minutes, isCurrent = true, isSuspicious = false, kind = SessionDeviceKind.Desktop),
    SessionRowState("s2", "iOS app", "iPhone", null, Now - 3.hours, isCurrent = false, isSuspicious = true, kind = SessionDeviceKind.Phone),
    SessionRowState("s3", "Android app", "Pixel", null, Now - 50.hours, isCurrent = false, isSuspicious = false, kind = SessionDeviceKind.Phone),
)

fun readySessions(rows: List<SessionRowState> = sessionRows()): Resource<List<SessionRowState>> =
    Resource.Ready(rows, Freshness.network(0))

@OptIn(ExperimentalTestApi::class)
class AccountScreensTest {
    private fun inRow(id: String, text: String) = hasText(text) and hasAnyAncestor(hasTestTag("${AuthTestTags.SESSION_ROW}.$id"))

    @Test
    fun guestWallOffersSignInRegisterAndNotNow() = runComposeUiTest {
        val events = Events<GuestUpgradeEvent>()
        var state by mutableStateOf(GuestUpgradeState())
        val h = harness { GuestUpgradeScreen(state, events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("Create an account to continue").assertExists()
            onNodeWithText("Sign in").performClick()
            onNodeWithText("Create account").performClick()
            onNodeWithText("Not now").performClick()
            assertEquals(listOf(GuestUpgradeEvent.SignIn, GuestUpgradeEvent.Register, GuestUpgradeEvent.NotNow), events.all)
        }
        state = GuestUpgradeState(canDismiss = false)
        waitForIdle()
        onNodeWithText("Not now").assertDoesNotExist()
    }

    @Test
    fun sessionsShowMarkersAndOfferTheRightActionsPerRow() = runComposeUiTest {
        val events = Events<SessionsEvent>()
        val h = harness { SessionsScreen(SessionsState(readySessions(), Now), events.sink) }
        eachSkin(h) {
            events.clear()
            onNode(hasText("This device", substring = true) and hasAnyAncestor(hasTestTag("${AuthTestTags.SESSION_ROW}.s1")), useUnmergedTree = true).assertExists()
            onNode(inRow("s1", "Sign out"), useUnmergedTree = true).assertDoesNotExist()
            onNode(hasText("Unrecognized sign-in", substring = true) and hasAnyAncestor(hasTestTag("${AuthTestTags.SESSION_ROW}.s2")), useUnmergedTree = true).assertExists()
            onNode(hasText("3 hours ago", substring = true) and hasAnyAncestor(hasTestTag("${AuthTestTags.SESSION_ROW}.s2")), useUnmergedTree = true).assertExists()
            onNode(hasText("2 days ago", substring = true) and hasAnyAncestor(hasTestTag("${AuthTestTags.SESSION_ROW}.s3")), useUnmergedTree = true).assertExists()
            onNode(inRow("s2", "This was me"), useUnmergedTree = true).performClick()
            onNode(inRow("s3", "Sign out"), useUnmergedTree = true).performClick()
            onNode(inRow("s3", "This was me"), useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag(AuthTestTags.SIGN_OUT_OTHERS).performClick()
            assertEquals(listOf(SessionsEvent.ConfirmMine("s2"), SessionsEvent.AskRevoke("s3"), SessionsEvent.AskRevokeOthers), events.all)
        }
    }

    @Test
    fun revokeAsksForConfirmation() = runComposeUiTest {
        val events = Events<SessionsEvent>()
        val h = harness { SessionsScreen(SessionsState(readySessions(), Now, confirmRevoke = "s3"), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("Sign out this device?").assertExists()
            onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag(AuthTestTags.REVOKE_CONFIRM)), useUnmergedTree = true).performClick()
            onNode(hasText("Sign out") and hasAnyAncestor(hasTestTag(AuthTestTags.REVOKE_CONFIRM)), useUnmergedTree = true).performClick()
            assertEquals(listOf(SessionsEvent.CancelRevoke, SessionsEvent.ConfirmRevoke), events.all)
        }
    }

    @Test
    fun onlyTheCurrentSessionMeansNoSignOutEveryoneElse() = runComposeUiTest {
        harness { SessionsScreen(SessionsState(readySessions(sessionRows().take(1)), Now), {}) }
        onNodeWithTag(AuthTestTags.SIGN_OUT_OTHERS).assertDoesNotExist()
    }

    @Test
    fun sessionsEmptyFailedAndRefresh() = runComposeUiTest {
        val events = Events<SessionsEvent>()
        var sessions by mutableStateOf<Resource<List<SessionRowState>>>(readySessions(emptyList()))
        harness { SessionsScreen(SessionsState(sessions, Now), events.sink) }
        onNodeWithText("No active sessions.").assertExists()
        sessions = Resource.Failed(refusal("error.500.internal", 500))
        waitForIdle()
        onNodeWithText("Try again").performClick()
        onNodeWithTag(AuthTestTags.SESSIONS_REFRESH).performClick()
        assertEquals(listOf<SessionsEvent>(SessionsEvent.Refresh, SessionsEvent.Refresh), events.all)
    }

    @Test
    fun signOutConfirmationTellsAGuestWhatIsLost() = runComposeUiTest {
        val events = Events<SignOutEvent>()
        var guest by mutableStateOf(false)
        val h = harness { SignOutConfirmDialog(SignOutConfirmState(isGuest = guest), events.sink) }
        eachSkin(h) {
            events.clear()
            onNodeWithText("You'll need to sign in again", substring = true).assertExists()
            onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag(AuthTestTags.SIGN_OUT_CONFIRM)), useUnmergedTree = true).performClick()
            onNode(hasText("Sign out") and hasAnyAncestor(hasTestTag(AuthTestTags.SIGN_OUT_CONFIRM)), useUnmergedTree = true).performClick()
            assertEquals(listOf(SignOutEvent.Cancel, SignOutEvent.Confirm), events.all)
        }
        guest = true
        waitForIdle()
        onNodeWithText("signed in as a guest", substring = true).assertExists()
    }

    @Test
    fun accountMenuIsNamedAndSignsOutOnlyAfterConfirmation() = runComposeUiTest {
        var signedOut = 0
        var opened = 0
        val h = harness { AccountMenu("ann@example.com", isGuest = false, onSignOut = { signedOut++ }, onOpen = { opened++ }) }
        eachSkin(h) {
            signedOut = 0
            onNodeWithTag(AuthTestTags.ACCOUNT_MENU).assertContentDescriptionEquals("Account menu, signed in as ann@example.com")
            onNodeWithTag(AuthTestTags.ACCOUNT_MENU).performClick()
            onNodeWithTag(AuthTestTags.ACCOUNT_MENU_SIGN_OUT).performClick()
            assertEquals(0, signedOut)
            onNode(hasText("Sign out") and hasAnyAncestor(hasTestTag(AuthTestTags.SIGN_OUT_CONFIRM)), useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(1, signedOut)
            onNodeWithTag(AuthTestTags.ACCOUNT_MENU).performClick()
            onNodeWithText("Account").performClick()
            assertEquals(1, opened)
            opened = 0
        }
    }
}
