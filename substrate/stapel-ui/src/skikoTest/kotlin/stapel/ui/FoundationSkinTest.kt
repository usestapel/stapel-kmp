package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import stapel.i18n.CoreUiBundle
import stapel.i18n.CoreUiStrings
import stapel.i18n.StapelStrings
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.state.Freshness
import stapel.result.state.Resource
import stapel.showcase.contract.assertSkinContract
import stapel.showcase.contract.assertSlotsMirrorInRtl
import stapel.showcase.contract.assertTopBarSurvivesLargeText
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import stapel.showcase.contract.assertSlotsSurviveLargeText
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import stapel.showcase.contract.assertSkeletonRespectsReduceMotion
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.PagerIndicatorSpec
import stapel.ui.skin.ChipGroupSpec
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import stapel.ui.skin.MenuSpec
import stapel.ui.skin.StepperSpec
import stapel.ui.skin.ListStatusKind
import stapel.ui.skin.ListStatusSpec
import stapel.ui.skin.canDecrement
import stapel.ui.skin.menuPosition
import stapel.ui.skin.stepperValue
import androidx.compose.ui.semantics.contentDescription
import stapel.ui.skin.ProgressSpec
import stapel.ui.skin.SelectSpec
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.CodeFieldSpec
import stapel.ui.skin.keyboardOptions
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.BannerSpec
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.ListItemSpec
import stapel.ui.skin.PullToRefreshSpec
import stapel.ui.skin.ToggleKind
import stapel.ui.skin.ToggleSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.TabsSpec
import stapel.ui.skin.TopBarSpec
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.StapelSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** A skin that breaks one duty: its button runs the action while loading. */
private object LeakyLoadingSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        FoundationSkin.Button(ButtonSpec(spec.label, spec.onClick, spec.variant, spec.size, spec.enabled, false, spec.gatedReason, testTag = spec.testTag), modifier)
    }
}

/** Breaks the large-text duty: a single-line, fixed-height bar. */
private object SingleLineTitleSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        Box(modifier.height(56.dp).optionalTag(spec.testTag)) {
            StapelText(spec.title, maxLines = 1, modifier = Modifier.semantics { heading() })
        }
    }
}

/** Breaks the detail duty: the detail line hides behind a "Details" toggle. */
private object CollapsedDetailSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Banner(spec: BannerSpec, modifier: Modifier) {
        FoundationSkin.Banner(BannerSpec(spec.tone, spec.message, spec.title, null, spec.action, spec.onDismiss, spec.dismissLabel, spec.testTag), modifier)
    }
}

/** Marks its Tabs slot, to see that the wrapper goes through the skin. */
private object MarkedTabsSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Tabs(spec: TabsSpec, modifier: Modifier) {
        Box(Modifier.testTag("marked.tabs")) { FoundationSkin.Tabs(spec, modifier) }
    }
}

/** Breaks the radio duty: a radio drawn as a toggle reports `false` when tapped while checked. */
private object UncheckingRadioSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Toggle(spec: ToggleSpec, modifier: Modifier) {
        val kind = if (spec.kind == ToggleKind.Radio) ToggleKind.Checkbox else spec.kind
        FoundationSkin.Toggle(ToggleSpec(kind, spec.checked, spec.onCheckedChange, spec.label, spec.enabled, spec.testTag, spec.supporting, spec.indeterminate, spec.error, spec.controlAtEnd), modifier)
    }
}

/** Breaks the status-chip duty: every chip is clickable. */
private object ClickableStatusChipSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Chip(spec: ChipSpec, modifier: Modifier) {
        FoundationSkin.Chip(ChipSpec(spec.label, ChipKind.Choice, spec.selected, spec.onClick ?: {}, spec.tone, spec.testTag, spec.leadingIcon, spec.onRemove, spec.removeLabel, spec.enabled, spec.size), modifier)
    }
}

/** Breaks the avatar ladder: an image that does not load leaves a blank circle. */
private object BlankAvatarSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Avatar(spec: AvatarSpec, modifier: Modifier) {
        FoundationSkin.Avatar(AvatarSpec(if (spec.image != null) null else spec.initials, spec.contentDescription, spec.size, spec.icon, spec.image, spec.badge, spec.badgePlacement, spec.onClick, spec.testTag), modifier)
    }
}

/** Breaks the badge cap: shows the raw count. */
private object RawCountBadgeSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Badge(spec: BadgeSpec, modifier: Modifier, content: @Composable () -> Unit) {
        FoundationSkin.Badge(BadgeSpec(spec.count, spec.contentDescription, spec.testTag, spec.tone, spec.size, spec.emphasis, Int.MAX_VALUE, spec.icon), modifier, content)
    }
}

