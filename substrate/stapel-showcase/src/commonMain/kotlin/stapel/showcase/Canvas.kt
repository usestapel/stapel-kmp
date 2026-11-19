package stapel.showcase

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** What a story's content can read and report while it is drawn. */
public interface StoryScope {
    /** The state being drawn ([StoryState.Default] in the playground). */
    public val state: StoryState

    /** The value of [knob]: a matrix cell's value, else the state's fixed value, else the live knob value. */
    public fun <T> value(knob: Knob<T>): T

    /** Reports a callback of the component (`click`, `dismiss`); the gallery shows it in the event log. */
    public fun event(name: String)

    /** Marks this element as the anatomy part [partId]; the anatomy view outlines and numbers it. */
    public fun Modifier.anatomy(partId: String): Modifier
}

/** Test tag of the canvas box around a story. */
public const val STORY_CANVAS_TAG: String = "stapel.showcase.canvas"

/**
 * Draws [story] in [state] on its canvas (checkerboard, padding, width cap from [Story.canvas]).
 * Theme, strings and the catalogue's environment are the caller's: the gallery and the snapshot runner
 * wrap this in the theme of their controls.
 *
 * @param values the live knob values (the playground's); a fresh [KnobValues] gives the defaults.
 * @param overrides knob values that win over everything (a matrix cell).
 * @param onEvent receives `event(name)` calls of the story.
 * @param showAnatomy outline and number the parts marked with `Modifier.anatomy`.
 */
@Composable
public fun StoryCanvas(
    story: Story,
    state: StoryState = story.states.first(),
    values: KnobValues = remember(story) { KnobValues(story.knobs) },
    modifier: Modifier = Modifier,
    overrides: Map<String, Any?> = emptyMap(),
    onEvent: (String) -> Unit = {},
    showAnatomy: Boolean = false,
) {
    val bounds = remember(story) { mutableStateMapOf<String, Rect>() }
    val root = remember(story) { CoordinatesHolder() }
    val scope = StoryScopeImpl(state, values, overrides, onEvent, bounds, root)
    val options = story.canvas
    var outer = modifier.testTag(STORY_CANVAS_TAG)
    if (options.checkerboard) outer = outer.checkerboard()
    outer = outer.onGloballyPositioned { root.coordinates = it }
    var inner = Modifier.padding(options.padding)
    options.maxWidth?.let { inner = inner.widthIn(max = it) }
    Box(outer) {
        Box(inner) { story.content(scope) }
        if (showAnatomy) AnatomyOverlay(story, bounds)
    }
}

@Composable
private fun AnatomyOverlay(story: Story, bounds: Map<String, Rect>) {
    val density = LocalDensity.current
    story.anatomy.forEachIndexed { index, part ->
        val r = bounds[part.id] ?: return@forEachIndexed
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(r.width.toDp(), r.height.toDp())
                    .border(1.dp, ANATOMY)
                    .testTag("stapel.showcase.anatomy.${part.id}"),
            ) {
                BasicText(
                    "${index + 1}",
                    Modifier.background(ANATOMY).padding(horizontal = 3.dp),
                    style = TextStyle(color = Color.White, fontSize = 10.sp),
                )
            }
        }
    }
}

private val ANATOMY = Color(0xFFE5006E)

/** A checkerboard behind the content, so padding, shadows and translucent fills are visible. */
public fun Modifier.checkerboard(cell: androidx.compose.ui.unit.Dp = 8.dp, light: Color = Color(0xFFF2F2F2), dark: Color = Color(0xFFDADADA)): Modifier =
    drawBehind {
        drawRect(light)
        val step = cell.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step
            while (x < size.width) {
                drawRect(dark, Offset(x, y), Size(step, step))
                x += 2 * step
            }
            y += step
            row++
        }
    }

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

private class StoryScopeImpl(
    override val state: StoryState,
    private val values: KnobValues,
    private val overrides: Map<String, Any?>,
    private val onEvent: (String) -> Unit,
    private val bounds: SnapshotStateMap<String, Rect>,
    private val root: CoordinatesHolder,
) : StoryScope {
    override fun <T> value(knob: Knob<T>): T = when {
        knob.key in overrides -> knob.cast(overrides[knob.key])
        knob.key in state.values -> knob.cast(state.values[knob.key])
        else -> values[knob]
    }

    override fun event(name: String) = onEvent(name)

    override fun Modifier.anatomy(partId: String): Modifier = onGloballyPositioned { coords ->
        val parent = root.coordinates ?: return@onGloballyPositioned
        if (parent.isAttached && coords.isAttached) {
            val r = parent.localBoundingBoxOf(coords, clipBounds = false)
            if (bounds[partId] != r) bounds[partId] = r
        }
    }
}
