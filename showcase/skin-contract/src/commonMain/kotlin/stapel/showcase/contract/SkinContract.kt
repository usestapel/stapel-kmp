package stapel.showcase.contract

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import stapel.ui.LocalStapelReduceMotion
import stapel.ui.skin.PagerIndicatorSpec
import stapel.ui.skin.ChipGroupLayout
import stapel.ui.skin.ChipGroupSpec
import stapel.ui.skin.MenuItemSpec
import stapel.ui.skin.MenuSpec
import stapel.ui.skin.StepperSpec
import stapel.ui.skin.ListStatusKind
import stapel.ui.skin.ListStatusSpec
import androidx.compose.foundation.layout.width
import stapel.ui.skin.SelectSpec
import stapel.ui.skin.SkeletonShape
import androidx.compose.ui.test.swipeDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import stapel.ui.LocalStapelImages
import stapel.ui.StapelImages
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.BadgeEmphasis
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemMedia
import stapel.ui.skin.ListItemValue
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
import stapel.ui.StapelLayout
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

/** How the contract wraps its screen in a theme (a skin may need its design system's own root). */
public interface SkinContractTheme {
    /** Provides the theme for [skin] around [content]. */
    @Composable
    public fun Wrap(skin: StapelSkin, tokens: StapelTokens, mode: ThemeMode, layout: StapelLayout, content: @Composable () -> Unit)
}

/** Plain [StapelTheme]. */
public object PlainContractTheme : SkinContractTheme {
    @Composable
    override fun Wrap(skin: StapelSkin, tokens: StapelTokens, mode: ThemeMode, layout: StapelLayout, content: @Composable () -> Unit) {
        StapelTheme(skin = skin, tokens = tokens, mode = mode, layout = layout, content = content)
    }
}

/** The contract's phases: every slot in one column, then the menu, the dialogs, and gestures, each on a screen of its own. */
private enum class Phase { Slots, Menu, Dialogs, Gestures }

/** Draws [ContractImages.OK] as a solid painter and nothing else (a failing or loading image). */
private object ContractImages : StapelImages {
    const val OK = "contract://ok"

    @Composable
    override fun painter(source: String): Painter? = if (source == OK) ColorPainter(Color(0xFF3366CCu.toInt())) else null
}

