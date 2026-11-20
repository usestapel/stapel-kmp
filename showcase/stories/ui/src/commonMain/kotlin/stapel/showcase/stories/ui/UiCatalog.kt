package stapel.showcase.stories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import stapel.showcase.Knob
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.StoryEnvironment
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.tokens.ColorRole
import stapel.ui.AdaptiveGrid
import stapel.ui.EmptyState
import stapel.ui.SectionHeader
import stapel.ui.SectionHeaderAction
import stapel.ui.SectionHeaderSize
import stapel.ui.StapelButton
import stapel.ui.StapelIconIds
import stapel.ui.StapelListItem
import stapel.ui.StapelSurface
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.StatusPage
import stapel.ui.StatusSize
import stapel.ui.TYPE_STEP_NAMES
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant

private val colorRoles = Story(
    "colors", "Colour roles", "Foundations", StoryKind.Foundation,
    description = "Every semantic colour role of the theme with its value in the current mode.",
) {
    val tokens = StapelTheme.tokens
    val mode = StapelTheme.mode
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorRole.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { role ->
                    Row(Modifier.width(170.dp)) {
                        Box(Modifier.size(16.dp).background(StapelTheme.colors[role]).border(1.dp, StapelTheme.colors.borderSubtle))
                        StapelText(" ${role.key} ${tokens.colors(mode)[role].toHex()}", style = StapelTheme.type.caption)
                    }
                }
            }
        }
    }
}

private val typeScale = Story(
    "type", "Type scale", "Foundations", StoryKind.Foundation,
    description = "The seven type steps, each drawn in itself (the size class may change them).",
) {
    val t = StapelTheme.type
    val steps = listOf(t.xs, t.sm, t.md, t.lg, t.xl, t.xxl, t.xxxl)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, style -> StapelText("${TYPE_STEP_NAMES[i]} ${style.fontSize}", style = style, maxLines = 1) }
    }
}

private val spacing = Story(
    "spacing", "Spacing", "Foundations", StoryKind.Foundation,
    description = "The spacing steps s0–s8 as bars.",
) {
    val s = StapelTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(s.s0, s.s1, s.s2, s.s3, s.s4, s.s5, s.s6, s.s7, s.s8).forEachIndexed { i, d ->
            Row {
                StapelText("s$i ${d.value.toInt()}  ", style = StapelTheme.type.caption, modifier = Modifier.width(56.dp))
                Box(Modifier.width(d).height(12.dp).background(StapelTheme.colors.brand))
            }
        }
    }
}

private val gutter = Story(
    "gutter", "Page gutter", "Foundations", StoryKind.Foundation,
    description = "THE page gutter of the current size class: every content edge touches the ruler.",
    canvas = stapel.showcase.CanvasOptions(padding = 0.dp),
) {
    val g = StapelTheme.layout.pageGutter
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = g, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelText("Gutter ${g.value.toInt()} dp (${StapelTheme.layout.sizeClass})", style = StapelTheme.type.label)
            StapelButton("Continue", { event("click") }, fillWidth = true)
        }
        Box(Modifier.padding(start = g).width(1.dp).fillMaxHeight().background(StapelTheme.colors.error.copy(alpha = 0.6f)))
    }
}

private val headerSize = Knob.enumOf("size", "Size", SectionHeaderSize.Medium)
private val headerLink = Knob.Flag("link", "Link action", true)

private val sectionHeader = Story(
    "section-header", "Section header", "Composites", StoryKind.Composite,
    description = "A section title in five sizes with caption, description and a trailing action.",
    knobs = listOf(headerSize, headerLink),
) {
    SectionHeader(
        "Recent orders", size = value(headerSize), caption = "This week", description = "Paid and pending",
        action = if (value(headerLink)) SectionHeaderAction.Link("See all") { event("see all") } else null,
    )
}

private val statusTone = Knob.enumOf("tone", "Tone", StapelTone.Success)
private val statusSize = Knob.enumOf("size", "Size", StatusSize.Regular)

private val statusPage = Story(
    "status-page", "Status page", "Composites", StoryKind.Composite,
    description = "A result page: icon, title, description, two actions, footer; three sizes.",
    knobs = listOf(statusTone, statusSize),
    states = listOf(StoryState("default", "Success"), StoryState("error", "Failure", mapOf("tone" to StapelTone.Error))),
) {
    StatusPage(
        "Order placed", description = "We sent the receipt to your e-mail.", icon = StapelIconIds.CheckCircle, tone = value(statusTone),
        primaryAction = ButtonSpec("Track order", { event("track") }), secondaryAction = ButtonSpec("Keep shopping", { event("shop") }, ButtonVariant.Text),
        footer = "Order 1024", size = value(statusSize),
    )
}

private val emptyState = Story(
    "empty-state", "Empty state", "Composites", StoryKind.Composite,
    description = "Nothing to show yet, with a way forward.",
) {
    EmptyState("No orders yet", description = "Your orders show up here.", icon = StapelIconIds.Info, action = ButtonSpec("Browse", { event("browse") }))
}

private val cards = Knob.Number("items", "Items", 1..12, 5)

private val adaptiveGrid = Story(
    "adaptive-grid", "Adaptive grid", "Composites", StoryKind.Composite,
    description = "Columns from a minimum cell width: change the size class to see the column count move.",
    knobs = listOf(cards),
) {
    AdaptiveGrid((1..value(cards)).toList(), 120.dp) { n ->
        StapelSurface(Modifier.height(56.dp), bordered = true) { Box(Modifier.padding(8.dp)) { StapelText("Card $n") } }
    }
}

private val rows = Story(
    "list", "Settings list", "Composites", StoryKind.Composite,
    description = "Rows under a section header, as a settings screen uses them.",
) {
    Column {
        SectionHeader("Account", size = SectionHeaderSize.Small)
        StapelListItem("E-mail", supporting = "ada@example.com", onClick = { event("email") })
        StapelListItem("Password", supporting = "Changed 3 months ago", onClick = { event("password") })
    }
}

/** The stapel-ui catalogue: foundations, one story per `StapelSkin` slot, composites. */
val storyCatalog: StoryCatalog = StoryCatalog(
    id = "ui",
    title = "Stapel UI",
    stories = listOf(colorRoles, typeScale, spacing, gutter) + slotStories + listOf(sectionHeader, statusPage, emptyState, adaptiveGrid, rows, loadList),
    environment = StoryEnvironment(locales = listOf("en", "ru", "es")),
)
