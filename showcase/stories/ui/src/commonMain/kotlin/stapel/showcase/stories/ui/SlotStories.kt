package stapel.showcase.stories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import stapel.showcase.AnatomyPart
import stapel.showcase.CanvasOptions
import stapel.showcase.Knob
import stapel.showcase.Story
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.showcase.StoryTarget
import stapel.ui.StapelAvatar
import stapel.ui.StapelBadge
import stapel.ui.StapelBanner
import stapel.ui.StapelButton
import stapel.ui.StapelChip
import stapel.ui.StapelCodeField
import stapel.ui.StapelDialog
import stapel.ui.StapelDivider
import stapel.ui.StapelIconButton
import stapel.ui.StapelIconIds
import stapel.ui.StapelListItem
import stapel.ui.StapelNavigationBar
import stapel.ui.StapelNavigationRail
import stapel.ui.StapelPagerIndicator
import stapel.ui.StapelMenu
import stapel.ui.StapelStepper
import stapel.ui.StapelChipGroup
import stapel.ui.skin.ChipGroupLayout
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.MenuItemSpec
import androidx.compose.ui.Alignment
import stapel.ui.StapelProgress
import stapel.ui.StapelPullToRefresh
import stapel.ui.StapelSelect
import stapel.ui.StapelSurface
import stapel.ui.StapelTabs
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelToggle
import stapel.ui.StapelTone
import stapel.ui.StapelTopBar
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeEmphasis
import stapel.ui.skin.BadgePlacement
import stapel.ui.skin.BadgeSize
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ControlSize
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.IconButtonSpec
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemMedia
import stapel.ui.skin.ListItemValue
import stapel.ui.skin.NavigationItemSpec
import stapel.ui.skin.ProgressKind
import stapel.ui.skin.SkeletonShape
import stapel.ui.skin.SnackbarSpec
import stapel.ui.skin.SurfaceTone
import stapel.ui.skin.TabsVariant
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.ToggleKind

private fun slot(name: String) = setOf(StoryTarget.Slot(name))

private val buttonVariant = Knob.enumOf("variant", "Variant", ButtonVariant.Primary)
private val buttonSize = Knob.enumOf("size", "Size", ControlSize.Regular)
private val buttonLabel = Knob.Text("label", "Label", "Continue")
private val enabled = Knob.Flag("enabled", "Enabled", true)
private val loading = Knob.Flag("loading", "Loading")
private val gated = Knob.Text("gated", "Gated reason", null)
private val fill = Knob.Flag("fill", "Fill width")

private val button = Story(
    "button", "Button", "Slots/Actions",
    description = "The one call to action of a form or page: variants, sizes, loading (refuses a second click) and gated.",
    knobs = listOf(buttonVariant, buttonSize, buttonLabel, enabled, loading, gated, fill),
    states = listOf(
        StoryState("default", "Primary"),
        StoryState("secondary", "Secondary", mapOf("variant" to ButtonVariant.Secondary)),
        StoryState("danger", "Danger", mapOf("variant" to ButtonVariant.Danger, "label" to "Delete account")),
        StoryState("loading", "Loading", mapOf("loading" to true)),
        StoryState("gated", "Gated", mapOf("gated" to "Confirm your e-mail first")),
    ),
    covers = slot("Button"),
    anatomy = listOf(AnatomyPart("container", "Container", "ButtonSpec.variant, size, enabled")),
    snippet = "StapelButton(\"Continue\", onClick = submit, loading = busy, fillWidth = true)",
) {
    StapelButton(
        value(buttonLabel) ?: "", { event("click") }, Modifier.anatomy("container"),
        variant = value(buttonVariant), size = value(buttonSize), enabled = value(enabled), loading = value(loading),
        gatedReason = value(gated), fillWidth = value(fill),
    )
}

private val iconTone = Knob.enumOf("tone", "Tone", StapelTone.Neutral)