/** Every slot of [skin] with the contract's fixtures, per [phase]. */
@Composable
private fun ContractScreen(skin: StapelSkin, tokens: StapelTokens, mode: ThemeMode, theme: SkinContractTheme, hits: Hits, fields: Fields, phase: Phase) {
    val layout = stapelLayout(tokens, 390.dp, input = StapelInput.Pointer)
    theme.Wrap(skin, tokens, mode, layout) {
      CompositionLocalProvider(LocalStapelImages provides ContractImages) {
        val s = StapelTheme.skin
        if (phase == Phase.Gestures) {
            s.PullToRefresh(PullToRefreshSpec(false, { hits.hit("ptr.swipe") }, "Refresh by pull", testTag = "c.ptr.swipe"), Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState()).testTag("c.ptr.swipe.content")) {
                    StapelText("Pull me")
                }
            }
            return@CompositionLocalProvider
        }
        if (phase == Phase.Menu) {
            var open by fields.menuOpen
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(400.dp)) {
                s.Menu(
                    MenuSpec(
                        open, { open = false; hits.hit("menu.dismiss") },
                        listOf(
                            MenuItemSpec("Reply", { hits.hit("menu.reply") }, icon = StapelIconIds.ArrowBack, testTag = "c.menu.reply"),
                            MenuItemSpec("Forward", { hits.hit("menu.forward") }, enabled = false, testTag = "c.menu.forward"),
                            MenuItemSpec("Delete", { hits.hit("menu.delete") }, destructive = true, testTag = "c.menu.delete"),
                        ),
                        "Message actions", testTag = "c.menu",
                    ),
                    Modifier.align(androidx.compose.ui.Alignment.TopEnd),
                ) {
                    s.IconButton(IconButtonSpec(StapelIconIds.More, "More actions", { open = true }, testTag = "c.menu.anchor"), Modifier)
                }
            }
            return@CompositionLocalProvider
        }
        if (phase == Phase.Dialogs) {
            s.Dialog(
                DialogSpec({ hits.hit("dialog.dismiss") }, DialogSurface.Modal, title = "Dialog title", dismissLabel = "Close dialog", testTag = "c.dialog"),
            ) { StapelText("Dialog body") }
            s.Dialog(
                DialogSpec({ hits.hit("locked.dismiss") }, DialogSurface.Sheet, accessibleName = "Locked sheet", dismissLabel = "Close locked", dismissible = false),
            ) { StapelText("Locked body") }
            return@CompositionLocalProvider
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
                    StapelTone.Warning, "Banner message", title = "Banner title", detail = "HTTP 503 · req-42",
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
            s.PullToRefresh(PullToRefreshSpec(true, { hits.hit("ptr.busy") }, "Refreshing list", testTag = "c.ptr.busy"), Modifier) { StapelText("Busy body") }
            // Depth of the later slots.
            var radio by fields.radioPicked
            s.Toggle(ToggleSpec(ToggleKind.Radio, true, { radio = it; hits.hit("radio") }, "Pick me", testTag = "c.toggle.radio"), Modifier)
            s.Toggle(ToggleSpec(ToggleKind.Checkbox, false, { hits.values["tri"] = it }, "Select all", indeterminate = true, testTag = "c.toggle.tri"), Modifier)
            s.Toggle(
                ToggleSpec(
                    ToggleKind.Switch, false, { hits.hit("toggle.rich") }, "Marketing e-mails", supporting = "Once a month at most",
                    error = "Confirm your e-mail first", controlAtEnd = true, testTag = "c.toggle.rich",
                ),
                Modifier.fillMaxWidth(),
            )
            s.Toggle(ToggleSpec(ToggleKind.Switch, false, { hits.hit("toggle.off") }, "Locked switch", enabled = false, testTag = "c.toggle.disabled"), Modifier)
            s.Tabs(
                TabsSpec(
                    listOf("Grid", "List", "Map"), 0, { hits.values["tabs.icons"] = it },
                    icons = listOf(StapelIconIds.Home, StapelIconIds.More, StapelIconIds.Search), iconOnly = true, testTag = "c.tabs.icons",
                ),
                Modifier,
            )
            s.Tabs(TabsSpec((1..9).map { "Section $it" }, 0, { hits.values["tabs.scroll"] = it }, scrollable = true, testTag = "c.tabs.scroll"), Modifier)
            s.Chip(ChipSpec("Verified", ChipKind.Status, tone = StapelTone.Success, testTag = "c.chip.status"), Modifier)
            s.Chip(
                ChipSpec(
                    "Ada", ChipKind.Input, onClick = { hits.hit("chip.input") }, onRemove = { hits.hit("chip.remove") },
                    removeLabel = "Remove Ada", leadingIcon = StapelIconIds.User, testTag = "c.chip.input",
                ),
                Modifier,
            )
            s.Chip(ChipSpec("Off chip", ChipKind.Filter, onClick = { hits.hit("chip.disabled") }, enabled = false, testTag = "c.chip.disabled"), Modifier)
            s.Avatar(AvatarSpec("Al", "Fallback person", image = "contract://missing", testTag = "c.avatar.fallback"), Modifier)
            s.Avatar(AvatarSpec("BC", "Pictured person", image = ContractImages.OK, testTag = "c.avatar.image"), Modifier)
            s.Avatar(AvatarSpec(null, "Glyph person", testTag = "c.avatar.glyph"), Modifier)
            s.Avatar(
                AvatarSpec("GH", "Grace Hopper", size = AvatarSize.Large, badge = BadgeSpec(null, "Online", tone = StapelTone.Success), testTag = "c.avatar.large"),
                Modifier,
            )
            s.Avatar(AvatarSpec("KJ", "Katherine Johnson", onClick = { hits.hit("avatar") }, testTag = "c.avatar.button"), Modifier)
            s.Badge(BadgeSpec(150, "150 unread", testTag = "c.badge.max"), Modifier) { StapelText("Max host") }
            s.Badge(BadgeSpec(0, "Nothing unread", testTag = "c.badge.zero"), Modifier) { StapelText("Zero host") }
            s.Badge(BadgeSpec(null, "New", tone = StapelTone.Info, emphasis = BadgeEmphasis.Subtle, testTag = "c.badge.dot"), Modifier) { StapelText("Dot host") }
            s.ListItem(
                ListItemSpec(
                    "Coffee beans", supporting = "Card payment", onClick = { hits.hit("listitem.value") },
                    media = ListItemMedia.Icon(StapelIconIds.Info), value = ListItemValue("-12.50", "Yesterday"),
                    accessory = ListItemAccessory.Chevron, testTag = "c.listitem.value",
                ),
                Modifier,
            )
            var rowOn by fields.rowSwitch
            s.ListItem(
                ListItemSpec(
                    "Push alerts", supporting = "On this device", onClick = { hits.hit("listitem.toggle.click") },
                    accessory = ListItemAccessory.Toggle(ToggleKind.Switch, rowOn, { rowOn = it }), testTag = "c.listitem.toggle",
                ),
                Modifier,
            )
            s.ListItem(ListItemSpec("Disabled row", onClick = { hits.hit("listitem.disabled") }, enabled = false, testTag = "c.listitem.disabled"), Modifier)
            // Slots added after the first twenty.
            s.TextField(
                TextFieldSpec(fields.search, "Search products", TextFieldKind.Search, loading = true, clearLabel = "Clear search", testTag = "c.textfield.search"),
                Modifier,
            )
            s.TextField(
                TextFieldSpec(fields.masked, "Reference", inputTransformation = UpperCase, outputTransformation = DashAfterThree, testTag = "c.textfield.mask"),
                Modifier,
            )
            s.CodeField(CodeFieldSpec(fields.folded, 4, "Folded code", inputTransformation = FoldArabicIndicDigits, testTag = "c.codefield.fold"), Modifier)
            s.Select(SelectSpec("Language", "English", { hits.hit("select") }, error = "Pick a supported language", testTag = "c.select"), Modifier)
            s.Select(SelectSpec("Currency", null, { hits.hit("select.empty") }, placeholder = "Choose a currency", testTag = "c.select.empty"), Modifier)
            s.Select(SelectSpec("Region", "North", { hits.hit("select.disabled") }, enabled = false, testTag = "c.select.disabled"), Modifier)
            s.PagerIndicator(
                PagerIndicatorSpec(12, 6, "Page 7 of 12", onSelect = { hits.values["pager"] = it }, dotLabel = { "Go to page ${it + 1}" }, maxDots = 5, testTag = "c.pager"),
                Modifier,
            )
            s.TopBar(TopBarSpec("Inbox", subtitle = "12 unread", collapsedFraction = 0f, testTag = "c.topbar.large"), Modifier)
            s.TopBar(TopBarSpec("Archive", subtitle = "3 threads", collapsedFraction = 1f, testTag = "c.topbar.collapsed"), Modifier)
            var qty by fields.quantity
            s.Stepper(StepperSpec(qty, { qty = it; hits.hit("stepper") }, "Quantity", "Remove one", "Add one", min = 1, max = 5, testTag = "c.stepper"), Modifier)
            s.Stepper(StepperSpec(1, { hits.hit("stepper.min") }, "At minimum", "Fewer at minimum", "More at minimum", min = 1, max = 5, testTag = "c.stepper.min"), Modifier)
            s.Stepper(StepperSpec(5, { hits.hit("stepper.max") }, "At maximum", "Fewer at maximum", "More at maximum", min = 1, max = 5, testTag = "c.stepper.max"), Modifier)
            s.Stepper(StepperSpec(2, { hits.hit("stepper.off") }, "Locked quantity", "Fewer locked", "More locked", enabled = false, testTag = "c.stepper.disabled"), Modifier)
            androidx.compose.foundation.layout.Box(Modifier.width(220.dp)) {
                s.ChipGroup(
                    ChipGroupSpec(
                        GROUP_CHIPS.mapIndexed { i, label -> ChipSpec(label, ChipKind.Filter, selected = i == 1, onClick = { hits.values["chipgroup.wrap"] = i }, testTag = "c.cg.wrap.$i") },
                        "Filters", ChipGroupLayout.Wrap, testTag = "c.chipgroup.wrap",
                    ),
                    Modifier,
                )
            }
            androidx.compose.foundation.layout.Box(Modifier.width(220.dp)) {
                s.ChipGroup(
                    ChipGroupSpec(
                        GROUP_CHIPS.mapIndexed { i, label -> ChipSpec(label, ChipKind.Choice, selected = i == 0, onClick = { hits.values["chipgroup.scroll"] = i }, testTag = "c.cg.scroll.$i") },
                        "Categories", ChipGroupLayout.Scroll, testTag = "c.chipgroup.scroll",
                    ),
                    Modifier,
                )
            }
            hits.values["liststatus.min"] = StapelTheme.layout.controlHeight
            androidx.compose.foundation.layout.Box(Modifier.width(300.dp).testTag("c.liststatus.box")) {
                Column {
                    s.ListStatus(ListStatusSpec(ListStatusKind.Appending, "Loading more rows", "Ignored", { hits.hit("ls.appending") }, testTag = "c.ls.appending"), Modifier)
                    s.ListStatus(ListStatusSpec(ListStatusKind.AppendFailed, "Rows failed to load", "Retry rows", { hits.hit("ls.retry") }, testTag = "c.ls.failed"), Modifier)
                    s.ListStatus(ListStatusSpec(ListStatusKind.End, "No more rows", "Ignored end", { hits.hit("ls.end") }, testTag = "c.ls.end"), Modifier)
                    s.ListStatus(ListStatusSpec(ListStatusKind.Stale, "Showing saved rows", "Refresh rows", { hits.hit("ls.stale") }, testTag = "c.ls.stale"), Modifier)
                    s.ListStatus(ListStatusSpec(ListStatusKind.Stale, "Saved rows, no action", testTag = "c.ls.stale.bare"), Modifier)
                }
            }
        }
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
    val radioPicked = mutableStateOf<Boolean?>(null)
    val search = TextFieldState("shoes")
    val masked = TextFieldState()
    val folded = TextFieldState()
    val rowSwitch = mutableStateOf(false)
    val quantity = mutableIntStateOf(3)
    val menuOpen = mutableStateOf(false)
}