/** Breaks the refresh duty: gesture only, no accessibility action. */
private object GestureOnlyRefreshSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun PullToRefresh(spec: PullToRefreshSpec, modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.optionalTag(spec.testTag)) { content() }
    }
}

/** Breaks the value-block duty: the value is squeezed to one ellipsized line under the headline's width. */
private object CrampedValueSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ListItem(spec: ListItemSpec, modifier: Modifier) {
        val value = spec.value
        FoundationSkin.ListItem(
            ListItemSpec(
                spec.headline, spec.supporting, spec.overline, spec.leading,
                value?.let { { StapelText(it.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(8.dp)) } } ?: spec.trailing,
                spec.onClick, spec.selected, spec.enabled, spec.testTag, spec.media, null, spec.accessory,
            ),
            modifier,
        )
    }
}

/** Breaks the large-text duty: a fixed-height single-line chip. */
private object FixedChipSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Chip(spec: ChipSpec, modifier: Modifier) {
        Box(modifier.height(32.dp).optionalTag(spec.testTag)) { StapelText(spec.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Breaks the search duty: no clear action. */
private object ClearlessSearchSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun TextField(spec: TextFieldSpec, modifier: Modifier) {
        FoundationSkin.TextField(
            TextFieldSpec(
                spec.state, spec.label, spec.kind, spec.placeholder, spec.supportingText, spec.error, spec.enabled, spec.readOnly, spec.leadingIcon,
                spec.suffix, spec.trailingAction, spec.imeAction, spec.onImeAction, spec.autofill, spec.minLines, spec.maxLines, spec.testTag, spec.loading, null,
                spec.inputTransformation, spec.outputTransformation,
            ),
            modifier,
        )
    }
}

/** Breaks the forwarding duty: drops the caller's transformations. */
private object FormattingSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun TextField(spec: TextFieldSpec, modifier: Modifier) {
        FoundationSkin.TextField(
            TextFieldSpec(
                spec.state, spec.label, spec.kind, spec.placeholder, spec.supportingText, spec.error, spec.enabled, spec.readOnly, spec.leadingIcon,
                spec.suffix, spec.trailingAction, spec.imeAction, spec.onImeAction, spec.autofill, spec.minLines, spec.maxLines, spec.testTag,
                spec.loading, spec.clearLabel, null, null,
            ),
            modifier,
        )
    }
}

/** Breaks the code-field order: the digit filter runs before the caller's transformation. */
private object FilterFirstCodeSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun CodeField(spec: CodeFieldSpec, modifier: Modifier) {
        FoundationSkin.CodeField(CodeFieldSpec(spec.state, spec.length, spec.label, spec.error, spec.enabled, spec.onComplete, spec.testTag, null), modifier)
    }
}

/** Breaks the select duty: a plain button role. */
private object ButtonSelectSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Select(spec: SelectSpec, modifier: Modifier) {
        FoundationSkin.Button(ButtonSpec(spec.value ?: spec.placeholder.orEmpty(), spec.onClick, enabled = spec.enabled, testTag = spec.testTag), modifier)
    }
}

/** Breaks the dot window: every page gets a dot. */
private object UnwindowedPagerSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun PagerIndicator(spec: PagerIndicatorSpec, modifier: Modifier) {
        FoundationSkin.PagerIndicator(PagerIndicatorSpec(spec.count, spec.current, spec.description, spec.onSelect, spec.dotLabel, Int.MAX_VALUE, spec.testTag), modifier)
    }
}

/** Breaks the one-heading duty: the large title is a second heading. */
private object TwoHeadingBarSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        Column(modifier.optionalTag(spec.testTag)) {
            FoundationSkin.TopBar(TopBarSpec(spec.title, spec.navigation, spec.actions, null, spec.subtitle, null), Modifier)
            if ((spec.collapsedFraction ?: 1f) < 1f) {
                Box(Modifier.optionalTag(spec.testTag?.plus(".large"))) { StapelText(spec.title, modifier = Modifier.semantics { heading() }) }
            }
        }
    }
}

/** Breaks reduce motion: the skeleton always shimmers. */
private object RestlessSkeletonSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Progress(spec: ProgressSpec, modifier: Modifier) {
        CompositionLocalProvider(LocalStapelReduceMotion provides false) { FoundationSkin.Progress(spec, modifier) }
    }
}