private val iconButton = Story(
    "icon-button", "Icon button", "Slots/Actions",
    description = "An icon with an accessible name; the touch target meets the platform floor.",
    knobs = listOf(iconTone, enabled),
    covers = slot("IconButton"),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(StapelIconIds.ArrowBack, StapelIconIds.Settings, StapelIconIds.Bell, StapelIconIds.Search).forEach {
            StapelIconButton(it, it.id, { event("click ${it.id}") }, enabled = value(enabled), tone = value(iconTone))
        }
    }
}

private val fieldKind = Knob.enumOf("kind", "Kind", TextFieldKind.Email)
private val fieldValue = Knob.Text("value", "Value", "ada@example.com")
private val supporting = Knob.Text("supporting", "Supporting text", "We never share it", optional = true)
private val error = Knob.Text("error", "Error", null)

private val textField = Story(
    "text-field", "Text field", "Slots/Inputs",
    description = "Label, supporting text, error, kind (keyboard, autofill, obfuscation) and the search kind's clear action.",
    knobs = listOf(fieldKind, fieldValue, supporting, error, enabled, loading),
    states = listOf(
        StoryState("default", "E-mail"),
        StoryState("error", "Error", mapOf("kind" to TextFieldKind.Phone, "value" to "12", "error" to "Enter the full number", "supporting" to null)),
        StoryState("password", "Password", mapOf("kind" to TextFieldKind.Password, "value" to "secret", "supporting" to null)),
        StoryState("search", "Search, loading", mapOf("kind" to TextFieldKind.Search, "value" to "running shoes", "loading" to true, "supporting" to null)),
        StoryState("rtl", "Search, right to left", mapOf("kind" to TextFieldKind.Search, "value" to "أحذية جري", "supporting" to null), rtl = true, locale = "ar"),
    ),
    covers = slot("TextField"),
    anatomy = listOf(AnatomyPart("field", "Field", "label, value, supporting text or error")),
) {
    val text = value(fieldValue) ?: ""
    val state = remember(text) { TextFieldState(text) }
    StapelTextField(
        state, value(fieldKind).name, Modifier.fillMaxWidth().anatomy("field"), kind = value(fieldKind),
        supportingText = value(supporting), error = value(error), enabled = value(enabled), loading = value(loading),
    )
}

private val codeLength = Knob.Number("length", "Length", 4..8, 6)
private val codeValue = Knob.Text("value", "Typed", "12")

private val codeField = Story(
    "code-field", "Code field", "Slots/Inputs",
    description = "A fixed-length one-time code: one editable node, cells drawn per digit.",
    knobs = listOf(codeLength, codeValue, error, enabled),
    states = listOf(
        StoryState("default", "Typing"),
        StoryState("error", "Wrong code", mapOf("value" to "123456", "error" to "Wrong code, 2 attempts left")),
        StoryState("rtl", "Right to left: the digits still read left to right", mapOf("value" to "1234"), rtl = true, locale = "ar"),
    ),
    covers = slot("CodeField"),
) {
    val typed = value(codeValue) ?: ""
    val state = remember(typed) { TextFieldState(typed) }
    StapelCodeField(state, value(codeLength), "Code", error = value(error), enabled = value(enabled))
}

private val dialogOpen = Knob.Flag("open", "Open")

private val dialog = Story(
    "dialog", "Dialog", "Slots/Containment",
    description = "A sheet on a phone, a modal on wider windows (the layout decides); dismissible with a named action.",
    knobs = listOf(dialogOpen),
    covers = slot("Dialog"),
) {
    var open by remember(value(dialogOpen)) { mutableStateOf(value(dialogOpen)) }
    StapelButton("Open dialog", { open = true; event("open") }, variant = ButtonVariant.Secondary)
    StapelText("Surface on this window: ${StapelTheme.layout.dialogSurface}", style = StapelTheme.type.caption)
    if (open) {
        StapelDialog(
            { open = false; event("dismiss") }, title = "Sign out everywhere?",
            footer = { StapelButton("Sign out", { open = false; event("confirm") }, variant = ButtonVariant.Danger) },
        ) { StapelText("Every other session ends now.") }
    }
}

private val bannerTone = Knob.enumOf("tone", "Tone", StapelTone.Info)
private val bannerDetail = Knob.Text("detail", "Technical detail", null)
private val dismissible = Knob.Flag("dismissible", "Dismissible")

