package stapel.showcase.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.tokens.ColorRole
import stapel.tokens.StapelPresetTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.ui.StapelIconIds
import stapel.ui.StapelInput
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.material3.Material3Skin
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeEmphasis
import stapel.ui.skin.BadgePlacement
import stapel.ui.skin.BadgeSize
import stapel.ui.skin.ControlSize
import stapel.ui.skin.PagerIndicatorSpec
import stapel.ui.skin.SelectSpec
import stapel.ui.skin.SkeletonShape
import stapel.ui.AdaptiveGrid
import stapel.ui.EmptyState
import stapel.ui.SectionHeader
import stapel.ui.SectionHeaderAction
import stapel.ui.SectionHeaderSize
import stapel.ui.StapelMultiSelect
import stapel.ui.StapelSelect
import stapel.ui.StatusPage
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemMedia
import stapel.ui.skin.ListItemValue
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.BannerSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.CodeFieldSpec
import stapel.ui.skin.DialogSpec
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.FoundationSkin
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
import stapel.ui.skin.TabsVariant
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.ToggleKind
import stapel.ui.skin.ToggleSpec
import stapel.ui.skin.TopBarSpec
import stapel.ui.stapelLayout
import stapel.ui.toColor

/** The skins the gallery switches between. */
enum class GallerySkin(val skin: StapelSkin) { Material3(Material3Skin), Foundation(FoundationSkin) }

/** One slot's demo: the slot name and its content drawn through the current skin. */
class SlotStory(val slot: String, val content: @Composable () -> Unit)