/** Breaks the menu: picking an item leaves it open. */
private object StickyMenuSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Menu(spec: MenuSpec, modifier: Modifier, anchor: @Composable () -> Unit) {
        FoundationSkin.Menu(MenuSpec(spec.expanded, {}, spec.items, spec.label, spec.testTag), modifier, anchor)
    }
}

/** Breaks the stepper bounds: no minimum. */
private object UnboundedStepperSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Stepper(spec: StepperSpec, modifier: Modifier) {
        FoundationSkin.Stepper(
            StepperSpec(spec.value, spec.onValueChange, spec.label, spec.decrementLabel, spec.incrementLabel, Int.MIN_VALUE, spec.max, spec.step, spec.valueText, spec.enabled, spec.testTag),
            modifier,
        )
    }
}

/** Breaks the end row: it offers the action (drawn as a failed row). */
private object ActionAtTheEndSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        val kind = if (spec.kind == ListStatusKind.End) ListStatusKind.AppendFailed else spec.kind
        FoundationSkin.ListStatus(ListStatusSpec(kind, spec.message, spec.actionLabel, spec.onAction, spec.testTag), modifier)
    }
}

/** Breaks appending: no progress (drawn as an end row). */
private object SpinnerlessListStatusSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        val kind = if (spec.kind == ListStatusKind.Appending) ListStatusKind.End else spec.kind
        FoundationSkin.ListStatus(ListStatusSpec(kind, spec.message, spec.actionLabel, spec.onAction, spec.testTag), modifier)
    }
}

/** Breaks the failed row: not announced (drawn as a stale row). */
private object SilentFailureListStatusSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        val kind = if (spec.kind == ListStatusKind.AppendFailed) ListStatusKind.Stale else spec.kind
        FoundationSkin.ListStatus(ListStatusSpec(kind, spec.message, spec.actionLabel, spec.onAction, spec.testTag), modifier)
    }
}

/** Breaks the width: a status row narrower than its list. */
private object NarrowListStatusSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        androidx.compose.foundation.layout.Box(modifier.width(120.dp)) { FoundationSkin.ListStatus(spec, Modifier) }
    }
}

/** Breaks the wrap: every group is one row that neither wraps nor scrolls. */
private object OneRowChipGroupSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun ChipGroup(spec: ChipGroupSpec, modifier: Modifier) {
        androidx.compose.foundation.layout.Row(
            modifier.semantics { contentDescription = spec.label }.optionalTag(spec.testTag),
        ) { spec.chips.forEach { Chip(it, Modifier) } }
    }
}

/** A project chip: the chip group must draw it. */
private object MarkedChipSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Chip(spec: ChipSpec, modifier: Modifier) {
        Box(modifier.testTag("marked.chip.${spec.label}")) { StapelText(spec.label) }
    }
}

private fun Modifier.optionalTag(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/** A project skin replacing exactly one slot by interface delegation. */
private object BrandButtonSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        Box(modifier.testTag("brand.button")) { StapelText("BRAND ${spec.label}") }
    }
}

@OptIn(ExperimentalTestApi::class)
class FoundationSkinTest {
    @Test
    fun foundationSkinHonoursTheContractLight() = runComposeUiTest { assertSkinContract(FoundationSkin) }

    @Test
    fun foundationSkinHonoursTheContractDark() = runComposeUiTest { assertSkinContract(FoundationSkin, mode = ThemeMode.Dark) }

    @Test
    fun theTopBarSurvivesTwiceTheTextSizeInEnglishAndRussian() = runComposeUiTest { assertTopBarSurvivesLargeText(FoundationSkin) }

    /** More than the allowed lines at full size (any font at 3x): the title shrinks, it never truncates. */
    @Test
    fun theTopBarSurvivesThreeTimesTheTextSize() = runComposeUiTest { assertTopBarSurvivesLargeText(FoundationSkin, fontScale = 3f) }

    @Test
    fun aSingleLineTopBarIsCaughtTruncatingAtLargeText() = runComposeUiTest {
        val e = assertFails { assertTopBarSurvivesLargeText(SingleLineTitleSkin) }
        assertTrue(e.message.orEmpty().startsWith("skin contract duty TopBar.largeText"), e.message)
    }

    @Test
    fun aCollapsedErrorDetailIsCaught() = runComposeUiTest {
        val e = assertFails { assertSkinContract(CollapsedDetailSkin) }
        assertTrue(e.message.orEmpty().startsWith("skin contract duty Banner.detail"), e.message)
    }