private val banner = Story(
    "banner", "Banner", "Slots/Feedback",
    description = "An inline message in one of the tones; errors may carry a technical line.",
    knobs = listOf(bannerTone, bannerDetail, dismissible),
    states = listOf(
        StoryState("default", "Info"),
        StoryState("error", "Error with detail", mapOf("tone" to StapelTone.Error, "detail" to "HTTP 503")),
        StoryState("success", "Success", mapOf("tone" to StapelTone.Success, "dismissible" to true)),
        StoryState("rtl", "Error, right to left", mapOf("tone" to StapelTone.Error, "detail" to "HTTP 503", "dismissible" to true), rtl = true, locale = "ar"),
    ),
    covers = slot("Banner"),
) {
    val tone = value(bannerTone)
    StapelBanner(
        tone, "A ${tone.name.lowercase()} message.", title = tone.name, detail = value(bannerDetail),
        onDismiss = if (value(dismissible)) ({ event("dismiss") }) else null,
    )
}

private val progressKind = Knob.enumOf("kind", "Kind", ProgressKind.Spinner)
private val progressFraction = Knob.Number("fraction", "Percent (linear)", 0..100, 60, 10)
private val skeleton = Knob.enumOf("skeleton", "Skeleton shape", SkeletonShape.Text)

private val progress = Story(
    "progress", "Progress", "Slots/Feedback",
    description = "Spinner, linear (determinate or not) and skeleton placeholders; still under reduced motion.",
    knobs = listOf(progressKind, progressFraction, skeleton),
    states = listOf(
        StoryState("default", "Spinner"),
        StoryState("linear", "Linear 60 %", mapOf("kind" to ProgressKind.Linear)),
        StoryState("skeleton", "Skeleton lines", mapOf("kind" to ProgressKind.Skeleton)),
    ),
    covers = slot("Progress"),
) {
    val kind = value(progressKind)
    StapelProgress(
        kind, if (kind == ProgressKind.Skeleton && value(skeleton) == SkeletonShape.Block) Modifier.height(64.dp) else Modifier,
        label = "Loading", fraction = if (kind == ProgressKind.Linear) value(progressFraction) / 100f else null,
        skeleton = value(skeleton), lines = 3,
    )
}

private val surfaceTone = Knob.enumOf("tone", "Tone", SurfaceTone.Raised)
private val bordered = Knob.Flag("bordered", "Bordered")

private val surface = Story(
    "surface", "Surface", "Slots/Containment",
    description = "Raised containers at the four elevation steps.",
    knobs = listOf(surfaceTone, bordered),
    covers = slot("Surface"),
    canvas = CanvasOptions(checkerboard = true),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevationStep.entries.forEach { e ->
            StapelSurface(Modifier.size(72.dp), tone = value(surfaceTone), elevation = e, bordered = value(bordered)) {
                Box(Modifier.padding(8.dp)) { StapelText(e.name, style = StapelTheme.type.caption) }
            }
        }
    }
}

private val listItem = Story(
    "list-item", "List item", "Slots/Containment",
    description = "Leading media, headline and supporting text, trailing value, and an accessory (chevron, toggle, badge).",
    knobs = listOf(enabled),
    states = listOf(StoryState.Default, StoryState("rtl", "Right to left", rtl = true, locale = "ar")),
    covers = slot("ListItem"),
    anatomy = listOf(
        AnatomyPart("media", "Leading media", "ListItemSpec.media"),
        AnatomyPart("value", "Trailing value", "ListItemSpec.value"),
    ),
) {
    var push by remember { mutableStateOf(true) }
    Column {
        StapelListItem("This device", supporting = "Active now", onClick = { event("click") }, selected = true, media = ListItemMedia.Icon(StapelIconIds.Device), enabled = value(enabled))
        StapelListItem(
            "Grace Hopper", Modifier.anatomy("media"), supporting = "Sent the invoice", onClick = { event("click") },
            media = ListItemMedia.Avatar(AvatarSpec("GH", "Grace Hopper")), value = ListItemValue("-42.00", "Yesterday", StapelTone.Error),
            enabled = value(enabled),
        )
        StapelListItem("Language", Modifier.anatomy("value"), onClick = { event("click") }, value = ListItemValue("English"), accessory = ListItemAccessory.Chevron, enabled = value(enabled))
        StapelListItem("Push alerts", supporting = "On this device", accessory = ListItemAccessory.Toggle(ToggleKind.Switch, push) { push = it }, enabled = value(enabled))
        StapelListItem("Inbox", onClick = { event("click") }, accessory = ListItemAccessory.Badge(BadgeSpec(12, "12 unread")), enabled = value(enabled))
    }
}