/** Chip labels of the contract's chip groups: more than fit on one 220 dp row. */
private val GROUP_CHIPS = listOf("Under 50", "Free delivery", "In stock", "New", "Top rated", "On sale")

/** The tags of the nodes the slot phase renders, one or more per slot (the menu has a phase of its own). */
public val SKIN_CONTRACT_TAGS: List<String> = listOf(
    "c.button", "c.iconbutton", "c.textfield", "c.codefield", "c.banner", "c.progress", "c.surface", "c.listitem",
    "c.topbar", "c.divider", "c.snackbar", "c.toggle", "c.tabs", "c.chip", "c.avatar", "c.badge", "c.navbar", "c.navrail", "c.ptr",
    "c.select", "c.pager", "c.stepper", "c.chipgroup.wrap", "c.chipgroup.scroll",
    "c.ls.appending", "c.ls.failed", "c.ls.end", "c.ls.stale",
)

/**
 * The skin conformance test: renders every slot of [skin] and asserts the slot duties on the
 * semantics tree (clicks reach callbacks, disabled/busy/gated buttons refuse them, accessible names,
 * semantic errors, password obfuscation, digits-only codes, live regions, progress info, selection,
 * headings, toggles, dialog pane titles and dismissal rules). Throws on the first broken duty.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertSkinContract(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    mode: ThemeMode = ThemeMode.Light,
    theme: SkinContractTheme = PlainContractTheme,
) {
    val hits = Hits()
    val fields = Fields()
    var phase by mutableStateOf(Phase.Slots)
    mainClock.autoAdvance = false
    setContent { ContractScreen(skin, tokens, mode, theme, hits, fields, phase) }
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
    duty("Banner.detail") {
        // The ladder's detail line (status and request id) is visible text, not behind a toggle.
        onNodeWithText("HTTP 503 · req-42", useUnmergedTree = true).assertExists()
    }
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
    duty("PullToRefresh.action") {
        onNode(hasCustomAction("Refresh")).performCustomAction("Refresh")
        settle()
        assertEquals(1, hits["ptr"], "PullToRefresh: the refresh is an accessibility action named by the label")
    }
    duty("PullToRefresh.refreshing") {
        check(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestorTag("c.ptr.busy"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            "PullToRefresh: a progress node while refreshing"
        }
        check(onAllNodes(hasCustomAction("Refreshing list")).fetchSemanticsNodes().isEmpty()) { "PullToRefresh: no refresh action while refreshing" }
        onNodeWithText("Busy body", useUnmergedTree = true).assertExists()
    }

    assertLaterSlotDepth(hits, fields)
    assertAddedSlots(hits, fields)
    assertStepperAndChipGroup(hits, fields)
    assertListStatus(hits)

    // Menu phase
    phase = Phase.Menu
    settle()
    assertMenu(hits, fields)

    // Gesture phase
    phase = Phase.Gestures
    settle()
    duty("PullToRefresh.pull") {
        onNodeWithTag("c.ptr.swipe.content").performTouchInput { swipeDown(startY = top + 4f, endY = bottom, durationMillis = 400) }
        settle()
        settle()
        assertEquals(1, hits["ptr.swipe"], "PullToRefresh: a pull past the threshold refreshes once")
    }

    // Dialog phase
    phase = Phase.Dialogs
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

/** Scrolls [node] into the window and taps it with a real pointer event (a disabled node must ignore it). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.realClick(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
    mainClock.autoAdvance = true
    try {
        node.performScrollTo()
        waitForIdle()
        node.performClick()
        waitForIdle()
    } finally {
        mainClock.autoAdvance = false
    }
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
    } catch (e: IllegalStateException) {
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

/** Screen titles of a real flow, long enough to wrap at twice the text size on a phone (en, ru). */
public val LARGE_TEXT_TITLES: List<String> = listOf(
    "Confirm your phone number",
    "Подтвердите номер телефона",
)

