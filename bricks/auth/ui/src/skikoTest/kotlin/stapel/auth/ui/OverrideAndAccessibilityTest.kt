package stapel.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import stapel.ui.StapelText
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.SkinExtensionKey
import stapel.ui.skin.StapelSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/** A project table replacing exactly one screen. */
private object CustomCodeScreens : AuthScreens by DefaultAuthScreens {
    @Composable
    override fun OtpCode(state: OtpCodeState, onEvent: (SignInEvent) -> Unit) {
        Box(Modifier.testTag("custom.code")) { StapelText("Custom code for ${state.maskedTarget}") }
    }
}

/** A project skin replacing one base slot and auth's session row. */
private object BrandSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        FoundationSkin.Button(ButtonSpec("BRAND ${spec.label}", spec.onClick, spec.variant, spec.size, spec.enabled, spec.loading, spec.gatedReason, spec.leadingIcon, spec.fillWidth, spec.testTag), modifier)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> extension(key: SkinExtensionKey<T>): T = if (key == AuthSkinKey) BrandAuthSkin as T else key.default
}

private object BrandAuthSkin : AuthSkin by DefaultAuthSkin {
    @Composable
    override fun SessionRow(row: SessionRowState, actions: SessionRowActions, modifier: Modifier) {
        Box(modifier.testTag("brand.row.${row.id}")) { StapelText("Brand row ${row.name}") }
    }
}

@OptIn(ExperimentalTestApi::class)
class OverrideAndAccessibilityTest {
    private val codeState = SignInState(
        MethodZones(main = listOf(SignInMethod.Email, SignInMethod.Phone, SignInMethod.Password)),
        step = SignInStep.Code(OtpCodeState(SignInMethod.Email, "a***@example.com", 4)),
    )

    @Test
    fun aScreenReplacedInTheTableIsDrawnInsideTheDefaultSignIn() = runComposeUiTest {
        harness {
            CompositionLocalProvider(LocalAuthScreens provides CustomCodeScreens) {
                LocalAuthScreens.current.SignIn(codeState, {}, AuthSlots())
            }
        }
        onNodeWithTag("custom.code").assertExists()
        onNodeWithText("Custom code for a***@example.com").assertExists()
        onNodeWithTag(AuthTestTags.CODE_STEP).assertDoesNotExist()
        // The rest of the screen is still the default.
        onNodeWithTag(AuthTestTags.TITLE).assertExists()
    }

    @Test
    fun withoutTheOverrideTheDefaultStepIsDrawn() = runComposeUiTest {
        harness { SignInScreen(codeState, {}) }
        onNodeWithTag("custom.code").assertDoesNotExist()
        onNodeWithTag(AuthTestTags.CODE_STEP).assertExists()
    }

    @Test
    fun aSkinDelegationRestylesAuthButtonsAndAuthOwnWidgets() = runComposeUiTest {
        val h = harness { SessionsScreen(SessionsState(readySessions(), Instant.parse("2026-11-19T12:00:00Z")), {}) }
        h.customSkin = BrandSkin
        waitForIdle()
        onNodeWithText("BRAND Sign out everyone else").assertExists()
        onNodeWithText("Brand row iOS app").assertExists()
        onNodeWithText("This was me").assertDoesNotExist()
        h.customSkin = null
        waitForIdle()
        onNodeWithText("Sign out everyone else").assertExists()
        onNodeWithText("Brand row iOS app").assertDoesNotExist()
        onNodeWithText("This was me").assertExists()
    }