private val barTitle = Knob.Text("title", "Title", "Security")
private val barSubtitle = Knob.Text("subtitle", "Subtitle", null)
private val back = Knob.Flag("back", "Back action", true)
private val actions = Knob.Flag("actions", "Actions", true)

private val topBar = Story(
    "top-bar", "Top bar", "Slots/Navigation",
    description = "Title (single line, truncates), optional subtitle, navigation and actions.",
    knobs = listOf(barTitle, barSubtitle, back, actions),
    states = listOf(
        StoryState("default", "Back and actions"),
        StoryState("subtitle", "With subtitle", mapOf("title" to "Inbox", "subtitle" to "12 unread", "actions" to false)),
        StoryState("rtl", "Right to left", mapOf("title" to "الأمان", "subtitle" to "جلستان نشطتان"), rtl = true, locale = "ar"),
    ),
    covers = slot("TopBar"),
) {
    StapelTopBar(
        value(barTitle) ?: "",
        navigation = if (value(back)) IconButtonSpec(StapelIconIds.ArrowBack, "Back", { event("back") }) else null,
        actions = if (value(actions)) listOf(IconButtonSpec(StapelIconIds.More, "More", { event("more") })) else emptyList(),
        subtitle = value(barSubtitle),
    )
}

private val divider = Story("divider", "Divider", "Slots/Containment", description = "A hairline between groups.", covers = slot("Divider")) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StapelText("Above")
        StapelDivider()
        StapelText("Below")
    }
}

private val snackbarAction = Knob.Text("action", "Action", "Undo")

private val snackbar = Story(
    "snackbar", "Snackbar", "Slots/Feedback",
    description = "A transient message with an optional action.",
    knobs = listOf(snackbarAction),
    covers = slot("Snackbar"),
) {
    val action = value(snackbarAction)
    StapelTheme.skin.Snackbar(SnackbarSpec("Password changed", actionLabel = action, onAction = action?.let { { event("action") } }), Modifier)
}

private val toggleKind = Knob.enumOf("kind", "Kind", ToggleKind.Switch)
private val checked = Knob.Flag("checked", "Checked", true)
private val toggleSupporting = Knob.Text("supporting", "Supporting", null)

private val toggle = Story(
    "toggle", "Toggle", "Slots/Inputs",
    description = "Switch, checkbox (with indeterminate) and radio, with label, supporting text and error.",
    knobs = listOf(toggleKind, checked, toggleSupporting, error, enabled),
    states = listOf(
        StoryState("default", "Switch on"),
        StoryState("checkbox-error", "Checkbox with error", mapOf("kind" to ToggleKind.Checkbox, "checked" to false, "error" to "Required to continue")),
        StoryState("radio", "Radio", mapOf("kind" to ToggleKind.Radio, "supporting" to "Text message")),
    ),
    covers = slot("Toggle"),
) {
    var on by remember(value(checked)) { mutableStateOf(value(checked)) }
    StapelToggle(
        value(toggleKind), on, { on = it; event("checked=$it") }, "Two-factor sign-in", Modifier.fillMaxWidth(),
        enabled = value(enabled), supporting = value(toggleSupporting), error = value(error),
    )
}

private val tabsVariant = Knob.enumOf("variant", "Variant", TabsVariant.Tabs)
private val tabIcons = Knob.Flag("icons", "Icons")
private val scrollable = Knob.Flag("scrollable", "Scrollable (8 tabs)")

