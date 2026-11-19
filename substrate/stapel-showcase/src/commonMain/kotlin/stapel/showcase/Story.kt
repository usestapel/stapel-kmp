package stapel.showcase

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What a story shows; the gallery groups and filters by it. */
public enum class StoryKind {
    /** A token family drawn as itself: colour roles, type scale, spacing. */
    Foundation,

    /** One skin slot or wrapper. */
    Component,

    /** A composite built from slots (status page, picker sheet). */
    Composite,

    /** A brick screen in one state. */
    Screen,

    /** Several screens of one journey. */
    Flow,
}

/**
 * Something a story is evidence for; `checkDemoCoverage` requires every `StapelSkin` slot and every
 * member of a brick's screen table (`<Brick>Screens`) to be covered by at least one story.
 *
 * @property key the stable form used in the exported index (`slot:Button`, `screen:SignIn`).
 */
public sealed class StoryTarget(public val key: String) {
    /**
     * A `StapelSkin` slot by its member name.
     *
     * @property name the slot (`Button`, `NavigationRail`).
     */
    public class Slot(public val name: String) : StoryTarget("slot:$name")

    /**
     * A member of the brick's screen table.
     *
     * @property name the member (`SignIn`, `Sessions`).
     */
    public class Screen(public val name: String) : StoryTarget("screen:$name")

    /** Equal when the [key]s are equal. */
    override fun equals(other: Any?): Boolean = other is StoryTarget && other.key == key

    /** Hash of the [key]. */
    override fun hashCode(): Int = key.hashCode()

    /** The [key]. */
    override fun toString(): String = key
}

/**
 * A named, fixed combination of knob values: one snapshot, one docs image, one tile of the states view.
 *
 * @property id stable within its story (`default`, `error`, `loading`); part of snapshot file names.
 * @property title what the state shows.
 * @property values knob key → value; a knob not named here keeps its default.
 */
public class StoryState(
    public val id: String,
    public val title: String,
    public val values: Map<String, Any?> = emptyMap(),
) {
    init {
        require(ID.matches(id)) { "state id '$id' must match ${ID.pattern}" }
    }

    /** Defaults. */
    public companion object {
        /** The one state of a story that declares none: every knob at its default. */
        public val Default: StoryState = StoryState("default", "Default")
    }
}

/**
 * A named part of a component, listed on its anatomy view. Mark the part in the story's content
 * with `Modifier.anatomy(id)` (from [StoryScope]) to have the canvas outline and number it.
 *
 * @property id stable within the story.
 * @property label the part's name ("Leading media").
 * @property description what the part is for and which spec field drives it.
 */
public class AnatomyPart(public val id: String, public val label: String, public val description: String? = null)

/**
 * How the canvas presents a story.
 *
 * @property checkerboard draw a checkerboard behind the story so padding and translucent fills show.
 * @property padding space around the story inside the canvas.
 * @property maxWidth the story's width cap inside the frame (null: the frame's width).
 */
public class CanvasOptions(
    public val checkerboard: Boolean = false,
    public val padding: Dp = 16.dp,
    public val maxWidth: Dp? = null,
)

/**
 * One component, composite or screen of a catalogue.
 *
 * @property id stable within its catalogue (`button`, `sign_in.code.error`); the gallery route,
 *   snapshot names and the docs index use it. Lower-case letters, digits, `.`, `_`, `-`.
 * @property title the story's name in the catalogue tree.
 * @property group catalogue path, `/`-separated ("Slots/Inputs", "Sign-in"); the gallery's family pages.
 * @property kind what the story shows.
 * @property description one or two sentences shown on the story page and in the docs index.
 * @property states named fixed states (snapshot units); [StoryState.Default] when none is declared.
 * @property knobs live controls of the playground.
 * @property covers the slots / screens this story is evidence for (`checkDemoCoverage`).
 * @property anatomy named parts for the anatomy view.
 * @property canvas presentation options.
 * @property snippet usage shown on the page and exported to the docs index.
 * @property content draws the story; read knob values through the [StoryScope].
 */
public class Story(
    public val id: String,
    public val title: String,
    public val group: String,
    public val kind: StoryKind = StoryKind.Component,
    public val description: String? = null,
    states: List<StoryState> = emptyList(),
    public val knobs: List<Knob<*>> = emptyList(),
    public val covers: Set<StoryTarget> = emptySet(),
    public val anatomy: List<AnatomyPart> = emptyList(),
    public val canvas: CanvasOptions = CanvasOptions(),
    public val snippet: String? = null,
    public val content: @Composable StoryScope.() -> Unit,
) {
    /** The declared states, or [StoryState.Default] alone. */
    public val states: List<StoryState> = states.ifEmpty { listOf(StoryState.Default) }

    /** [group] split into its path segments. */
    public val groupPath: List<String> = group.split('/').map { it.trim() }.filter { it.isNotEmpty() }

    init {
        require(ID.matches(id)) { "story id '$id' must match ${ID.pattern}" }
        require(groupPath.isNotEmpty()) { "story '$id' has an empty group" }
        duplicates(this.states.map { it.id })?.let { throw IllegalArgumentException("story '$id' declares state '$it' twice") }
        duplicates(knobs.map { it.key })?.let { throw IllegalArgumentException("story '$id' declares knob '$it' twice") }
        duplicates(anatomy.map { it.id })?.let { throw IllegalArgumentException("story '$id' declares anatomy part '$it' twice") }
        val keys = knobs.associateBy { it.key }
        for (state in this.states) {
            for ((key, value) in state.values) {
                val knob = requireNotNull(keys[key]) { "story '$id' state '${state.id}' sets '$key', which is not a knob of the story" }
                require(knob.accepts(value)) { "story '$id' state '${state.id}': '$value' is not a value of knob '$key'" }
            }
        }
    }

    /** The state named [stateId], or null. */
    public fun state(stateId: String): StoryState? = states.firstOrNull { it.id == stateId }
}

internal val ID = Regex("[a-z0-9][a-z0-9._-]*")

internal fun duplicates(ids: List<String>): String? = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