/**
 * The top bar survives large text: at [fontScale] (200 % by default) on a 360 dp phone, each of
 * [LARGE_TEXT_TITLES] is laid out in full — no ellipsis, no visual overflow — and the title is not
 * clipped by the bar. One `setContent` per test: call it from its own test.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertTopBarSurvivesLargeText(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    theme: SkinContractTheme = PlainContractTheme,
    fontScale: Float = 2f,
) {
    var title by mutableStateOf(LARGE_TEXT_TITLES.first())
    setContent {
        val base = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
        ) {
            val layout = stapelLayout(tokens, 360.dp, input = StapelInput.Touch)
            theme.Wrap(skin, tokens, ThemeMode.Light, layout) {
                Column(Modifier.size(width = 360.dp, height = 800.dp)) {
                    StapelTheme.skin.TopBar(
                        TopBarSpec(title, navigation = IconButtonSpec(StapelIconIds.ArrowBack, "Go back", {}), testTag = "c.topbar"),
                        Modifier,
                    )
                }
            }
        }
    }
    for (t in LARGE_TEXT_TITLES) {
        title = t
        waitForIdle()
        duty("TopBar.largeText") {
            val node = onNode(hasText(t) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            if (layout.lineCount < 2) throw AssertionError("'$t' was expected to wrap at ${fontScale}x on 360 dp (lines = ${layout.lineCount})")
            if (layout.hasVisualOverflow || layout.isLineEllipsized(layout.lineCount - 1)) {
                throw AssertionError("TopBar title '$t' is truncated at ${fontScale}x (lines = ${layout.lineCount})")
            }
            val bar = onNodeWithTag("c.topbar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            if (node.boundsInRoot.bottom > bar.bottom + 0.5f || node.boundsInRoot.top < bar.top - 0.5f) {
                throw AssertionError("TopBar title '$t' is clipped by the bar (${node.boundsInRoot} outside $bar)")
            }
        }
    }
}


/** The duties of the later slots' depth (Toggle, Tabs, Chip, Avatar, Badge, ListItem). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertLaterSlotDepth(hits: Hits, fields: Fields) {
    // Toggle
    duty("Toggle.radio") {
        onNodeWithTag("c.toggle.radio").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected().tap()
        settle()
        assertEquals(true, fields.radioPicked.value, "Toggle: tapping a checked radio keeps it checked (reports true)")
    }
    duty("Toggle.indeterminate") {
        onNodeWithTag("c.toggle.tri").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate)).tap()
        settle()
        assertEquals(true, hits.values["tri"], "Toggle: an indeterminate checkbox reports true")
    }
    duty("Toggle.supporting") {
        onNodeWithTag("c.toggle.rich").assert(hasText("Once a month at most", substring = true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Confirm your e-mail first"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }
    duty("Toggle.disabled") {
        realClick(onNodeWithTag("c.toggle.disabled").assertIsNotEnabled())
        settle()
        assertEquals(0, hits["toggle.off"], "Toggle: disabled refuses taps")
    }

    // Tabs
    duty("Tabs.iconOnly") {
        onNode(hasContentDescription("List") and hasAnyAncestorTag("c.tabs.icons") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).tap()
        settle()
        assertEquals(1, hits.values["tabs.icons"], "Tabs: an icon-only item is named by its label and selectable")
    }
    duty("Tabs.scrollable") {
        val items = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and hasAnyAncestorTag("c.tabs.scroll")).fetchSemanticsNodes()
        assertEquals(9, items.size, "Tabs: every item of a scrollable row is reachable")
        onNode(hasText("Section 9") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).tap()
        settle()
        assertEquals(8, hits.values["tabs.scroll"], "Tabs: the last item of a scrollable row selects")
    }

    // Chip
    duty("Chip.status") {
        onNodeWithTag("c.chip.status").assertHasNoClickAction()
        onNodeWithText("Verified", useUnmergedTree = true).assertExists()
    }
    duty("Chip.remove") {
        onNodeWithContentDescription("Remove Ada").tap()
        settle()
        assertEquals(1, hits["chip.remove"], "Chip: the remove button calls onRemove")
        assertEquals(0, hits["chip.input"], "Chip: removing never calls onClick")
    }
    duty("Chip.disabled") {
        realClick(onNodeWithTag("c.chip.disabled").assertIsNotEnabled())
        settle()
        assertEquals(0, hits["chip.disabled"], "Chip: disabled refuses taps")
    }

    // Avatar
    duty("Avatar.fallback") {
        onNodeWithTag("c.avatar.fallback").assert(hasContentDescription("Fallback person"))
        onNode(hasText("AL") and hasAnyAncestorTag("c.avatar.fallback"), useUnmergedTree = true).assertExists()
        onNode(hasText("BC") and hasAnyAncestorTag("c.avatar.image"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("c.avatar.glyph").assert(hasContentDescription("Glyph person"))
    }
    duty("Avatar.size") {
        val b = onNodeWithTag("c.avatar.large").getUnclippedBoundsInRoot()
        val width = b.right - b.left
        check(kotlin.math.abs(width.value - AvatarSize.Large.dp) < 0.5f) { "Avatar: a Large avatar is ${AvatarSize.Large.dp} dp wide (was $width)" }
    }
    duty("Avatar.badge") {
        onNodeWithTag("c.avatar.large").assert(hasContentDescription("Online", substring = true)).assert(hasContentDescription("Grace Hopper", substring = true))
    }
    duty("Avatar.button") {
        onNodeWithTag("c.avatar.button").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).tap()
        settle()
        assertEquals(1, hits["avatar"], "Avatar: a clickable avatar is a button")
    }

    // Badge
    duty("Badge.max") {
        onNode(hasText("99+") and hasAnyAncestorTag("c.badge.max"), useUnmergedTree = true).assertExists()
        onAllNodesWithText("150", useUnmergedTree = true).fetchSemanticsNodes().let { check(it.isEmpty()) { "Badge: a count above max reads max+" } }
    }
    duty("Badge.zero") {
        onNodeWithText("Zero host", useUnmergedTree = true).assertExists()
        onNodeWithTag("c.badge.zero", useUnmergedTree = true).assertDoesNotExist()
    }
    duty("Badge.dot") {
        onNodeWithTag("c.badge.dot", useUnmergedTree = true).assert(hasContentDescription("New"))
    }

    // ListItem
    duty("ListItem.value") {
        onNodeWithTag("c.listitem.value").assert(hasText("-12.50", substring = true)).assert(hasText("Yesterday", substring = true))
            .assert(hasText("Coffee beans", substring = true)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val head = onNode(hasText("Coffee beans"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val value = onNode(hasText("-12.50"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        check(head.right <= value.left + 0.5.dp && value.right > value.left) { "ListItem: the value block ($value) overlaps the headline ($head)" }
    }
    duty("ListItem.toggle") {
        onNodeWithTag("c.listitem.toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff().tap()
        settle()
        assertEquals(true, fields.rowSwitch.value, "ListItem: tapping a toggle row flips the toggle")
        assertEquals(0, hits["listitem.toggle.click"], "ListItem: a toggle row does not run onClick")
    }
    duty("ListItem.disabled") {
        realClick(onNodeWithTag("c.listitem.disabled").assertIsNotEnabled())
        settle()
        assertEquals(0, hits["listitem.disabled"], "ListItem: disabled refuses clicks")
    }
}

private fun hasCustomAction(label: String): SemanticsMatcher = SemanticsMatcher("has custom action '$label'") { node ->
    node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performCustomAction(label: String) {
    val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
    action.action()
}

/** Texts of the large-text pass: long enough to wrap at twice the size on a phone, short enough to fit their line budget. */
private object Large {
    const val ROW = "Order updates"
    const val ROW_SUPPORTING = "Delivery and receipts"
    const val VALUE = "1 250,00"
    const val CHIP = "Free delivery"
    const val TOGGLE = "Remember this device"
    const val TOGGLE_SUPPORTING = "Skip the code next time"
    const val TAB_A = "Active"
    const val TAB_B = "Archived"
    const val NAV = "Account"
    const val STEPPER = "12 pcs"
    const val GROUP_CHIP = "Pick-up today"
}