private val tabs = Story(
    "tabs", "Tabs", "Slots/Navigation",
    description = "Tabs and segmented controls, with icons or scrolling.",
    knobs = listOf(tabsVariant, tabIcons, scrollable),
    states = listOf(StoryState("default", "Tabs"), StoryState("segmented", "Segmented with icons", mapOf("variant" to TabsVariant.Segmented, "icons" to true))),
    covers = slot("Tabs"),
) {
    var tab by remember { mutableIntStateOf(0) }
    val items = if (value(scrollable)) (1..8).map { "Section $it" } else listOf("Home", "Alerts", "Search")
    StapelTabs(
        items, tab, { tab = it; event("select $it") }, variant = value(tabsVariant),
        icons = if (value(tabIcons) && !value(scrollable)) listOf(StapelIconIds.Home, StapelIconIds.Bell, StapelIconIds.Search) else null,
        scrollable = value(scrollable),
    )
}

private val chipKind = Knob.enumOf("kind", "Kind", ChipKind.Choice)
private val selected = Knob.Flag("selected", "Selected", true)
private val chipSize = Knob.enumOf("size", "Size", ControlSize.Regular)
private val removable = Knob.Flag("removable", "Removable")

private val chip = Story(
    "chip", "Chip", "Slots/Inputs",
    description = "Choice, filter, input (removable) and status chips.",
    knobs = listOf(chipKind, selected, chipSize, removable, enabled),
    states = listOf(
        StoryState("default", "Choice, selected"),
        StoryState("input", "Input, removable", mapOf("kind" to ChipKind.Input, "removable" to true, "selected" to false)),
        StoryState("rtl", "Input, removable, right to left", mapOf("kind" to ChipKind.Input, "removable" to true, "selected" to false), rtl = true, locale = "ar"),
    ),
    covers = slot("Chip"),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StapelChip(
            "Ada", kind = value(chipKind), selected = value(selected), onClick = { event("click") },
            leadingIcon = if (value(chipKind) == ChipKind.Input) StapelIconIds.User else null,
            onRemove = if (value(removable)) ({ event("remove") }) else null, enabled = value(enabled), size = value(chipSize),
        )
        StapelChip("Verified", kind = ChipKind.Status, tone = StapelTone.Success)
    }
}

private val avatarSize = Knob.enumOf("size", "Size", AvatarSize.Medium)
private val avatarBadge = Knob.Flag("badge", "Badge")

private val avatar = Story(
    "avatar", "Avatar", "Slots/Display",
    description = "Image, initials or icon, in four sizes, with an optional badge.",
    knobs = listOf(avatarSize, avatarBadge),
    covers = slot("Avatar"),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val badge = if (value(avatarBadge)) BadgeSpec(4, "4 new") else null
        StapelAvatar("Ada Lovelace", size = value(avatarSize), badge = badge)
        StapelAvatar("Unknown person", initials = null, size = value(avatarSize))
        StapelAvatar("Team", initials = null, icon = StapelIconIds.Settings, size = value(avatarSize))
        StapelAvatar("Grace Hopper", size = value(avatarSize), badge = BadgeSpec(null, "Online", tone = StapelTone.Success), badgePlacement = BadgePlacement.TopEnd)
    }
}

private val badgeCount = Knob.Number("count", "Count (0 = dot)", 0..150, 3)
private val badgeTone = Knob.enumOf("tone", "Tone", StapelTone.Error)
private val badgeSize = Knob.enumOf("size", "Size", BadgeSize.Medium)
private val emphasis = Knob.enumOf("emphasis", "Emphasis", BadgeEmphasis.Solid)

private val badge = Story(
    "badge", "Badge", "Slots/Display",
    description = "A count or a dot on content or standalone; counts above the maximum show 99+.",
    knobs = listOf(badgeCount, badgeTone, badgeSize, emphasis),
    states = listOf(StoryState("default", "Count"), StoryState("overflow", "Overflow", mapOf("count" to 150))),
    covers = slot("Badge"),
) {
    val count = value(badgeCount).takeIf { it > 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StapelBadge(count = count, contentDescription = "$count unread", tone = value(badgeTone), size = value(badgeSize), emphasis = value(emphasis)) {
            StapelText("Inbox   ")
        }
        StapelBadge(count = count, contentDescription = "standalone", tone = value(badgeTone), size = value(badgeSize), emphasis = value(emphasis))
    }
}

