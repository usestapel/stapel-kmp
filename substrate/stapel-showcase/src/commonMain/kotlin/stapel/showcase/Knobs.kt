package stapel.showcase

import androidx.compose.runtime.mutableStateMapOf
import stapel.tokens.ColorRole

/**
 * A typed live control of a story's playground. Its value is read in the story through
 * `value(knob)`; a [StoryState] fixes it by [key].
 *
 * @property key stable within the story; states and links name the knob by it.
 * @property label the control's caption.
 * @property default the value after a reset.
 */
public sealed class Knob<T>(public val key: String, public val label: String, public val default: T) {
    init {
        require(ID.matches(key)) { "knob key '$key' must match ${ID.pattern}" }
    }

    /** True when [value] is a legal value of this knob. */
    public abstract fun accepts(value: Any?): Boolean

    /** A short human form of [value] for labels, matrices and the exported index. */
    public open fun describe(value: T): String = value.toString()

    /** The kind name written to the exported index (`flag`, `choice`, `text`, `int`, `color`). */
    public abstract val kindName: String

    @Suppress("UNCHECKED_CAST")
    internal fun cast(value: Any?): T = value as T

    /** On / off. */
    public class Flag(key: String, label: String, default: Boolean = false) : Knob<Boolean>(key, label, default) {
        override fun accepts(value: Any?): Boolean = value is Boolean

        override val kindName: String get() = "flag"
    }

    /**
     * One of [options] (label → value); drawn as a segmented or dropdown control.
     *
     * @property options the labelled values, in display order; labels are unique.
     */
    public class Choice<T>(
        key: String,
        label: String,
        public val options: List<Pair<String, T>>,
        default: T,
    ) : Knob<T>(key, label, default) {
        init {
            require(options.isNotEmpty()) { "choice knob '$key' has no options" }
            duplicates(options.map { it.first })?.let { throw IllegalArgumentException("choice knob '$key' has option label '$it' twice") }
            require(options.any { it.second == default }) { "choice knob '$key': the default is not one of its options" }
        }

        override fun accepts(value: Any?): Boolean = options.any { it.second == value }

        override fun describe(value: T): String = options.first { it.second == value }.first

        override val kindName: String get() = "choice"
    }

    /**
     * Free text; with [optional] the control can switch the value off (null), for optional spec fields.
     *
     * @property optional null is a legal value.
     */
    public class Text(key: String, label: String, default: String?, public val optional: Boolean = default == null) :
        Knob<String?>(key, label, default) {
        init {
            require(default != null || optional) { "text knob '$key' has a null default but is not optional" }
        }

        override fun accepts(value: Any?): Boolean = value is String || (value == null && optional)

        override fun describe(value: String?): String = value ?: "—"

        override val kindName: String get() = "text"
    }

    /**
     * An integer in [range], moved in [step]s (a count, a length, a page index).
     *
     * @property range legal values.
     * @property step increment of the stepper control.
     */
    public class Number(key: String, label: String, public val range: IntRange, default: Int, public val step: Int = 1) :
        Knob<Int>(key, label, default) {
        init {
            require(!range.isEmpty()) { "number knob '$key' has an empty range" }
            require(default in range) { "number knob '$key': default $default is outside $range" }
            require(step > 0) { "number knob '$key': step must be positive" }
        }

        override fun accepts(value: Any?): Boolean = value is Int && value in range

        override val kindName: String get() = "int"
    }

    /**
     * A colour role of the theme (a tint, a background behind the component).
     *
     * @property roles the roles offered.
     */
    public class Color(key: String, label: String, default: ColorRole, public val roles: List<ColorRole> = ColorRole.entries) :
        Knob<ColorRole>(key, label, default) {
        init {
            require(default in roles) { "color knob '$key': the default is not one of its roles" }
        }

        override fun accepts(value: Any?): Boolean = value is ColorRole && value in roles

        override fun describe(value: ColorRole): String = value.key

        override val kindName: String get() = "color"
    }

    /** Factories. */
    public companion object {
        /** A [Choice] over every constant of enum [E], labelled by name. */
        public inline fun <reified E : Enum<E>> enumOf(key: String, label: String, default: E): Choice<E> =
            Choice(key, label, enumValues<E>().map { it.name to it }, default)
    }
}

/**
 * The live values of a story's knobs: starts at the defaults, observable by Compose (the playground
 * recomposes on [set]), resettable per knob or as a whole. Values are validated against their knob.
 *
 * @property knobs the knobs these values belong to (a knob is matched by identity).
 */
public class KnobValues(public val knobs: List<Knob<*>>) {
    private val byKey = knobs.associateBy { it.key }
    private val values = mutableStateMapOf<String, Any?>()

    /** The current value of [knob] (its default until set). */
    public operator fun <T> get(knob: Knob<T>): T {
        requireKnown(knob)
        return if (knob.key in values) knob.cast(values[knob.key]) else knob.default
    }

    /** Sets [knob] to [value]; throws when the value is not legal for the knob. */
    public operator fun <T> set(knob: Knob<T>, value: T) {
        requireKnown(knob)
        require(knob.accepts(value)) { "'$value' is not a value of knob '${knob.key}'" }
        if (value == knob.default) values.remove(knob.key) else values[knob.key] = value
    }

    /** Sets the knob named [key] from an untyped [value] (a link, a saved session); false when not legal. */
    public fun trySet(key: String, value: Any?): Boolean {
        val knob = byKey[key] ?: return false
        if (!knob.accepts(value)) return false
        if (value == knob.default) values.remove(key) else values[key] = value
        return true
    }

    /** Puts [knob] back to its default. */
    public fun reset(knob: Knob<*>) {
        requireKnown(knob)
        values.remove(knob.key)
    }

    /** Puts every knob back to its default. */
    public fun reset() {
        values.clear()
    }

    /** Keys of the knobs that differ from their default. */
    public val modified: Set<String> get() = values.keys.toSet()

    /** Every knob's current value by key, defaults included. */
    public fun snapshot(): Map<String, Any?> = knobs.associate { it.key to (if (it.key in values) values[it.key] else it.default) }

    private fun requireKnown(knob: Knob<*>) {
        require(byKey[knob.key] === knob) { "knob '${knob.key}' does not belong to these values" }
    }
}