/** Every slot in table order, then the composites built on them. */
fun slotStories(): List<SlotStory> = listOf(
    SlotStory("Button") {
        val s = StapelTheme.skin
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.Button(ButtonSpec("Primary", {}), Modifier)
            s.Button(ButtonSpec("Secondary", {}, ButtonVariant.Secondary), Modifier)
            s.Button(ButtonSpec("Text", {}, ButtonVariant.Text), Modifier)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.Button(ButtonSpec("Delete", {}, ButtonVariant.Danger), Modifier)
            s.Button(ButtonSpec("Saving", {}, loading = true), Modifier)
            s.Button(ButtonSpec("Disabled", {}, enabled = false), Modifier)
        }
        s.Button(ButtonSpec("Continue", {}, gatedReason = "Confirm your e-mail first"), Modifier)
    },
    SlotStory("IconButton") {
        Row { listOf(StapelIconIds.ArrowBack, StapelIconIds.Settings, StapelIconIds.Bell, StapelIconIds.Search).forEach { StapelTheme.skin.IconButton(IconButtonSpec(it, it.id, {}), Modifier) } }
    },
    SlotStory("TextField") {
        val s = StapelTheme.skin
        s.TextField(TextFieldSpec(remember { TextFieldState("ada@example.com") }, "E-mail", TextFieldKind.Email, supportingText = "We never share it"), Modifier)
        s.TextField(TextFieldSpec(remember { TextFieldState("secret") }, "Password", TextFieldKind.Password), Modifier)
        s.TextField(TextFieldSpec(remember { TextFieldState("12") }, "Phone", TextFieldKind.Phone, error = "Enter the full number"), Modifier)
        s.TextField(TextFieldSpec(remember { TextFieldState("running shoes") }, "Search", TextFieldKind.Search, loading = true, clearLabel = "Clear search"), Modifier)
    },
    SlotStory("CodeField") { StapelTheme.skin.CodeField(CodeFieldSpec(remember { TextFieldState("12") }, 6, "Code"), Modifier) },
    SlotStory("Dialog") {
        var open by remember { mutableStateOf(false) }
        StapelTheme.skin.Button(ButtonSpec("Open dialog", { open = true }, ButtonVariant.Secondary), Modifier)
        if (open) {
            StapelTheme.skin.Dialog(
                DialogSpec({ open = false }, StapelTheme.layout.dialogSurface, title = "Sign out everywhere?", dismissLabel = "Close",
                    footer = { StapelTheme.skin.Button(ButtonSpec("Sign out", { open = false }, ButtonVariant.Danger), Modifier) }),
            ) { StapelText("Every other session ends now.") }
        }
        StapelText("Surface on this window: ${StapelTheme.layout.dialogSurface}", style = StapelTheme.type.caption)
    },
    SlotStory("Banner") {
        StapelTone.entries.forEach { tone ->
            StapelTheme.skin.Banner(BannerSpec(tone, "A ${tone.name.lowercase()} message.", title = tone.name, detail = if (tone == StapelTone.Error) "HTTP 503" else null), Modifier)
        }
    },
    SlotStory("Progress") {
        val s = StapelTheme.skin
        s.Progress(ProgressSpec(ProgressKind.Spinner, "Loading"), Modifier)
        s.Progress(ProgressSpec(ProgressKind.Linear, "Upload", 0.6f), Modifier)
        s.Progress(ProgressSpec(ProgressKind.Skeleton, "Loading", skeleton = SkeletonShape.Text, lines = 3), Modifier)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.Progress(ProgressSpec(ProgressKind.Skeleton, "Loading", skeleton = SkeletonShape.Circle), Modifier)
            s.Progress(ProgressSpec(ProgressKind.Skeleton, "Loading", skeleton = SkeletonShape.Block), Modifier.height(64.dp))
        }
    },
    SlotStory("Surface") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ElevationStep.None, ElevationStep.Low, ElevationStep.Medium, ElevationStep.High).forEach { e ->
                StapelTheme.skin.Surface(SurfaceSpec(elevation = e, bordered = e == ElevationStep.None), Modifier.size(72.dp)) {
                    Box(Modifier.padding(8.dp)) { StapelText(e.name, style = StapelTheme.type.caption) }
                }
            }
        }
    },
    SlotStory("ListItem") {
        val s = StapelTheme.skin
        var push by remember { mutableStateOf(true) }
        s.ListItem(ListItemSpec("This device", supporting = "Active now", onClick = {}, selected = true, media = ListItemMedia.Icon(StapelIconIds.Device)), Modifier)
        s.ListItem(ListItemSpec("Laptop", supporting = "2 days ago", overline = "Session", onClick = {}, accessory = ListItemAccessory.Chevron), Modifier)
        s.ListItem(
            ListItemSpec(
                "Grace Hopper", supporting = "Sent the invoice", onClick = {},
                media = ListItemMedia.Avatar(AvatarSpec("GH", "Grace Hopper")), value = ListItemValue("-42.00", "Yesterday", StapelTone.Error),
            ),
            Modifier,
        )
        s.ListItem(ListItemSpec("Language", onClick = {}, value = ListItemValue("English"), accessory = ListItemAccessory.Chevron), Modifier)
        s.ListItem(ListItemSpec("Push alerts", supporting = "On this device", accessory = ListItemAccessory.Toggle(ToggleKind.Switch, push, { push = it })), Modifier)
        s.ListItem(ListItemSpec("Inbox", onClick = {}, accessory = ListItemAccessory.Badge(BadgeSpec(12, "12 unread"))), Modifier)
    },
    SlotStory("TopBar") {
        StapelTheme.skin.TopBar(TopBarSpec("Security", IconButtonSpec(StapelIconIds.ArrowBack, "Back", {}), listOf(IconButtonSpec(StapelIconIds.More, "More", {}))), Modifier)
        StapelTheme.skin.TopBar(TopBarSpec("Inbox", IconButtonSpec(StapelIconIds.ArrowBack, "Back", {}), subtitle = "12 unread"), Modifier)
        StapelTheme.skin.TopBar(TopBarSpec("Orders", subtitle = "3 in progress", collapsedFraction = 0f), Modifier)
    },
    SlotStory("Divider") { StapelTheme.skin.Divider(Modifier) },
    SlotStory("Snackbar") { StapelTheme.skin.Snackbar(SnackbarSpec("Password changed", actionLabel = "Undo", onAction = {}), Modifier) },
    SlotStory("Toggle") {
        var on by remember { mutableStateOf(true) }
        val s = StapelTheme.skin
        s.Toggle(ToggleSpec(ToggleKind.Switch, on, { on = it }, "Two-factor sign-in"), Modifier)
        s.Toggle(ToggleSpec(ToggleKind.Checkbox, on, { on = it }, "Remember me"), Modifier)
        s.Toggle(ToggleSpec(ToggleKind.Radio, !on, { on = !it }, "Text message"), Modifier)
        s.Toggle(ToggleSpec(ToggleKind.Checkbox, false, { on = it }, "Select all", indeterminate = true), Modifier)
        s.Toggle(
            ToggleSpec(ToggleKind.Switch, on, { on = it }, "Marketing e-mails", supporting = "Once a month at most", controlAtEnd = true),
            Modifier.fillMaxWidth(),
        )
        s.Toggle(ToggleSpec(ToggleKind.Checkbox, false, {}, "Accept the terms", error = "Required to continue"), Modifier)
        s.Toggle(ToggleSpec(ToggleKind.Switch, true, {}, "Locked", enabled = false), Modifier)
    },
    SlotStory("Tabs") {
        var tab by remember { mutableIntStateOf(0) }
        StapelTheme.skin.Tabs(TabsSpec(listOf("E-mail", "Phone"), tab, { tab = it }), Modifier)
        StapelTheme.skin.Tabs(TabsSpec(listOf("Day", "Week", "Month"), tab, { tab = it }, TabsVariant.Segmented), Modifier)
        StapelTheme.skin.Tabs(
            TabsSpec(listOf("Home", "Alerts", "Search"), tab, { tab = it }, TabsVariant.Segmented, icons = listOf(StapelIconIds.Home, StapelIconIds.Bell, StapelIconIds.Search), iconOnly = true),
            Modifier,
        )
        StapelTheme.skin.Tabs(TabsSpec((1..8).map { "Section $it" }, tab, { tab = it }, scrollable = true), Modifier)
    },
    SlotStory("Chip") {
        var filter by remember { mutableStateOf(true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelTheme.skin.Chip(ChipSpec("Selected", selected = true, onClick = {}), Modifier)
            StapelTheme.skin.Chip(ChipSpec("Filter", ChipKind.Filter, selected = filter, onClick = { filter = !filter }), Modifier)
            StapelTheme.skin.Chip(ChipSpec("Verified", ChipKind.Status, tone = StapelTone.Success), Modifier)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelTheme.skin.Chip(ChipSpec("Ada", ChipKind.Input, onClick = {}, onRemove = {}, removeLabel = "Remove Ada", leadingIcon = StapelIconIds.User), Modifier)
            StapelTheme.skin.Chip(ChipSpec("Small", size = ControlSize.Small, onClick = {}), Modifier)
            StapelTheme.skin.Chip(ChipSpec("Disabled", onClick = {}, enabled = false), Modifier)
        }
    },
    SlotStory("Avatar") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarSize.entries.forEach { size -> StapelTheme.skin.Avatar(AvatarSpec("AL", "Ada Lovelace", size = size), Modifier) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelTheme.skin.Avatar(AvatarSpec(null, "Unknown person"), Modifier)
            StapelTheme.skin.Avatar(AvatarSpec(null, "Team", icon = StapelIconIds.Settings), Modifier)
            StapelTheme.skin.Avatar(AvatarSpec("GH", "Grace Hopper", badge = BadgeSpec(null, "Online", tone = StapelTone.Success)), Modifier)
            StapelTheme.skin.Avatar(AvatarSpec("KJ", "Katherine Johnson", size = AvatarSize.Large, badge = BadgeSpec(4, "4 new"), badgePlacement = BadgePlacement.TopEnd), Modifier)
        }
    },
    SlotStory("Badge") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StapelTheme.skin.Badge(BadgeSpec(3, "3 unread"), Modifier) { StapelText("Inbox   ") }
            StapelTheme.skin.Badge(BadgeSpec(150, "150 unread"), Modifier) { StapelText("Mail      ") }
            StapelTheme.skin.Badge(BadgeSpec(null, "New"), Modifier) { StapelText("News  ") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelTone.entries.forEach { tone ->
                BadgeEmphasis.entries.forEach { e -> StapelTheme.skin.Badge(BadgeSpec(7, tone.name, tone = tone, emphasis = e), Modifier) {} }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeSize.entries.forEach { size -> StapelTheme.skin.Badge(BadgeSpec(5, size.name, size = size), Modifier) {} }
            BadgeSize.entries.forEach { size -> StapelTheme.skin.Badge(BadgeSpec(null, size.name, size = size), Modifier) {} }
        }
    },
    SlotStory("NavigationBar") { StapelTheme.skin.NavigationBar(navigation(), Modifier) },
    SlotStory("NavigationRail") { StapelTheme.skin.NavigationRail(navigation(), Modifier.height(200.dp)) },
    SlotStory("Select") {
        var lang by remember { mutableStateOf<String?>("English") }
        StapelSelect("Language", listOf("English", "Español", "Русский", "Čeština"), lang, { lang = it }, { it }, "Close", searchLabel = "Search languages", clearLabel = "Clear")
        var picked by remember { mutableStateOf(setOf("Books")) }
        StapelMultiSelect("Categories", (1..300).map { "Category $it" } + "Books", picked, { picked = it }, { it }, "Close", "Apply", searchLabel = "Search", clearLabel = "Clear")
        StapelTheme.skin.Select(SelectSpec("Currency", null, {}, placeholder = "Choose a currency", error = "Required"), Modifier)
    },
    SlotStory("PagerIndicator") {
        var page by remember { mutableIntStateOf(0) }
        StapelTheme.skin.PagerIndicator(PagerIndicatorSpec(4, page % 4, "Page ${page % 4 + 1} of 4"), Modifier)
        StapelTheme.skin.PagerIndicator(PagerIndicatorSpec(20, page, "Page ${page + 1} of 20", onSelect = { page = it }, dotLabel = { "Go to page ${it + 1}" }), Modifier)
    },
    SlotStory("Composites") {
        SectionHeader("Recent orders", caption = "This week", description = "Paid and pending", action = SectionHeaderAction.Link("See all") {})
        SectionHeader("Settings", size = SectionHeaderSize.XSmall)
        StatusPage(
            "Order placed", description = "We sent the receipt to your e-mail.", icon = StapelIconIds.CheckCircle, tone = StapelTone.Success,
            primaryAction = ButtonSpec("Track order", {}), secondaryAction = ButtonSpec("Keep shopping", {}, ButtonVariant.Text), footer = "Order 1024",
        )
        EmptyState("No orders yet", description = "Your orders show up here.", icon = StapelIconIds.Info, action = ButtonSpec("Browse", {}))
        AdaptiveGrid((1..5).toList(), 120.dp) { n ->
            StapelTheme.skin.Surface(SurfaceSpec(bordered = true), Modifier.height(56.dp)) { Box(Modifier.padding(8.dp)) { StapelText("Card $n") } }
        }
    },
    SlotStory("PullToRefresh") {
        var refreshing by remember { mutableStateOf(false) }
        StapelTheme.skin.PullToRefresh(PullToRefreshSpec(refreshing, { refreshing = true }, "Refresh"), Modifier) {
            Column(Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
                StapelText("Sessions list (pull, or use the button)")
                if (refreshing) StapelTheme.skin.Button(ButtonSpec("Finish refresh", { refreshing = false }, ButtonVariant.Text), Modifier)
            }
        }
    },
)