private val navItems = listOf(
    NavigationItemSpec("home", "Home", StapelIconIds.Home),
    NavigationItemSpec("bell", "Alerts", StapelIconIds.Bell, BadgeSpec(2)),
    NavigationItemSpec("user", "Account", StapelIconIds.User),
)

private val navigationBar = Story(
    "navigation-bar", "Navigation bar", "Slots/Navigation",
    description = "The phone's primary navigation (the shell draws it).",
    states = listOf(StoryState.Default, StoryState("rtl", "Right to left", rtl = true, locale = "ar")),
    covers = slot("NavigationBar"),
) {
    var current by remember { mutableStateOf("home") }
    StapelNavigationBar(navItems, current, { current = it; event("select $it") })
}

private val navigationRail = Story(
    "navigation-rail", "Navigation rail", "Slots/Navigation",
    description = "The tablet / desktop primary navigation.",
    covers = slot("NavigationRail"),
) {
    var current by remember { mutableStateOf("home") }
    StapelNavigationRail(navItems, current, { current = it; event("select $it") }, Modifier.height(240.dp))
}

private val select = Story(
    "select", "Select", "Slots/Inputs",
    description = "A select field opening a picker sheet (never a dropdown); search filters case- and accent-insensitively.",
    knobs = listOf(error, enabled),
    states = listOf(StoryState("default", "Chosen"), StoryState("error", "Required", mapOf("error" to "Required"))),
    covers = slot("Select"),
) {
    var lang by remember { mutableStateOf<String?>("English") }
    StapelSelect(
        "Language", listOf("English", "Español", "Русский", "Čeština"), lang, { lang = it; event("select $it") }, { it },
        searchLabel = "Search languages", error = value(error), enabled = value(enabled),
    )
}

private val pages = Knob.Number("count", "Pages", 2..30, 4)

private val pagerIndicator = Story(
    "pager-indicator", "Pager indicator", "Slots/Navigation",
    description = "Page dots, windowed when there are more pages than dots.",
    knobs = listOf(pages),
    states = listOf(StoryState("default", "Four pages"), StoryState("windowed", "Twenty pages", mapOf("count" to 20))),
    covers = slot("PagerIndicator"),
) {
    var page by remember { mutableIntStateOf(0) }
    val count = value(pages)
    StapelPagerIndicator(count, page.coerceAtMost(count - 1), "Page ${page + 1} of $count", onSelect = { page = it; event("page $it") }, dotLabel = { "Go to page ${it + 1}" })
}

private val refreshingKnob = Knob.Flag("refreshing", "Refreshing")

private val pullToRefresh = Story(
    "pull-to-refresh", "Pull to refresh", "Slots/Feedback",
    description = "Pull on touch; an accessibility action and a visible control on pointer input.",
    knobs = listOf(refreshingKnob),
    covers = slot("PullToRefresh"),
) {
    var refreshing by remember(value(refreshingKnob)) { mutableStateOf(value(refreshingKnob)) }
    StapelPullToRefresh(refreshing, { refreshing = true; event("refresh") }) {
        Column(Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
            StapelText("Sessions list (pull, or use the control)")
            if (refreshing) StapelButton("Finish refresh", { refreshing = false }, variant = ButtonVariant.Text)
        }
    }
}

private val menuOpen = Knob.Flag("open", "Open", true)