/**
 * The later slots survive large text: at [fontScale] (200 % by default) on a 360 dp phone, the texts
 * of a list row (with a value block), a chip, a toggle with supporting text, tabs, the navigation
 * bar, a stepper's value and a chip group's chips are laid out in full — no ellipsis, no visual overflow — inside their slot's bounds (nothing
 * clipped by a fixed-height container), and a row's value block does not overlap its headline. One
 * `setContent` per test: call it from its own test.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertSlotsSurviveLargeText(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    theme: SkinContractTheme = PlainContractTheme,
    fontScale: Float = 2f,
) {
    setContent {
        val base = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
        ) {
            val layout = stapelLayout(tokens, 360.dp, input = StapelInput.Touch)
            theme.Wrap(skin, tokens, ThemeMode.Light, layout) {
                val s = StapelTheme.skin
                Column(Modifier.size(width = 360.dp, height = 2400.dp)) {
                    s.ListItem(
                        ListItemSpec(Large.ROW, supporting = Large.ROW_SUPPORTING, onClick = {}, value = ListItemValue(Large.VALUE), accessory = ListItemAccessory.Chevron, testTag = "l.row"),
                        Modifier,
                    )
                    s.Chip(ChipSpec(Large.CHIP, ChipKind.Filter, selected = true, onClick = {}, testTag = "l.chip"), Modifier)
                    s.Toggle(ToggleSpec(ToggleKind.Switch, true, {}, Large.TOGGLE, supporting = Large.TOGGLE_SUPPORTING, controlAtEnd = true, testTag = "l.toggle"), Modifier.fillMaxWidth())
                    s.Tabs(TabsSpec(listOf(Large.TAB_A, Large.TAB_B), 0, {}, testTag = "l.tabs"), Modifier.fillMaxWidth())
                    s.NavigationBar(
                        NavigationSpec(
                            listOf(NavigationItemSpec("a", "Home", StapelIconIds.Home), NavigationItemSpec("b", Large.NAV, StapelIconIds.User)),
                            selectedId = "a", onSelect = {},
                        ),
                        Modifier.testTag("l.nav"),
                    )
                    s.Stepper(StepperSpec(12, {}, "Quantity", "Remove one", "Add one", valueText = Large.STEPPER, testTag = "l.stepper"), Modifier)
                    s.ChipGroup(
                        ChipGroupSpec(listOf(ChipSpec("New", onClick = {}), ChipSpec(Large.GROUP_CHIP, onClick = {}), ChipSpec("Sale", onClick = {})), "Filters", testTag = "l.chipgroup"),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    waitForIdle()
    val checks = listOf(
        "ListItem" to listOf("l.row" to Large.ROW, "l.row" to Large.ROW_SUPPORTING, "l.row" to Large.VALUE),
        "Chip" to listOf("l.chip" to Large.CHIP),
        "Toggle" to listOf("l.toggle" to Large.TOGGLE, "l.toggle" to Large.TOGGLE_SUPPORTING),
        "Tabs" to listOf("l.tabs" to Large.TAB_A, "l.tabs" to Large.TAB_B),
        "NavigationBar" to listOf("l.nav" to Large.NAV),
        "Stepper" to listOf("l.stepper" to Large.STEPPER),
        "ChipGroup" to listOf("l.chipgroup" to Large.GROUP_CHIP),
    )
    for ((slot, texts) in checks) {
        duty("$slot.largeText") {
            for ((tag, text) in texts) {
                val node = onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                // `didOverflowWidth` is also true for a short single line (paragraph width = max width), so
                // width overflow is measured from the lines themselves.
                val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
                if (layout.didOverflowHeight || layout.isLineEllipsized(layout.lineCount - 1) || widest > layout.size.width + 0.5f) {
                    throw AssertionError("$slot text '$text' is truncated at ${fontScale}x (lines = ${layout.lineCount})")
                }
                val box = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val b = node.boundsInRoot
                if (b.top < box.top - 0.5f || b.bottom > box.bottom + 0.5f || b.left < box.left - 0.5f || b.right > box.right + 0.5f) {
                    throw AssertionError("$slot text '$text' ($b) is clipped by its slot ($box) at ${fontScale}x")
                }
            }
        }
    }
    duty("ListItem.largeText") {
        val head = onNode(hasText(Large.ROW), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = onNode(hasText(Large.VALUE), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        check(head.right <= value.left + 0.5f) { "ListItem: at ${fontScale}x the value block ($value) overlaps the headline ($head)" }
    }
}

/** Upper-cases what is typed (stands for any caller's input mask). */
private object UpperCase : androidx.compose.foundation.text.input.InputTransformation {
    override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformInput() {
        val upper = toString().uppercase()
        if (upper != toString()) replace(0, length, upper)
    }
}

/** Shows a dash after the third character (stands for any caller's display format). */
private object DashAfterThree : androidx.compose.foundation.text.input.OutputTransformation {
    override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformOutput() {
        if (length > 3) replace(3, 3, "-")
    }
}

/** Turns Arabic-Indic digits into ASCII ones (a code typed on an Arabic keyboard). */
private object FoldArabicIndicDigits : androidx.compose.foundation.text.input.InputTransformation {
    override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformInput() {
        val folded = toString().map { if (it in '\u0660'..'\u0669') '0' + (it - '\u0660') else it }.joinToString("")
        if (folded != toString()) replace(0, length, folded)
    }
}