    @Test
    fun everyActionableNodeIsNamedAndFieldsAreSingleEditableNodes() = runComposeUiTest {
        val h = harness()
        val screens: List<@Composable () -> Unit> = listOf(
            { SignInScreen(SignInState(codeState.zones, guest = GuestEntry(), canRegister = true, notice = AuthNotice.SessionExpired), {}) },
            { SignInScreen(SignInState(codeState.zones, selected = SignInMethod.Password), {}) },
            { OtpCodeScreen(OtpCodeState(SignInMethod.Phone, "+1 *** 0100", 6, isMock = true), {}) },
            { TotpChallengeScreen(TotpChallengeState(), {}) },
            { ForcedPasswordChangeScreen(ForcedPasswordChangeState(), {}) },
            { GuestUpgradeScreen(GuestUpgradeState(), {}) },
            { SessionsScreen(SessionsState(readySessions(), Instant.parse("2026-11-19T12:00:00Z")), {}) },
            { AccountMenu("ann@example.com", false, {}) },
        )
        eachSkin(h) { skin ->
            screens.forEachIndexed { i, screen ->
                h.content = screen
                waitForIdle()
                for (node in onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
                    val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
                        node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
                    assertTrue(name.isNotBlank(), "$skin screen #$i: a clickable node has no name: ${node.config}")
                }
            }
            h.content = { OtpCodeScreen(OtpCodeState(SignInMethod.Phone, "+1 *** 0100", 6), {}) }
            waitForIdle()
            assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "$skin: the code field is one editable node")
        }
    }

    @Test
    fun touchTargetsMeetTheFloorOnAPhone() = runComposeUiTest {
        val h = harness { SignInScreen(SignInState(codeState.zones, guest = GuestEntry(), canRegister = true), {}) }
        eachSkin(h) { skin ->
            for (node in onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
                val heightDp = with(density) { node.size.height.toDp() }
                assertTrue(heightDp >= 44.dp, "$skin: '${node.config.getOrNull(SemanticsProperties.Text)}' is ${heightDp} high")
            }
        }
    }

    @Test
    fun textAtTwiceTheSizeIsNotClipped() = runComposeUiTest {
        val h = harness()
        h.fontScale = 2f
        val screens: List<@Composable () -> Unit> = listOf(
            { SignInScreen(SignInState(codeState.zones, guest = GuestEntry(), canRegister = true, notice = AuthNotice.SignedOutElsewhere), {}) },
            // Steps are measured where they live: inside the sign-in page, which scrolls.
            { SignInScreen(codeState.copy(step = SignInStep.Code(OtpCodeState(SignInMethod.Email, "a***@example.com", 4, isMock = true, resendIn = kotlin.time.Duration.parse("30s"), error = refusal("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2")))), {}) },
            { SignInScreen(codeState.copy(step = SignInStep.Locked(LockedState(refusal("error.423.account_locked", 423, "retry_after_minutes" to "5"), kotlin.time.Duration.parse("5m")))), {}) },
            { SignInScreen(codeState.copy(step = SignInStep.ForcedPasswordChange(ForcedPasswordChangeState(error = refusal("error.400.first_login_challenge_invalid", 400)))), {}) },
            { SessionsScreen(SessionsState(readySessions(), Instant.parse("2026-11-19T12:00:00Z")), {}) },
            { GuestUpgradeScreen(GuestUpgradeState(), {}) },
        )
        eachSkin(h) { skin ->
            for (locale in listOf("en", "ru")) {
                h.locale = locale
                screens.forEachIndexed { i, screen ->
                    h.content = screen
                    waitForIdle()
                    assertNoClippedText("$skin/$locale screen #$i")
                }
            }
        }
    }

    private fun SemanticsNodeInteractionsProvider.assertNoClippedText(where: String) {
        val root = onNodeWithTag(ROOT_TAG).fetchSemanticsNode()
        val rootRight = root.boundsInRoot.right
        val nodes = onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "$where: no text node seen")
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: continue
            val text = layout.layoutInput.text.text
            if (text.isEmpty()) continue
            // Measured from the lines (didOverflowWidth is set by sub-pixel rounding on a line that fits).
            val mp = layout.multiParagraph
            val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
            val clipped = mp.didExceedMaxLines || widest > layout.size.width + 1f || mp.height > layout.size.height + 1f
            if (clipped) fail("$where: '$text' is clipped at 200 % (box ${layout.size}, widest line $widest, height ${mp.height}, lines ${layout.lineCount}, constraints ${layout.layoutInput.constraints}, style lh ${layout.layoutInput.style.lineHeight} fs ${layout.layoutInput.style.fontSize})")
            if (node.boundsInRoot.right > rootRight + 0.5f) fail("$where: '$text' runs past the window edge (${node.boundsInRoot.right} > $rootRight)")
        }
    }
}