@Composable
private fun navigation(): NavigationSpec {
    var selected by remember { mutableStateOf("home") }
    return NavigationSpec(
        listOf(
            NavigationItemSpec("home", "Home", StapelIconIds.Home),
            NavigationItemSpec("bell", "Alerts", StapelIconIds.Bell, BadgeSpec(2)),
            NavigationItemSpec("user", "Account", StapelIconIds.User),
        ),
        selected,
        { selected = it },
    )
}

/** All slots, the composites and the 33 roles, in one theme column. */
@Composable
fun GalleryColumn(skin: GallerySkin, tokens: StapelTokens, mode: ThemeMode, width: Dp, ruler: Boolean, stories: List<SlotStory> = slotStories()) {
    val layout = stapelLayout(tokens, width, input = StapelInput.Pointer)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.width(width).background(StapelTheme.colors.surface).padding(horizontal = layout.pageGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StapelText("${mode.name} · ${skin.name} · ${tokens.sourceId}", style = StapelTheme.type.title)
            stories.forEach { story ->
                StapelText(story.slot, style = StapelTheme.type.label, color = StapelTheme.colors.textMuted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { story.content() }
            }
            StapelText("Roles", style = StapelTheme.type.label, color = StapelTheme.colors.textMuted)
            ColorRole.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { role ->
                        Row(Modifier.width((width - 48.dp) / 3)) {
                            Box(Modifier.size(16.dp).background(StapelTheme.colors[role]).border(1.dp, StapelTheme.colors.borderSubtle))
                            StapelText(" ${role.key} ${tokens.colors(mode)[role].toHex()}", style = StapelTheme.type.caption)
                        }
                    }
                }
            }
        }
        if (ruler) {
            // The gutter ruler: a line at THE page gutter, every content edge should touch it.
            Box(Modifier.padding(start = layout.pageGutter).width(1.dp).height(4000.dp).background(StapelTheme.colors.error.copy(alpha = 0.5f)))
        }
    }
    val content: @Composable () -> Unit = { Box { body() } }
    when (skin) {
        GallerySkin.Material3 -> StapelMaterialTheme(tokens = tokens, mode = mode, skin = skin.skin, layout = layout, content = content)
        GallerySkin.Foundation -> StapelTheme(skin = skin.skin, tokens = tokens, mode = mode, layout = layout, content = content)
    }
}

