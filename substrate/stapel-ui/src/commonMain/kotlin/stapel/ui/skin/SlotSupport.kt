package stapel.ui.skin

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import kotlin.math.roundToInt

// Rules several skins share, stated once so a second skin cannot drift from the first.

/** False when the badge draws nothing (a count of zero or less). */
public val BadgeSpec.visible: Boolean get() = count == null || count > 0

/** The badge's text: null for a dot, `max+` above [BadgeSpec.max]. */
public val BadgeSpec.text: String? get() = count?.let { if (it > max) "$max+" else it.toString() }

/** The avatar's accessible name: its description plus a visible badge's description. */
public val AvatarSpec.accessibleName: String
    get() = listOfNotNull(contentDescription, badge?.takeIf { it.visible }?.contentDescription).joinToString(", ")

/** The text an avatar shows when no image is drawn: trimmed [AvatarSpec.initials], at most two characters, or null. */
public val AvatarSpec.initialsText: String? get() = initials?.trim()?.take(2)?.uppercase()?.takeIf { it.isNotEmpty() }

/** The toggle state a checkbox / switch exposes. */
public fun toggleState(checked: Boolean, indeterminate: Boolean): ToggleableState = when {
    indeterminate -> ToggleableState.Indeterminate
    checked -> ToggleableState.On
    else -> ToggleableState.Off
}

/**
 * The interaction of a two-state control, per [kind]: a switch / checkbox toggles (an indeterminate
 * checkbox reports `true`), a radio is selectable and only ever reports `true`. One merged node with
 * the kind's role.
 */
public fun Modifier.stapelToggle(
    kind: ToggleKind,
    checked: Boolean,
    indeterminate: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    onCheckedChange: (Boolean) -> Unit,
): Modifier = when {
    kind == ToggleKind.Radio ->
        selectable(selected = checked, interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.RadioButton) {
            onCheckedChange(true)
        }
    kind == ToggleKind.Checkbox && indeterminate ->
        triStateToggleable(ToggleableState.Indeterminate, interactionSource, null, enabled, Role.Checkbox) { onCheckedChange(true) }
    else -> toggleable(
        value = checked,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = if (kind == ToggleKind.Switch) Role.Switch else Role.Checkbox,
        onValueChange = onCheckedChange,
    )
}

/** The refresh as an accessibility action named [PullToRefreshSpec.label] (absent while refreshing). */
public fun Modifier.refreshAction(spec: PullToRefreshSpec): Modifier =
    if (spec.refreshing) {
        this
    } else {
        semantics { customActions = listOf(CustomAccessibilityAction(spec.label) { spec.onRefresh(); true }) }
    }

/** Share of a list row the value block may take before it wraps (the headline keeps the rest). */
public const val LIST_VALUE_MAX_FRACTION: Float = 0.6f

/** Measures the element with at most [fraction] of the incoming maximum width. */
public fun Modifier.maxWidthFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).roundToInt().coerceAtLeast(constraints.minWidth) else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * One dot of a pager indicator.
 *
 * @property page the page it stands for (0-based).
 * @property small drawn smaller: the window is cut on that side.
 */
public class PagerDot(public val page: Int, public val small: Boolean) {
    /** Equal page and size. */
    override fun equals(other: Any?): Boolean = other is PagerDot && other.page == page && other.small == small

    /** Hash of page and size. */
    override fun hashCode(): Int = page * 2 + if (small) 1 else 0

    /** `3` or `3s`. */
    override fun toString(): String = if (small) "${page}s" else "$page"
}

/**
 * The dots a pager indicator draws for [count] pages at [current] with at most [maxDots]: every page
 * when they fit; otherwise a window that keeps [current] inside (centred while it can be), whose edge
 * dot is small on each side where pages are hidden.
 */
public fun pagerDotWindow(count: Int, current: Int, maxDots: Int): List<PagerDot> {
    if (count <= 0) return emptyList()
    val cur = current.coerceIn(0, count - 1)
    val max = maxDots.coerceAtLeast(3)
    if (count <= max) return (0 until count).map { PagerDot(it, small = false) }
    val start = (cur - max / 2).coerceIn(0, count - max)
    val end = start + max - 1
    return (start..end).map { p -> PagerDot(p, small = (p == start && start > 0 && p != cur) || (p == end && end < count - 1 && p != cur)) }
}

/**
 * The keyboard of a text field: the type derived from [TextFieldSpec.kind] — unless the caller's
 * [TextFieldSpec.inputTransformation] names a keyboard type, which then wins (a mask knows its keyboard);
 * the IME action from [TextFieldSpec.imeAction], `Search` for a search field left at the default.
 * Foundation merges the transformation's other keyboard options under these.
 */
public fun TextFieldSpec.keyboardOptions(): KeyboardOptions {
    val derived = when (kind) {
        TextFieldKind.Email -> KeyboardType.Email
        TextFieldKind.Phone -> KeyboardType.Phone
        TextFieldKind.Password -> KeyboardType.Password
        TextFieldKind.Number -> KeyboardType.Number
        TextFieldKind.Text, TextFieldKind.Multiline, TextFieldKind.Search -> KeyboardType.Text
    }
    val fromTransformation = inputTransformation?.keyboardOptions?.keyboardType?.takeIf { it != KeyboardType.Unspecified }
    return KeyboardOptions(
        keyboardType = fromTransformation ?: derived,
        imeAction = if (kind == TextFieldKind.Search && imeAction == ImeAction.Default) ImeAction.Search else imeAction,
    )
}

/** The value a stepper tap reports: [StepperSpec.value] moved by [direction] steps, clamped to min..max (no overflow). */
public fun StepperSpec.stepperValue(direction: Int): Int {
    val moved = value.toLong() + direction.toLong() * step.toLong()
    return moved.coerceIn(min.toLong(), max.toLong()).toInt()
}

/** The stepper's shown value: [StepperSpec.valueText], else the number. */
public val StepperSpec.shownValue: String get() = valueText ?: value.toString()

/** False when the decrement button is disabled (at [StepperSpec.min] or disabled). */
public val StepperSpec.canDecrement: Boolean get() = enabled && value > min

/** False when the increment button is disabled (at [StepperSpec.max] or disabled). */
public val StepperSpec.canIncrement: Boolean get() = enabled && value < max