private val menu = Story(
    "menu", "Menu", "Slots/Actions",
    description = "An action menu at its anchor (an overflow button); destructive actions in the error role, disabled ones shown but inert. Choosing a value is a Select, never a menu.",
    knobs = listOf(menuOpen),
    states = listOf(StoryState("default", "Open"), StoryState("closed", "Closed", mapOf("open" to false)), StoryState("rtl", "Open, right to left", rtl = true, locale = "ar")),
    covers = slot("Menu"),
    canvas = CanvasOptions(height = 300.dp),
    snippet = "StapelOverflowMenu(listOf(MenuItemSpec(\"Reply\", reply), MenuItemSpec(\"Delete\", delete, destructive = true)), label = \"Message actions\")",
) {
    var open by remember(value(menuOpen)) { mutableStateOf(value(menuOpen)) }
    Box(Modifier.fillMaxWidth()) {
        StapelMenu(
            open, { open = false; event("dismiss") },
            listOf(
                MenuItemSpec("Reply", { event("reply") }, icon = StapelIconIds.ArrowBack),
                MenuItemSpec("Copy link", { event("copy") }, enabled = false),
                MenuItemSpec("Settings", { event("settings") }, icon = StapelIconIds.Settings),
                MenuItemSpec("Delete", { event("delete") }, icon = StapelIconIds.Close, destructive = true),
            ),
            "Message actions",
            Modifier.align(Alignment.TopEnd),
        ) {
            StapelIconButton(StapelIconIds.More, "Message actions", { open = true; event("open") })
        }
    }
}

private val quantity = Knob.Number("value", "Value", 0..10, 2)
private val maxQuantity = Knob.Number("max", "Maximum", 1..10, 10)

private val stepper = Story(
    "stepper", "Stepper", "Slots/Inputs",
    description = "A quantity stepper (a cart line): the buttons disable at the bounds, the value never clips.",
    knobs = listOf(quantity, maxQuantity, enabled),
    states = listOf(
        StoryState("default", "Two"),
        StoryState("min", "At the minimum", mapOf("value" to 0)),
        StoryState("max", "At the maximum", mapOf("value" to 3, "max" to 3)),
        StoryState("rtl", "Right to left", rtl = true, locale = "ar"),
    ),
    covers = slot("Stepper"),
    snippet = "StapelStepper(qty, { qty = it }, label = \"Quantity\", decrementLabel = \"Remove one\", incrementLabel = \"Add one\", min = 1, max = stock)",
) {
    val max = value(maxQuantity)
    var current by remember(value(quantity), max) { mutableIntStateOf(value(quantity).coerceAtMost(max)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StapelStepper(current, { current = it; event("value $it") }, "Quantity", "Remove one", "Add one", max = max, enabled = value(enabled))
        StapelStepper(current * 250, { event("weight $it") }, "Weight", "Less", "More", step = 250, valueText = "${current * 250} g", enabled = value(enabled))
    }
}

private val groupLayout = Knob.enumOf("layout", "Layout", ChipGroupLayout.Wrap)

private val chipGroup = Story(
    "chip-group", "Chip group", "Slots/Inputs",
    description = "Filters that wrap onto rows, or a category strip that scrolls; each chip is the skin's Chip.",
    knobs = listOf(groupLayout),
    states = listOf(StoryState("default", "Wrap"), StoryState("scroll", "Scroll", mapOf("layout" to ChipGroupLayout.Scroll))),
    covers = slot("ChipGroup"),
    snippet = "StapelChipGroup(filters.map { ChipSpec(it.label, ChipKind.Filter, it.on, { toggle(it) }) }, label = \"Filters\")",
) {
    var picked by remember { mutableStateOf(setOf(1)) }
    val labels = listOf("Under 50", "Free delivery", "In stock", "New", "Top rated", "On sale", "Pick-up today")
    StapelChipGroup(
        labels.mapIndexed { i, label ->
            ChipSpec(label, ChipKind.Filter, selected = i in picked, onClick = { picked = if (i in picked) picked - i else picked + i; event("toggle $label") })
        },
        "Filters",
        layout = value(groupLayout),
    )
}

/** The slot stories, one per `StapelSkin` slot, in slot-table order. */
internal val slotStories: List<Story> = listOf(
    button, iconButton, textField, codeField, dialog, banner, progress, surface, listItem, topBar, divider, snackbar,
    toggle, tabs, chip, avatar, badge, navigationBar, navigationRail, pullToRefresh, select, pagerIndicator, menu, stepper, chipGroup,
)