    @Test
    fun stapelTabsDrawsThroughTheSkinSlot() = runComposeUiTest {
        var picked = -1
        setContent {
            StapelTheme(skin = MarkedTabsSkin, mode = ThemeMode.Light) {
                StapelTabs(listOf("Phone", "E-mail"), 0, { picked = it }, testTag = "tabs")
            }
        }
        onNodeWithTag("marked.tabs").assertExists()
        onNodeWithText("E-mail").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun theLaterSlotsSurviveTwiceTheTextSize() = runComposeUiTest { assertSlotsSurviveLargeText(FoundationSkin) }

    @Test
    fun aFixedHeightChipIsCaughtAtLargeText() = runComposeUiTest {
        val e = assertFails { assertSlotsSurviveLargeText(FixedChipSkin) }
        assertTrue(e.message.orEmpty().startsWith("skin contract duty Chip.largeText"), e.message)
    }

    @Test
    fun aRadioThatUnchecksIsCaught() = runComposeUiTest { assertDutyBroken("Toggle.radio") { assertSkinContract(UncheckingRadioSkin) } }

    @Test
    fun aClickableStatusChipIsCaught() = runComposeUiTest { assertDutyBroken("Chip.status") { assertSkinContract(ClickableStatusChipSkin) } }

    @Test
    fun aBlankAvatarIsCaught() = runComposeUiTest { assertDutyBroken("Avatar.fallback") { assertSkinContract(BlankAvatarSkin) } }

    @Test
    fun anUncappedBadgeIsCaught() = runComposeUiTest { assertDutyBroken("Badge.max") { assertSkinContract(RawCountBadgeSkin) } }

    @Test
    fun aGestureOnlyRefreshIsCaught() = runComposeUiTest { assertDutyBroken("PullToRefresh.action") { assertSkinContract(GestureOnlyRefreshSkin) } }

    @Test
    fun aCrampedValueBlockIsCaught() = runComposeUiTest { assertDutyBroken("ListItem.value") { assertSkinContract(CrampedValueSkin) } }

    @Test
    fun aSkinIgnoringTheTransformationIsCaught() = runComposeUiTest { assertDutyBroken("TextField.transformation") { assertSkinContract(FormattingSkin) } }

    @Test
    fun aCodeFieldDroppingTheTransformationIsCaught() = runComposeUiTest {
        assertDutyBroken("CodeField.transformation") { assertSkinContract(FilterFirstCodeSkin) }
    }

    @Test
    fun aMaskKeyboardWinsOverTheKind() {
        val numeric = object : androidx.compose.foundation.text.input.InputTransformation {
            override val keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
            override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformInput() = Unit
        }
        val state = androidx.compose.foundation.text.input.TextFieldState()
        assertEquals(androidx.compose.ui.text.input.KeyboardType.NumberPassword, TextFieldSpec(state, "x", inputTransformation = numeric).keyboardOptions().keyboardType)
        assertEquals(androidx.compose.ui.text.input.KeyboardType.Email, TextFieldSpec(state, "x", TextFieldKind.Email).keyboardOptions().keyboardType)
        assertEquals(androidx.compose.ui.text.input.ImeAction.Search, TextFieldSpec(state, "x", TextFieldKind.Search).keyboardOptions().imeAction)
    }

    @Test
    fun aSearchFieldWithoutClearIsCaught() = runComposeUiTest { assertDutyBroken("TextField.search") { assertSkinContract(ClearlessSearchSkin) } }

    @Test
    fun aSelectWithoutTheDropdownRoleIsCaught() = runComposeUiTest { assertDutyBroken("Select") { assertSkinContract(ButtonSelectSkin) } }

    @Test
    fun anUnwindowedPagerIsCaught() = runComposeUiTest { assertDutyBroken("PagerIndicator") { assertSkinContract(UnwindowedPagerSkin) } }

    @Test
    fun aSecondHeadingInALargeBarIsCaught() = runComposeUiTest { assertDutyBroken("TopBar.large") { assertSkinContract(TwoHeadingBarSkin) } }

    @Test
    fun aMenuThatStaysOpenIsCaught() = runComposeUiTest { assertDutyBroken("Menu.select") { assertSkinContract(StickyMenuSkin) } }

    @Test
    fun aStepperPastItsMinimumIsCaught() = runComposeUiTest { assertDutyBroken("Stepper.bounds") { assertSkinContract(UnboundedStepperSkin) } }

    @Test
    fun aChipGroupThatDoesNotWrapIsCaught() = runComposeUiTest { assertDutyBroken("ChipGroup.wrap") { assertSkinContract(OneRowChipGroupSkin) } }

    @Test
    fun anEndRowWithAnActionIsCaught() = runComposeUiTest { assertDutyBroken("ListStatus.end") { assertSkinContract(ActionAtTheEndSkin) } }

    @Test
    fun anAppendingRowWithoutProgressIsCaught() = runComposeUiTest { assertDutyBroken("ListStatus.appending") { assertSkinContract(SpinnerlessListStatusSkin) } }

    @Test
    fun aSilentFailedRowIsCaught() = runComposeUiTest { assertDutyBroken("ListStatus.failed") { assertSkinContract(SilentFailureListStatusSkin) } }

    @Test
    fun aNarrowStatusRowIsCaught() = runComposeUiTest { assertDutyBroken("ListStatus.width") { assertSkinContract(NarrowListStatusSkin) } }

    @Test
    fun theChipGroupDrawsTheProjectsChip() = runComposeUiTest {
        setContent {
            StapelTheme(skin = MarkedChipSkin, mode = ThemeMode.Light) {
                StapelChipGroup(listOf(ChipSpec("Fresh"), ChipSpec("Local")), "Filters")
            }
        }
        onNodeWithTag("marked.chip.Fresh").assertExists()
        onNodeWithTag("marked.chip.Local").assertExists()
    }

    @Test
    fun aStepperClampsAndNeverOverflows() {
        val spec = StepperSpec(Int.MAX_VALUE - 1, {}, "q", "-", "+", min = 0, max = Int.MAX_VALUE, step = 5)
        assertEquals(Int.MAX_VALUE, spec.stepperValue(1))
        assertEquals(Int.MAX_VALUE - 6, spec.stepperValue(-1))
        assertEquals(0, StepperSpec(3, {}, "q", "-", "+", min = 0, step = 5).stepperValue(-1))
        assertTrue(!StepperSpec(0, {}, "q", "-", "+").canDecrement)
    }

    @Test
    fun theMenuStaysInsideTheWindowAndFollowsTheDirection() {
        val window = androidx.compose.ui.unit.IntSize(400, 800)
        val popup = androidx.compose.ui.unit.IntSize(200, 150)
        val anchor = androidx.compose.ui.unit.IntRect(352, 10, 400, 58)
        val ltr = menuPosition(anchor, window, androidx.compose.ui.unit.LayoutDirection.Ltr, popup)
        assertEquals(androidx.compose.ui.unit.IntOffset(200, 58), ltr, "end edges meet, below the anchor")
        val rtl = menuPosition(androidx.compose.ui.unit.IntRect(0, 10, 48, 58), window, androidx.compose.ui.unit.LayoutDirection.Rtl, popup)
        assertEquals(androidx.compose.ui.unit.IntOffset(0, 58), rtl, "right-to-left: the left edges meet")
        val clamped = menuPosition(androidx.compose.ui.unit.IntRect(352, 700, 400, 748), window, androidx.compose.ui.unit.LayoutDirection.Rtl, popup)
        assertEquals(androidx.compose.ui.unit.IntOffset(200, 550), clamped, "inside the window, above when no room below")
    }

    @Test
    fun theSkeletonRespectsReduceMotion() = runComposeUiTest { assertSkeletonRespectsReduceMotion(FoundationSkin) }

    @Test
    fun aSkeletonIgnoringReduceMotionIsCaught() = runComposeUiTest {
        assertDutyBroken("Progress.reduceMotion") { assertSkeletonRespectsReduceMotion(RestlessSkeletonSkin) }
    }

    @Test
    fun contractCatchesABrokenSkin() = runComposeUiTest {
        val e = assertFails { assertSkinContract(LeakyLoadingSkin) }
        assertTrue(e.message.orEmpty().startsWith("skin contract duty Button.loading"), e.message)
    }

    @Test
    fun delegationReplacesExactlyOneSlot() = runComposeUiTest {
        var back = 0
        setContent {
            StapelTheme(skin = BrandButtonSkin, mode = ThemeMode.Light) {
                StapelButton("Pay", {})
                StapelIconButton(StapelIconIds.ArrowBack, "Back", { back++ }, testTag = "icon")
            }
        }
        onNodeWithText("BRAND Pay").assertExists()
        onNodeWithText("Pay").assertDoesNotExist()
        onNodeWithTag("icon").performClick()
        assertEquals(1, back)
    }

    @Test
    fun pageGutterIsTheTokenValuePerSizeClass() = runComposeUiTest {
        var width by mutableStateOf(390.dp)
        var edge by mutableStateOf(false)
        setContent {
            val layout = stapelLayout(StapelDefaultTokens, width, StapelInput.Touch, 44.dp)
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = layout) {
                Box(Modifier.size(width, 600.dp)) {
                    StapelPage(edgeToEdge = edge, measure = PaneMeasure.Full) { Box(Modifier.size(10.dp).testTag("child")) }
                }
            }
        }
        assertEquals(4.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
        width = 800.dp
        waitForIdle()
        assertEquals(8.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
        edge = true
        waitForIdle()
        assertEquals(0.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
    }

    @Test
    fun stringsRecomposeOnARuntimeLocaleSwitch() = runComposeUiTest {
        val strings = StapelStrings {
            initialLocale = "en"
            register(CoreUiBundle)
            override("ru", mapOf("stapel.ui.retry" to "Ещё раз"))
        }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) {
                StapelText(stapelString(CoreUiStrings.Retry))
            }
        }
        onNodeWithText("Try again").assertExists()
        runBlocking { strings.setLocale("ru") }
        waitForIdle()
        onNodeWithText("Ещё раз").assertExists()
    }

    @Test
    fun loadBoundaryHasDistinctArmsAndErrorsUseTheLadder() = runComposeUiTest {
        var state by mutableStateOf<Resource<String>>(Resource.Loading)
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }) {
                LoadBoundary(state, onRetry = {}) { StapelText("Data: $it") }
            }
        }
        onNodeWithText("Data", substring = true).assertDoesNotExist()
        state = Resource.Failed(ServerRefusal(ErrorEnvelope.synthesized(503)))
        waitForIdle()
        onNodeWithText("The service is temporarily unavailable. Please try again shortly.", useUnmergedTree = true).assertExists()
        onNodeWithText("Try again").assertExists()
        state = Resource.Ready("x", Freshness.network(0))
        waitForIdle()
        onNodeWithText("Data: x").assertExists()
    }
}

