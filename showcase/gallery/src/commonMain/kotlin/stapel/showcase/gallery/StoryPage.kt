package stapel.showcase.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import stapel.navigation.Route
import stapel.navigation.StapelNavigator
import stapel.navigation.Navigator
import stapel.showcase.Knob
import stapel.showcase.KnobValues
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.matrix
import stapel.tokens.StapelPresetTokens
import stapel.ui.EmptyState
import stapel.ui.SectionHeader
import stapel.ui.SectionHeaderSize
import stapel.ui.StapelButton
import stapel.ui.StapelChip
import stapel.ui.StapelSelect
import stapel.ui.StapelTabs
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelToggle
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ControlSize
import stapel.ui.skin.ToggleKind

/** The tabs of a story page, in order. */
enum class StoryTab(val id: String, val title: String) {
    Playground("playground", "Playground"),
    States("states", "States"),
    Matrix("matrix", "Matrix"),
    Anatomy("anatomy", "Anatomy"),
    Usage("usage", "Usage"),
}

/** The global controls: skin, preset, mode, locale, size class, text scale, reduce motion, RTL. */
@Composable
internal fun ControlsBar(state: GalleryState) {
    val c = state.controls
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp).testTag("gallery.controls"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun <T> group(key: String, options: List<T>, current: T, label: (T) -> String, set: (T) -> GalleryControls) {
            options.forEach { o ->
                StapelChip(
                    label(o), selected = o == current, size = ControlSize.Small, testTag = "gallery.control.$key.${label(o)}",
                    onClick = { state.controls = set(o) },
                )
            }
            StapelText(" ")
        }
        group("skin", GallerySkin.entries, c.skin, { it.name }) { c.copy(skin = it) }
        group("preset", StapelPresetTokens.all.keys.toList(), c.preset, { it }) { c.copy(preset = it) }
        group("mode", ModeChoice.entries, c.mode, { it.name }) { c.copy(mode = it) }
        group("locale", state.locales, c.locale, { it }) { c.copy(locale = it) }
        group("size", SizeChoice.entries, c.size, { it.name }) { c.copy(size = it) }
        group("scale", TEXT_SCALES, c.textScale, { "$it%" }) { c.copy(textScale = it) }
        StapelChip("Reduce motion", selected = c.reduceMotion, size = ControlSize.Small, testTag = "gallery.control.motion",
            onClick = { state.controls = c.copy(reduceMotion = !c.reduceMotion) })
        StapelChip("RTL", selected = c.rtl, size = ControlSize.Small, testTag = "gallery.control.rtl",
            onClick = { state.controls = c.copy(rtl = !c.rtl) })
    }
}

/** A story page: header with "copy id" / "copy link", then the tabs. */
@Composable
internal fun StoryPage(
    state: GalleryState,
    route: StoryRoute,
    navigator: Navigator,
    link: (Route) -> String?,
    copyText: (String) -> Unit,
) {
    val catalog = state.catalog(route.catalog)
    val story = catalog?.story(route.story)
    LaunchedEffect(route) {
        if (route.controls.isNotEmpty()) state.controls = GalleryControls.fromQuery(route.controls)
    }
    if (catalog == null || story == null) {
        EmptyState("No story “${route.catalog}/${route.story}”", Modifier.testTag("gallery.page.missing"), description = "Search the catalogue on the left.")
        return
    }
    val qid = catalog.qualifiedId(story)
    val tabs = StoryTab.entries.filter {
        when (it) {
            StoryTab.Matrix -> story.matrix()?.isNotEmpty() != false
            StoryTab.Anatomy -> story.anatomy.isNotEmpty()
            else -> true
        }
    }
    val tab = tabs.firstOrNull { it.id == route.tab } ?: StoryTab.Playground
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("gallery.page.story.$qid"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(story.title, caption = "$qid · ${story.group} · ${story.kind}", description = story.description)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StapelButton("Copy story id", { copyText(qid); state.log("copied $qid") }, variant = ButtonVariant.Secondary, size = ControlSize.Small, testTag = "gallery.copy-id")
            StapelButton(
                "Copy link",
                { link(route.copy(controls = state.controls.toQuery()))?.let { copyText(it); state.log("copied $it") } },
                variant = ButtonVariant.Text, size = ControlSize.Small, testTag = "gallery.copy-link",
            )
        }
        StapelTabs(tabs.map { it.title }, tabs.indexOf(tab), { i -> navigator.navigate(route.copy(tab = tabs[i].id, controls = emptyMap()), singleTop = true) }, testTag = "gallery.tabs")
        when (tab) {
            StoryTab.Playground -> Playground(state, catalog, story)
            StoryTab.States -> story.states.forEach { s ->
                SectionHeader(s.title, Modifier.testTag("gallery.state.${s.id}"), size = SectionHeaderSize.XSmall, caption = s.id)
                Frames(state, catalog, story, s, remember(story) { KnobValues(story.knobs) }, tag = "gallery.state.${s.id}.frame")
            }
            StoryTab.Matrix -> {
                val cells = story.matrix()
                if (cells == null) StapelText("More than 48 combinations: narrow the knobs in the playground.")
                else cells.forEachIndexed { i, cell ->
                    StapelText(cell.label, Modifier.testTag("gallery.cell.$i"), style = StapelTheme.type.caption)
                    Frames(state, catalog, story, values = state.values(catalog, story), overrides = cell.values, tag = "gallery.cell.$i.frame")
                }
            }
            StoryTab.Anatomy -> {
                Frames(state, catalog, story, showAnatomy = true)
                story.anatomy.forEachIndexed { i, part ->
                    StapelText("${i + 1}. ${part.label}" + (part.description?.let { " — $it" } ?: ""), Modifier.testTag("gallery.anatomy.${part.id}"))
                }
            }
            StoryTab.Usage -> Usage(catalog, story)
        }
    }
}