/** The duties of the slots added after the first twenty (search field, Select, PagerIndicator, large TopBar). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertAddedSlots(hits: Hits, fields: Fields) {
    duty("TextField.transformation") {
        onNodeWithTag("c.textfield.mask").performTextInput("abcdef")
        settle()
        assertEquals("ABCDEF", fields.masked.text.toString(), "TextField: the caller's input transformation shapes the value")
        onNodeWithTag("c.textfield.mask").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("ABC-DEF")))
    }
    duty("CodeField.transformation") {
        onNodeWithTag("c.codefield.fold").performTextInput("Code: \u0664\u0662\u0667\u0661")
        settle()
        assertEquals("4271", fields.folded.text.toString(), "CodeField: the caller's transformation runs before the digit filter")
    }
    duty("TextField.search") {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) or hasContentDescription("Loading", substring = true), useUnmergedTree = true)
        check(
            onAllNodes(hasAnyAncestorTag("c.textfield.search") and (SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) or hasContentDescription("Loading", substring = true)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasTestTag("c.textfield.search") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        ) { "TextField: a loading search field shows a progress node" }
        onNodeWithTag("c.textfield.search").performTextInput(" red")
        settle()
        assertEquals("shoes red", fields.search.text.toString(), "TextField: a loading search field stays editable")
        onNodeWithContentDescription("Clear search").tap()
        settle()
        assertEquals("", fields.search.text.toString(), "TextField: the clear action empties the field")
        check(onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().isEmpty()) { "TextField: no clear action on an empty field" }
    }
    duty("Select") {
        onNodeWithTag("c.select").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assert(hasContentDescription("Language", substring = true)).assert(hasText("English", substring = true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Pick a supported language")).tap()
        settle()
        assertEquals(1, hits["select"], "Select: a tap opens the picker")
        onNodeWithTag("c.select.empty").assert(hasText("Choose a currency", substring = true))
        realClick(onNodeWithTag("c.select.disabled").assertIsNotEnabled())
        assertEquals(0, hits["select.disabled"], "Select: disabled refuses taps")
    }
    duty("PagerIndicator") {
        onNodeWithTag("c.pager").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 7 of 12"))
        val dots = onAllNodes(hasContentDescription("Go to page", substring = true) and hasAnyAncestorTag("c.pager")).fetchSemanticsNodes()
        assertEquals(5, dots.size, "PagerIndicator: at most maxDots dots")
        onNodeWithContentDescription("Go to page 8").tap()
        settle()
        assertEquals(7, hits.values["pager"], "PagerIndicator: a dot jumps to its page")
    }
    duty("TopBar.large") {
        val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasAnyAncestorTag("c.topbar.large"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, headings.size, "TopBar: the title is the one heading of a large bar")
        onNodeWithText("12 unread", useUnmergedTree = true).assertExists()
        val large = onNodeWithTag("c.topbar.large.large", useUnmergedTree = true).getUnclippedBoundsInRoot()
        check(large.bottom - large.top > 1.dp) { "TopBar: an expanded large bar draws the large title block" }
        onNodeWithTag("c.topbar.collapsed.large", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("3 threads", useUnmergedTree = true).assertExists()
    }
}

/**
 * A skeleton honours reduce motion: with `LocalStapelReduceMotion` on, two captures of a skeleton
 * 700 ms apart are identical (a still placeholder); with it off, they differ (it shimmers). One
 * `setContent` per test: call it from its own test.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertSkeletonRespectsReduceMotion(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    theme: SkinContractTheme = PlainContractTheme,
) {
    mainClock.autoAdvance = false
    setContent {
        val layout = stapelLayout(tokens, 390.dp, input = StapelInput.Touch)
        theme.Wrap(skin, tokens, ThemeMode.Light, layout) {
            Column(Modifier.size(width = 390.dp, height = 400.dp)) {
                CompositionLocalProvider(LocalStapelReduceMotion provides true) {
                    StapelTheme.skin.Progress(ProgressSpec(ProgressKind.Skeleton, "Loading", skeleton = SkeletonShape.Block, testTag = "k.still"), Modifier.height(80.dp))
                }
                CompositionLocalProvider(LocalStapelReduceMotion provides false) {
                    StapelTheme.skin.Progress(ProgressSpec(ProgressKind.Skeleton, "Loading", skeleton = SkeletonShape.Block, testTag = "k.moving"), Modifier.height(80.dp))
                }
            }
        }
    }
    mainClock.advanceTimeBy(50)
    val still = onNodeWithTag("k.still").captureToImage().toPixelMap()
    val moving = onNodeWithTag("k.moving").captureToImage().toPixelMap()
    var stillMoved = false
    var movingMoved = false
    // Sample a few frames across two seconds: a shimmer is visible in some of them, a still fill in none.
    repeat(12) {
        mainClock.advanceTimeBy(170)
        if (!samePixels(still, onNodeWithTag("k.still").captureToImage().toPixelMap())) stillMoved = true
        if (!samePixels(moving, onNodeWithTag("k.moving").captureToImage().toPixelMap())) movingMoved = true
    }
    duty("Progress.reduceMotion") {
        check(!stillMoved) { "Progress: a skeleton moves although reduce motion is on" }
    }
    duty("Progress.shimmer") {
        check(movingMoved) { "Progress: a skeleton is still although reduce motion is off" }
    }
}

private fun samePixels(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    for (x in 0 until a.width step 3) for (y in 0 until a.height step 3) if (a[x, y] != b[x, y]) return false
    return true
}

/** The duties of Stepper and ChipGroup. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertStepperAndChipGroup(hits: Hits, fields: Fields) {
    duty("Stepper") {
        onNodeWithTag("c.stepper").assert(hasContentDescription("Quantity", substring = true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3"))
        onNode(hasText("3") and hasAnyAncestorTag("c.stepper"), useUnmergedTree = true).assertExists()
        onNode(hasContentDescription("Add one") and hasAnyAncestorTag("c.stepper"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).tap()
        settle()
        assertEquals(4, fields.quantity.intValue, "Stepper: increment moves the value by one step")
        onNode(hasContentDescription("Remove one") and hasAnyAncestorTag("c.stepper")).tap()
        settle()
        onNode(hasContentDescription("Remove one") and hasAnyAncestorTag("c.stepper")).tap()
        settle()
        assertEquals(2, fields.quantity.intValue, "Stepper: decrement moves the value by one step")
        assertEquals(3, hits["stepper"], "Stepper: one onValueChange per tap")
    }
    duty("Stepper.bounds") {
        realClick(onNode(hasContentDescription("Fewer at minimum") and hasAnyAncestorTag("c.stepper.min")).assertIsNotEnabled())
        onNode(hasContentDescription("More at minimum") and hasAnyAncestorTag("c.stepper.min")).assertIsEnabled()
        realClick(onNode(hasContentDescription("More at maximum") and hasAnyAncestorTag("c.stepper.max")).assertIsNotEnabled())
        onNode(hasContentDescription("Fewer at maximum") and hasAnyAncestorTag("c.stepper.max")).assertIsEnabled()
        assertEquals(0, hits["stepper.min"] + hits["stepper.max"], "Stepper: a bound refuses the step past it")
    }
    duty("Stepper.disabled") {
        realClick(onNode(hasContentDescription("More locked") and hasAnyAncestorTag("c.stepper.disabled")).assertIsNotEnabled())
        onNode(hasContentDescription("Fewer locked") and hasAnyAncestorTag("c.stepper.disabled")).assertIsNotEnabled()
        assertEquals(0, hits["stepper.off"], "Stepper: disabled refuses both buttons")
    }
    duty("ChipGroup.wrap") {
        onNodeWithTag("c.chipgroup.wrap").assert(hasContentDescription("Filters", substring = true))
        val group = onNodeWithTag("c.chipgroup.wrap").getUnclippedBoundsInRoot()
        val chips = GROUP_CHIPS.indices.map { onNodeWithTag("c.cg.wrap.$it").getUnclippedBoundsInRoot() }
        chips.forEachIndexed { i, b ->
            check(b.left >= group.left - 0.5.dp && b.right <= group.right + 0.5.dp) { "ChipGroup: wrapped chip $i ($b) leaves the group ($group)" }
        }
        check(chips.map { it.top }.distinct().size > 1) { "ChipGroup: six chips on 220 dp wrap onto more than one row" }
        onNodeWithTag("c.cg.wrap.4").tap()
        settle()
        assertEquals(4, hits.values["chipgroup.wrap"], "ChipGroup: a chip's click reaches its callback")
        onNodeWithTag("c.cg.wrap.1").assertIsSelected()
    }
    duty("ChipGroup.scroll") {
        onNodeWithTag("c.chipgroup.scroll").assert(hasContentDescription("Categories", substring = true))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        val tops = GROUP_CHIPS.indices.map { onNodeWithTag("c.cg.scroll.$it").getUnclippedBoundsInRoot().top }
        check(tops.distinct().size == 1) { "ChipGroup: a scrolling group keeps one row (tops $tops)" }
        val last = onNodeWithTag("c.cg.scroll.${GROUP_CHIPS.lastIndex}")
        mainClock.autoAdvance = true
        try {
            last.performScrollTo()
            waitForIdle()
        } finally {
            mainClock.autoAdvance = false
        }
        last.tap()
        settle()
        assertEquals(GROUP_CHIPS.lastIndex, hits.values["chipgroup.scroll"], "ChipGroup: the last chip of a scrolling row is reachable")
    }
}

/** The duties of the ListStatus slot (the status rows of a paged list). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertListStatus(hits: Hits) {
    fun under(tag: String) = hasTestTag(tag) or hasAnyAncestorTag(tag)
    fun progressUnder(tag: String) = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and under(tag), useUnmergedTree = true)
        .fetchSemanticsNodes()
    fun actionsUnder(tag: String) = onAllNodes(androidx.compose.ui.test.hasClickAction() and under(tag), useUnmergedTree = true).fetchSemanticsNodes()
    duty("ListStatus.width") {
        val box = onNodeWithTag("c.liststatus.box").getUnclippedBoundsInRoot()
        val min = hits.values["liststatus.min"] as androidx.compose.ui.unit.Dp
        for (tag in listOf("c.ls.appending", "c.ls.failed", "c.ls.end", "c.ls.stale", "c.ls.stale.bare")) {
            val b = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            check(b.left <= box.left + 0.5.dp && b.right >= box.right - 0.5.dp) { "ListStatus: $tag ($b) does not fill the list's width ($box)" }
            check(b.bottom - b.top >= min - 0.5.dp) { "ListStatus: $tag is ${b.bottom - b.top} tall, under the control height $min" }
        }
    }
    duty("ListStatus.appending") {
        onNode(hasText("Loading more rows") and under("c.ls.appending"), useUnmergedTree = true).assertExists()
        val progress = progressUnder("c.ls.appending")
        check(progress.isNotEmpty()) { "ListStatus: appending shows a progress node" }
        check(progress.any { it.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.contains("Loading more rows") == true }) {
            "ListStatus: the appending progress is named by the message"
        }
        check(actionsUnder("c.ls.appending").isEmpty()) { "ListStatus: appending offers no action" }
    }
    duty("ListStatus.failed") {
        onNode(hasText("Rows failed to load") and under("c.ls.failed"), useUnmergedTree = true).assertExists()
        check(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and under("c.ls.failed"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            "ListStatus: a failed page is announced (a polite live region)"
        }
        onNode(hasText("Retry rows") and hasAnyAncestorTag("c.ls.failed") or (hasContentDescription("Retry rows") and hasAnyAncestorTag("c.ls.failed")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).tap()
        settle()
        assertEquals(1, hits["ls.retry"], "ListStatus: the retry reaches its callback once")
    }
    duty("ListStatus.end") {
        onNode(hasText("No more rows") and under("c.ls.end"), useUnmergedTree = true).assertExists()
        check(progressUnder("c.ls.end").isEmpty()) { "ListStatus: the end shows no progress" }
        check(actionsUnder("c.ls.end").isEmpty()) { "ListStatus: the end offers no action" }
    }
    duty("ListStatus.stale") {
        onNode(hasText("Showing saved rows") and under("c.ls.stale"), useUnmergedTree = true).assertExists()
        onNode(hasText("Refresh rows") and hasAnyAncestorTag("c.ls.stale")).tap()
        settle()
        assertEquals(1, hits["ls.stale"], "ListStatus: the stale row's action reaches its callback")
        check(actionsUnder("c.ls.stale.bare").isEmpty()) { "ListStatus: a stale row without an action shows no button" }
        assertEquals(0, hits["ls.appending"] + hits["ls.end"], "ListStatus: appending and end never run an action")
    }
}

/** The duties of the Menu slot (its own phase: an open menu covers the screen). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertMenu(hits: Hits, fields: Fields) {
    duty("Menu.collapsed") {
        check(onAllNodesWithText("Reply", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) { "Menu: a collapsed menu has no item nodes" }
    }
    onNodeWithTag("c.menu.anchor").tap()
    settle()
    duty("Menu.pane") {
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Message actions"), useUnmergedTree = true).assertExists()
        onNodeWithText("Reply").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        onNodeWithText("Delete").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
    duty("Menu.position") {
        val anchor = onNodeWithTag("c.menu.anchor").fetchSemanticsNode().boundsInWindow
        val pane = onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Message actions"), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        check(pane.left < anchor.right && pane.right > anchor.left) { "Menu: the menu ($pane) is not at its anchor ($anchor) horizontally" }
        check(pane.top >= anchor.bottom - 1f || pane.bottom <= anchor.top + 1f) { "Menu: the menu ($pane) covers its anchor ($anchor)" }
        check(pane.left >= -0.5f) { "Menu: the menu ($pane) leaves the window" }
    }
    duty("Menu.disabled") {
        onNodeWithText("Forward").assertIsNotEnabled()
        tapForReal(onNodeWithText("Forward"))
        assertEquals(0, hits["menu.forward"], "Menu: a disabled item refuses taps")
    }
    duty("Menu.select") {
        onNodeWithText("Reply").tap()
        settle()
        assertEquals(1, hits["menu.reply"], "Menu: a tap runs the item")
        assertEquals(1, hits["menu.dismiss"], "Menu: a tap closes the menu once")
        assertEquals(false, fields.menuOpen.value)
        check(onAllNodesWithText("Reply", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) { "Menu: a closed menu leaves no item nodes" }
    }
    duty("Menu.dismiss") {
        onNodeWithTag("c.menu.anchor").tap()
        settle()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Message actions"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.Dismiss)
        settle()
        assertEquals(2, hits["menu.dismiss"], "Menu: the pane's dismiss action closes the menu")
        assertEquals(0, hits["menu.delete"], "Menu: dismissing runs no item")
    }
}

/** A real pointer tap on [node] where it is (no scrolling: menu items sit in a popup). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.tapForReal(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
    mainClock.autoAdvance = true
    try {
        node.performClick()
        waitForIdle()
    } finally {
        mainClock.autoAdvance = false
    }
}
/** Centre x of a node (unclipped, in root coordinates). */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.centerX(matcher: SemanticsMatcher): Float {
    val b = onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return ((b.left + b.right) / 2).value
}

