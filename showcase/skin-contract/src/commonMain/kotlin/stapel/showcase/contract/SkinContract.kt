package stapel.showcase.contract

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.ui.DialogSurface
import stapel.ui.StapelIconIds
import stapel.ui.StapelInput
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.BannerSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.CodeFieldSpec
import stapel.ui.skin.DialogSpec
import stapel.ui.skin.IconButtonSpec
import stapel.ui.skin.ListItemSpec
import stapel.ui.skin.NavigationItemSpec
import stapel.ui.skin.NavigationSpec
import stapel.ui.skin.ProgressKind
import stapel.ui.skin.ProgressSpec
import stapel.ui.skin.PullToRefreshSpec
import stapel.ui.skin.SnackbarSpec
import stapel.ui.skin.StapelSkin
import stapel.ui.skin.SurfaceSpec
import stapel.ui.skin.TabsSpec
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.ToggleKind
import stapel.ui.skin.ToggleSpec
import stapel.ui.skin.TopBarSpec
import stapel.ui.stapelLayout
import kotlin.test.assertEquals

/** Counters the contract's callbacks increment. */
private class Hits {
    val counts = mutableMapOf<String, Int>()
    val values = mutableMapOf<String, Any?>()
    fun hit(key: String) {
        counts[key] = (counts[key] ?: 0) + 1
    }
    operator fun get(key: String) = counts[key] ?: 0
}

/** Every slot of [skin] with the contract's fixtures; [dialogs] switches to the dialog phase. */
@Composable
private fun ContractScreen(skin: StapelSkin, tokens: StapelTokens, mode: ThemeMode, hits: Hits, fields: Fields, dialogs: Boolean) {
    val layout = stapelLayout(tokens, 390.dp, input = StapelInput.Pointer)
    StapelTheme(skin = skin, tokens = tokens, mode = mode, layout = layout) {
        val s = StapelTheme.skin
        if (dialogs) {
            s.Dialog(
                DialogSpec({ hits.hit("dialog.dismiss") }, DialogSurface.Modal, title = "Dialog title", dismissLabel = "Close dialog", testTag = "c.dialog"),
            ) { StapelText("Dialog body") }
            s.Dialog(
                DialogSpec({ hits.hit("locked.dismiss") }, DialogSurface.Sheet, accessibleName = "Locked sheet", dismissLabel = "Close locked", dismissible = false),
            ) { StapelText("Locked body") }
            return@StapelTheme
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            s.Button(ButtonSpec("Go", { hits.hit("button") }, testTag = "c.button"), Modifier)
            s.Button(ButtonSpec("Off", { hits.hit("button.disabled") }, enabled = false, testTag = "c.button.disabled"), Modifier)
            s.Button(ButtonSpec("Busy", { hits.hit("button.loading") }, loading = true, testTag = "c.button.loading"), Modifier)
            s.Button(ButtonSpec("Gated", { hits.hit("button.gated") }, gatedReason = "Verify your e-mail first", testTag = "c.button.gated"), Modifier)
            s.IconButton(IconButtonSpec(StapelIconIds.Settings, "Open settings", { hits.hit("iconbutton") }, testTag = "c.iconbutton"), Modifier)
            s.TextField(TextFieldSpec(fields.text, "Your name", testTag = "c.textfield"), Modifier)
            s.TextField(TextFieldSpec(fields.invalid, "E-mail", kind = TextFieldKind.Email, error = "Enter a valid e-mail", testTag = "c.textfield.error"), Modifier)
            s.TextField(TextFieldSpec(fields.password, "Password", kind = TextFieldKind.Password, testTag = "c.textfield.password"), Modifier)
            s.CodeField(CodeFieldSpec(fields.code, 4, "Code", onComplete = { hits.values["code"] = it }, testTag = "c.codefield"), Modifier)
            s.Banner(
                BannerSpec(
                    StapelTone.Warning, "Banner message", title = "Banner title",
                    action = ButtonSpec("Fix it", { hits.hit("banner.action") }), onDismiss = { hits.hit("banner.dismiss") },
                    dismissLabel = "Dismiss banner", testTag = "c.banner",
                ),
                Modifier,
            )
            s.Progress(ProgressSpec(ProgressKind.Spinner, label = "Loading things", testTag = "c.progress"), Modifier)
            s.Progress(ProgressSpec(ProgressKind.Linear, label = "Upload", fraction = 0.4f, testTag = "c.progress.linear"), Modifier)
            s.Surface(SurfaceSpec(onClick = { hits.hit("surface") }, testTag = "c.surface"), Modifier) { StapelText("Surface body") }
            s.ListItem(ListItemSpec("Row headline", supporting = "Row supporting", onClick = { hits.hit("listitem") }, testTag = "c.listitem"), Modifier)
            s.ListItem(ListItemSpec("Picked row", onClick = {}, selected = true, testTag = "c.listitem.selected"), Modifier)
            s.TopBar(
                TopBarSpec("Bar title", navigation = IconButtonSpec(StapelIconIds.ArrowBack, "Go back", { hits.hit("topbar.back") }), testTag = "c.topbar"),
                Modifier,
            )
            s.Divider(Modifier.testTag("c.divider"))
            s.Snackbar(
                SnackbarSpec("Snack message", actionLabel = "Undo", onAction = { hits.hit("snack.action") }, testTag = "c.snackbar"),
                Modifier,
            )
            var checked by fields.switchOn
            s.Toggle(ToggleSpec(ToggleKind.Switch, checked, { checked = it; hits.hit("toggle") }, "Notifications", testTag = "c.toggle"), Modifier)
            s.Toggle(ToggleSpec(ToggleKind.Checkbox, true, {}, "Remember me", testTag = "c.toggle.checkbox"), Modifier)
            var tab by fields.tab
            s.Tabs(TabsSpec(listOf("First", "Second"), tab, { tab = it; hits.hit("tabs") }, testTag = "c.tabs"), Modifier)
            s.Chip(ChipSpec("Chip label", ChipKind.Filter, selected = true, onClick = { hits.hit("chip") }, testTag = "c.chip"), Modifier)
            s.Avatar(AvatarSpec("AL", "Ada Lovelace", testTag = "c.avatar"), Modifier)
            s.Badge(BadgeSpec(3, "3 unread", testTag = "c.badge"), Modifier) { StapelText("Inbox") }
            val nav = NavigationSpec(
                listOf(NavigationItemSpec("one", "One", StapelIconIds.Home), NavigationItemSpec("two", "Two", StapelIconIds.Bell)),
                selectedId = "one",
                onSelect = { hits.values["nav"] = it },
            )
            s.NavigationBar(nav, Modifier.testTag("c.navbar"))
            s.NavigationRail(nav, Modifier.testTag("c.navrail").size(width = 88.dp, height = 200.dp))
            s.PullToRefresh(PullToRefreshSpec(false, { hits.hit("ptr") }, "Refresh", testTag = "c.ptr"), Modifier) { StapelText("Refreshable body") }
        }
    }
}