/** Asserts that [block] fails on the contract duty [name] (and not on an earlier one). */
private fun assertDutyBroken(name: String, block: () -> Unit) {
    val e = assertFails(block)
    assertTrue(e.message.orEmpty().startsWith("skin contract duty $name"), e.message)
}

@OptIn(ExperimentalTestApi::class)
class FoundationRtlTest {
    @Test
    fun theSlotsMirrorInRtl() = runComposeUiTest { assertSlotsMirrorInRtl(FoundationSkin) }
}

/** A project icon set whose back arrow does not declare itself auto-mirrored. */
private object PlainArrowIcons : StapelIconSet {
    private val arrow = androidx.compose.ui.graphics.vector.ImageVector.Builder("plain-arrow", 24.dp, 24.dp, 24f, 24f)
        .addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M4 12 L20 4 L20 20 Z").toNodes(), fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black))
        .build()

    @Composable
    override fun resolve(id: String): androidx.compose.ui.graphics.vector.ImageVector? = if (id == StapelIconIds.ArrowBack.id) arrow else null
}

@OptIn(ExperimentalTestApi::class)
class IconMirrorTest {
    @Test
    fun aDirectionalIconMirrorsInRtlWhateverTheSetSays() = runComposeUiTest {
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, icons = PlainArrowIcons) {
                androidx.compose.foundation.layout.Row {
                    StapelIconView(StapelIconIds.ArrowBack, null, Modifier.testTag("ltr"), size = 24.dp)
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                        StapelIconView(StapelIconIds.ArrowBack, null, Modifier.testTag("rtl"), size = 24.dp)
                        StapelIconView(StapelIcon(StapelIconIds.ArrowBack.id), null, Modifier.testTag("plain"), size = 24.dp)
                    }
                }
            }
        }
        val ltr = onNodeWithTag("ltr").captureToImage().toPixelMap()
        val rtl = onNodeWithTag("rtl").captureToImage().toPixelMap()
        val plain = onNodeWithTag("plain").captureToImage().toPixelMap()
        var mirrored = 0
        var same = 0
        for (x in 0 until ltr.width) for (y in 0 until ltr.height) {
            if (ltr[x, y] != rtl[ltr.width - 1 - x, y]) mirrored++
            if (ltr[x, y] != plain[x, y]) same++
        }
        assertEquals(0, mirrored, "the auto-mirrored id draws the set's glyph flipped in RTL")
        assertEquals(0, same, "an icon that is not directional is never flipped")
    }
}