/**
 * The slots lay out right to left: with the strings locale `ar` (the theme derives `LayoutDirection.Rtl`
 * from it) on a 360 dp phone, start and end swap
 * in every slot where order or placement carries meaning — the top bar's navigation sits right of the
 * title and its actions left; a row's value block and chevron sit left of the headline; a banner's
 * dismiss and a removable chip's remove button sit left of their text; the first tab, navigation item,
 * pager dot and a stepper's decrement are rightmost; a field's trailing action sits on the left; a directional
 * glyph (`autoMirror`) is drawn mirrored, also when the icon set's vector does not say so. Digits of a
 * code field stay left to right (a number is read left to right in every script). One `setContent`
 * per test: call it from its own test.
 */
@OptIn(ExperimentalTestApi::class)
public fun ComposeUiTest.assertSlotsMirrorInRtl(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    theme: SkinContractTheme = PlainContractTheme,
) {
    val code = TextFieldState()
    val label = TextFieldState("shoes")
    setContent {
        val layout = stapelLayout(tokens, 360.dp, input = StapelInput.Touch)
        // Right to left the way an app gets there: the active strings locale is Arabic and the theme
        // derives the direction from it (the platform direction says LTR and must not win).
        val arabic = androidx.compose.runtime.remember { stapel.i18n.StapelStrings { initialLocale = "ar"; register(stapel.i18n.CoreUiBundle) } }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            stapel.ui.LocalStapelStrings provides arabic,
        ) {
            theme.Wrap(skin, tokens, ThemeMode.Light, layout) {
                val s = StapelTheme.skin
                Column(Modifier.size(width = 360.dp, height = 2000.dp)) {
                    s.TopBar(
                        TopBarSpec(
                            "Inbox", navigation = IconButtonSpec(StapelIconIds.ArrowBack, "Go back", {}),
                            actions = listOf(IconButtonSpec(StapelIconIds.Settings, "Bar settings", {})), testTag = "r.topbar",
                        ),
                        Modifier,
                    )
                    s.ListItem(
                        ListItemSpec("Coffee beans", onClick = {}, value = ListItemValue("-12.50"), accessory = ListItemAccessory.Chevron, testTag = "r.row"),
                        Modifier.fillMaxWidth(),
                    )
                    s.Banner(BannerSpec(StapelTone.Info, "Banner text", onDismiss = {}, dismissLabel = "Dismiss banner", testTag = "r.banner"), Modifier)
                    s.Chip(ChipSpec("Ada", ChipKind.Input, onClick = {}, onRemove = {}, removeLabel = "Remove Ada", testTag = "r.chip"), Modifier)
                    s.Tabs(TabsSpec(listOf("First", "Second"), 0, {}, testTag = "r.tabs"), Modifier.fillMaxWidth())
                    s.NavigationBar(
                        NavigationSpec(listOf(NavigationItemSpec("a", "Alpha", StapelIconIds.Home), NavigationItemSpec("b", "Beta", StapelIconIds.User)), "a", {}),
                        Modifier.testTag("r.nav"),
                    )
                    s.Stepper(StepperSpec(2, {}, "Quantity", "Remove one", "Add one", testTag = "r.stepper"), Modifier)
                    s.PagerIndicator(PagerIndicatorSpec(3, 0, "Page 1 of 3", onSelect = {}, dotLabel = { "Dot ${it + 1}" }, testTag = "r.pager"), Modifier)
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().testTag("r.fieldbox")) {
                        s.TextField(TextFieldSpec(label, "Search products", TextFieldKind.Search, clearLabel = "Clear the search", testTag = "r.field"), Modifier.fillMaxWidth())
                    }
                    s.CodeField(CodeFieldSpec(code, 4, "Code", testTag = "r.code"), Modifier)
                    Row {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            s.IconButton(IconButtonSpec(StapelIconIds.ArrowBack, "Back ltr", {}, testTag = "r.icon.ltr"), Modifier)
                        }
                        s.IconButton(IconButtonSpec(StapelIconIds.ArrowBack, "Back rtl", {}, testTag = "r.icon.rtl"), Modifier)
                    }
                }
            }
        }
    }
    waitForIdle()
    duty("TopBar.rtl") {
        val title = centerX(hasText("Inbox") and hasAnyAncestorTag("r.topbar"))
        check(centerX(hasContentDescription("Go back")) > title) { "TopBar: in RTL the navigation sits right of the title" }
        check(centerX(hasContentDescription("Bar settings")) < title) { "TopBar: in RTL the actions sit left of the title" }
    }
    duty("ListItem.rtl") {
        check(centerX(hasText("-12.50") and hasAnyAncestorTag("r.row")) < centerX(hasText("Coffee beans") and hasAnyAncestorTag("r.row"))) {
            "ListItem: in RTL the value block sits left of the headline"
        }
    }
    duty("Banner.rtl") {
        check(centerX(hasContentDescription("Dismiss banner")) < centerX(hasText("Banner text"))) { "Banner: in RTL dismiss sits left of the message" }
    }
    duty("Chip.rtl") {
        check(centerX(hasContentDescription("Remove Ada")) < centerX(hasText("Ada") and hasAnyAncestorTag("r.chip"))) { "Chip: in RTL the remove button sits left of the label" }
    }
    duty("Tabs.rtl") {
        check(centerX(hasText("First") and hasAnyAncestorTag("r.tabs")) > centerX(hasText("Second") and hasAnyAncestorTag("r.tabs"))) { "Tabs: in RTL the first tab is rightmost" }
    }
    duty("NavigationBar.rtl") {
        check(centerX(hasText("Alpha") and hasAnyAncestorTag("r.nav")) > centerX(hasText("Beta") and hasAnyAncestorTag("r.nav"))) { "NavigationBar: in RTL the first item is rightmost" }
    }
    duty("Stepper.rtl") {
        check(centerX(hasContentDescription("Remove one")) > centerX(hasContentDescription("Add one"))) { "Stepper: in RTL the decrement (start) is rightmost" }
    }
    duty("PagerIndicator.rtl") {
        check(centerX(hasContentDescription("Dot 1")) > centerX(hasContentDescription("Dot 3"))) { "PagerIndicator: in RTL the first page's dot is rightmost" }
    }
    duty("TextField.rtl") {
        val box = onNodeWithTag("r.fieldbox", useUnmergedTree = true).getUnclippedBoundsInRoot()
        check(centerX(hasContentDescription("Clear the search")) < ((box.left + box.right) / 2).value) { "TextField: in RTL the trailing clear action sits on the left" }
    }
    duty("CodeField.digitsLeftToRight") {
        onNodeWithTag("r.code").performTextInput("1234")
        waitForIdle()
        check(centerX(hasText("1") and hasAnyAncestorTag("r.code")) < centerX(hasText("4") and hasAnyAncestorTag("r.code"))) {
            "CodeField: digits of a code read left to right in RTL too"
        }
    }
    duty("Icon.mirror") {
        val ltr = onNodeWithTag("r.icon.ltr").captureToImage().toPixelMap()
        val rtl = onNodeWithTag("r.icon.rtl").captureToImage().toPixelMap()
        check(ltr.width == rtl.width && ltr.height == rtl.height) { "Icon: sizes differ" }
        var differ = 0
        var mirrored = 0
        for (x in 0 until ltr.width) for (y in 0 until ltr.height) {
            if (ltr[x, y] != rtl[x, y]) differ++
            if (ltr[x, y] != rtl[ltr.width - 1 - x, y]) mirrored++
        }
        check(differ > 0 && mirrored * 10 < differ) { "Icon: a directional glyph is drawn mirrored in RTL (differ $differ, unmirrored $mirrored)" }
    }
}