private class Fields {
    val text = TextFieldState()
    val invalid = TextFieldState("not-an-email")
    val password = TextFieldState()
    val code = TextFieldState()
    val switchOn = mutableStateOf(false)
    val tab = mutableIntStateOf(0)
}

/** The tags of the nodes the contract renders, one or more per slot (all twenty slots appear). */
public val SKIN_CONTRACT_TAGS: List<String> = listOf(
    "c.button", "c.iconbutton", "c.textfield", "c.codefield", "c.banner", "c.progress", "c.surface", "c.listitem",
    "c.topbar", "c.divider", "c.snackbar", "c.toggle", "c.tabs", "c.chip", "c.avatar", "c.badge", "c.navbar", "c.navrail", "c.ptr",
)

/**
 * The skin conformance test: renders all twenty slots of [skin] and asserts the slot duties on the
 * semantics tree (clicks reach callbacks, disabled/busy/gated buttons refuse them, accessible names,
 * semantic errors, password obfuscation, digits-only codes, live regions, progress info, selection,
 * headings, toggles, dialog pane titles and dismissal rules). Throws on the first broken duty.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertSkinContract(skin: StapelSkin, tokens: StapelTokens = StapelDefaultTokens, mode: ThemeMode = ThemeMode.Light) {
    val hits = Hits()
    val fields = Fields()
    var dialogs by mutableStateOf(false)
    mainClock.autoAdvance = false
    setContent { ContractScreen(skin, tokens, mode, hits, fields, dialogs) }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    SKIN_CONTRACT_TAGS.forEach { onNodeWithTag(it, useUnmergedTree = true).assertExistsOrFail(it) }

    // Button
    onNodeWithTag("c.button").assert(hasClickAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).tap()
    settle()
    assertEquals(1, hits["button"], "Button: a click reaches onClick")
    onNodeWithTag("c.button.disabled").assertIsNotEnabled().performClick()
    settle()
    assertEquals(0, hits["button.disabled"], "Button: disabled refuses clicks")
    duty("Button.loading") {
        onNodeWithTag("c.button.loading").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).tap()
        settle()
        assertEquals(0, hits["button.loading"], "Button: loading refuses a second click and announces busy")
    }
    onNodeWithTag("c.button.gated").assertIsEnabled()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Verify your e-mail first")).tap()
    settle()
    assertEquals(0, hits["button.gated"], "Button: gated stays focusable but does not run the action")
    onNodeWithText("Verify your e-mail first", useUnmergedTree = true).assertExistsOrFail("gated reason is visible text")

    // IconButton
    onNodeWithTag("c.iconbutton").assert(hasContentDescription("Open settings")).tap()
    settle()
    assertEquals(1, hits["iconbutton"], "IconButton: click and accessible name")

    // TextField
    onNodeWithTag("c.textfield").assert(hasContentDescription("Your name", substring = true)).performTextInput("Ada  ")
    settle()
    assertEquals("Ada  ", fields.text.text.toString(), "TextField: the value is never rewritten")
    onNodeWithTag("c.textfield.error").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a valid e-mail"))
    onNodeWithTag("c.textfield.password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).performTextInput("s3cret")
    settle()
    assertEquals("s3cret", fields.password.text.toString(), "TextField: password value intact")

    // CodeField
    onNodeWithTag("c.codefield").assert(hasContentDescription("Code", substring = true)).performTextInput("12ab34")
    settle()
    assertEquals("1234", fields.code.text.toString(), "CodeField: digits only, up to length")
    assertEquals("1234", hits.values["code"], "CodeField: onComplete with the full code")

    // Banner
    onNodeWithTag("c.banner").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    onNodeWithText("Banner message", useUnmergedTree = true).assertExistsOrFail("banner message")
    onNodeWithContentDescription("Dismiss banner").tap()
    onNodeWithText("Fix it").tap()
    settle()
    assertEquals(1, hits["banner.dismiss"], "Banner: dismiss")
    assertEquals(1, hits["banner.action"], "Banner: action")

    // Progress
    onNodeWithTag("c.progress", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    onNodeWithTag("c.progress.linear", useUnmergedTree = true)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, androidx.compose.ui.semantics.ProgressBarRangeInfo(0.4f, 0f..1f)))

    // Surface
    onNodeWithTag("c.surface").tap()
    settle()
    assertEquals(1, hits["surface"], "Surface: clickable surface")
    onNodeWithText("Surface body", useUnmergedTree = true).assertExistsOrFail("surface content")

    // ListItem
    onNodeWithTag("c.listitem").assert(hasText("Row headline", substring = true)).assert(hasText("Row supporting", substring = true)).tap()
    settle()
    assertEquals(1, hits["listitem"], "ListItem: click")
    onNodeWithTag("c.listitem.selected").assertIsSelected()

    // TopBar
    onNode(hasText("Bar title") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExistsOrFail("TopBar: title is a heading")
    onNodeWithContentDescription("Go back").tap()
    settle()
    assertEquals(1, hits["topbar.back"], "TopBar: navigation")

    // Snackbar
    onNodeWithText("Snack message", useUnmergedTree = true).assertExistsOrFail("snackbar message")
    onNodeWithText("Undo").tap()
    settle()
    assertEquals(1, hits["snack.action"], "Snackbar: action")

    // Toggle
    onNodeWithTag("c.toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff().tap()
    settle()
    assertEquals(true, fields.switchOn.value, "Toggle: switch turns on")
    onNodeWithTag("c.toggle.checkbox").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))

    // Tabs
    onNode(hasText("Second") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).tap()
    settle()
    assertEquals(1, fields.tab.intValue, "Tabs: select")
    onNode(hasText("Second") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()

    // Chip, Avatar, Badge
    onNodeWithTag("c.chip").assertIsSelected().tap()
    settle()
    assertEquals(1, hits["chip"], "Chip: click")
    onNodeWithTag("c.avatar", useUnmergedTree = true).assert(hasContentDescription("Ada Lovelace"))
    onAllNodesWithContentDescription("3 unread", useUnmergedTree = true).fetchSemanticsNodes().let {
        check(it.isNotEmpty()) { "Badge: accessible description" }
    }

    // Navigation
    onNode(hasText("Two") and hasAnyAncestorTag("c.navbar") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).tap()
    settle()
    assertEquals("two", hits.values["nav"], "NavigationBar: select")
    onNode(hasText("One") and hasAnyAncestorTag("c.navrail") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()

    // PullToRefresh
    onNodeWithText("Refreshable body", useUnmergedTree = true).assertExistsOrFail("PullToRefresh: content")

    // Dialog phase
    dialogs = true
    settle()
    onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Dialog title"), useUnmergedTree = true).assertExistsOrFail("Dialog: pane title from title")
    onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Locked sheet"), useUnmergedTree = true).assertExistsOrFail("Dialog: pane title from accessibleName")
    onNodeWithText("Dialog body", useUnmergedTree = true).assertExistsOrFail("Dialog: content")
    check(onAllNodesWithContentDescription("Close locked").fetchSemanticsNodes().isEmpty()) { "Dialog: dismissible = false draws no dismiss affordance" }
    onNodeWithContentDescription("Close dialog").tap()
    settle()
    assertEquals(1, hits["dialog.dismiss"], "Dialog: dismissal calls onDismiss")
    assertEquals(0, hits["locked.dismiss"], "Dialog: a locked dialog is never dismissed")
}

/** Activates the node's click action (works for nodes scrolled out of the test window). */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.tap(): androidx.compose.ui.test.SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)

/** Runs [block], prefixing any failure with the duty's [name]. */
private inline fun duty(name: String, block: () -> Unit) {
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("skin contract duty $name: ${e.message}", e)
    }
}

private fun hasAnyAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.settle() {
    mainClock.advanceTimeBy(300)
    waitForIdle()
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsOrFail(what: String) {
    try {
        assertExists()
    } catch (e: AssertionError) {
        throw AssertionError("skin contract: missing $what", e)
    }
}