@Composable
private fun Usage(catalog: StoryCatalog, story: Story) {
    Column(Modifier.testTag("gallery.usage"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StapelText("Id: ${catalog.qualifiedId(story)}")
        if (story.covers.isNotEmpty()) StapelText("Covers: ${story.covers.joinToString { it.key }}")
        StapelText("States: ${story.states.joinToString { "${it.id} (${it.title})" }}")
        if (story.knobs.isNotEmpty()) StapelText("Knobs: ${story.knobs.joinToString { "${it.key}: ${it.kindName}" }}")
        story.snippet?.let { StapelText(it, style = StapelTheme.type.caption.copy(fontFamily = StapelTheme.type.mono)) }
    }
}

@Composable
private fun Playground(state: GalleryState, catalog: StoryCatalog, story: Story) {
    val values = state.values(catalog, story)
    Frames(state, catalog, story, values = values)
    if (story.knobs.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Knobs", Modifier.weight(1f), size = SectionHeaderSize.Small)
            StapelButton("Reset all", { values.reset() }, variant = ButtonVariant.Text, size = ControlSize.Small, enabled = values.modified.isNotEmpty(), testTag = "gallery.knobs.reset")
        }
        story.knobs.forEach { KnobControl(it, values) }
    }
    SectionHeader("Events", size = SectionHeaderSize.Small)
    Column(Modifier.testTag("gallery.events")) {
        state.events.takeLast(8).forEach { StapelText(it, style = StapelTheme.type.caption) }
    }
}

@Composable
private fun KnobControl(knob: Knob<*>, values: KnobValues) {
    val tag = "gallery.knob.${knob.key}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StapelText(knob.label, Modifier.width(140.dp), style = StapelTheme.type.label)
        val scroll = if (knob is Knob.Choice<*>) Modifier.horizontalScroll(rememberScrollState()) else Modifier
        Row(Modifier.weight(1f).then(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (knob) {
                is Knob.Flag -> StapelToggle(ToggleKind.Checkbox, values[knob], { values[knob] = it }, if (values[knob]) "On" else "Off", testTag = tag)
                is Knob.Choice<*> -> ChoiceControl(knob, values, tag)
                is Knob.Text -> TextControl(knob, values, tag)
                is Knob.Number -> {
                    val v = values[knob]
                    StapelButton("−", { values[knob] = (v - knob.step).coerceAtLeast(knob.range.first) }, variant = ButtonVariant.Secondary, size = ControlSize.Small, enabled = v > knob.range.first, testTag = "$tag.dec")
                    StapelText("$v", Modifier.testTag("$tag.value"))
                    StapelButton("+", { values[knob] = (v + knob.step).coerceAtMost(knob.range.last) }, variant = ButtonVariant.Secondary, size = ControlSize.Small, enabled = v < knob.range.last, testTag = "$tag.inc")
                }
                is Knob.Color -> StapelSelect(knob.label, knob.roles, values[knob], { values[knob] = it }, { it.key }, testTag = tag)
            }
        }
        if (knob.key in values.modified) StapelButton("Reset", { values.reset(knob) }, variant = ButtonVariant.Text, size = ControlSize.Small, testTag = "$tag.reset")
    }
}

@Composable
private fun <T> ChoiceControl(knob: Knob.Choice<T>, values: KnobValues, tag: String) {
    val current = values[knob]
    knob.options.forEach { (label, value) ->
        StapelChip(label, selected = value == current, size = ControlSize.Small, testTag = "$tag.$label", onClick = { values[knob] = value })
    }
}

@Composable
private fun TextControl(knob: Knob.Text, values: KnobValues, tag: String) {
    val current = values[knob]
    val field = remember(knob) { TextFieldState(current ?: "") }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { t -> if (values[knob] != null && t != values[knob]) values[knob] = t }
    }
    LaunchedEffect(current) {
        if (current != null && field.text.toString() != current) field.setTextAndPlaceCursorAtEnd(current)
    }
    if (knob.optional) {
        StapelToggle(ToggleKind.Checkbox, current != null, { on -> values[knob] = if (on) field.text.toString() else null }, "Shown", testTag = "$tag.on")
    }
    if (current != null) StapelTextField(field, knob.label, Modifier.width(260.dp), testTag = tag)
}