/** The gallery app: pick skin, preset, width and the ruler; light and dark render side by side. */
@Composable
fun Gallery() {
    var skin by remember { mutableStateOf(GallerySkin.Material3) }
    var preset by remember { mutableStateOf("default") }
    var phone by remember { mutableStateOf(true) }
    var ruler by remember { mutableStateOf(false) }
    val tokens = StapelPresetTokens.all.getValue(preset)
    StapelTheme(skin = FoundationSkin, tokens = tokens, mode = ThemeMode.Light, layout = stapelLayout(tokens, 1400.dp, input = StapelInput.Pointer)) {
        Column(Modifier.fillMaxSize().background(StapelTheme.colors.surfaceSunken)) {
            Row(Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GallerySkin.entries.forEach { s ->
                    StapelTheme.skin.Chip(ChipSpec(s.name, selected = s == skin, onClick = { skin = s }), Modifier)
                }
                StapelTheme.skin.Divider(Modifier.width(1.dp))
                StapelPresetTokens.all.keys.forEach { name ->
                    StapelTheme.skin.Chip(ChipSpec(name, selected = name == preset, onClick = { preset = name }), Modifier)
                }
                StapelTheme.skin.Chip(ChipSpec(if (phone) "Phone 390" else "Desktop 640", ChipKind.Filter, selected = phone, onClick = { phone = !phone }), Modifier)
                StapelTheme.skin.Chip(ChipSpec("Gutter ruler", ChipKind.Filter, selected = ruler, onClick = { ruler = !ruler }), Modifier)
            }
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val width = if (phone) 390.dp else 640.dp
                GalleryColumn(skin, tokens, ThemeMode.Light, width, ruler)
                GalleryColumn(skin, tokens, ThemeMode.Dark, width, ruler)
            }
        }
    }
}
