package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.tokens.Breakpoints
import stapel.tokens.StapelTokens

/** Size class of the window, from the token breakpoints (not Material's 600/840: one breakpoint system with the web). */
public enum class StapelSizeClass {
    /** Narrower than `breakpoints.tablet`. */
    Phone,

    /** From `breakpoints.tablet` up to `breakpoints.desktop`. */
    Tablet,

    /** From `breakpoints.desktop` up. */
    Desktop,

    ;

    /** Classification. */
    public companion object {
        /** The size class of a window [widthDp] wide under [breakpoints]. */
        public fun of(widthDp: Float, breakpoints: Breakpoints): StapelSizeClass = when {
            widthDp >= breakpoints.desktop -> Desktop
            widthDp >= breakpoints.tablet -> Tablet
            else -> Phone
        }
    }
}

/** How the user points: a finger (Android, iOS — tablets too) or a mouse (desktop). */
public enum class StapelInput {
    /** Touch screen. */
    Touch,

    /** Mouse / trackpad. */
    Pointer,
}

/** Where a dialog is drawn: a bottom sheet on a phone, a centered modal otherwise (the web `useDialogSurface` rule). */
public enum class DialogSurface {
    /** Bottom sheet. */
    Sheet,

    /** Centered modal. */
    Modal,
}

/**
 * The layout verdicts of the current window.
 *
 * @property sizeClass from the window width and the token breakpoints.
 * @property input touch or pointer.
 * @property pageGutter THE horizontal page inset: `responsive.page-gutter` for [sizeClass]; applied once, by `StapelPage`.
 * @property controlHeight `controls.height-phone` on touch, `controls.height` under a pointer.
 * @property minTouchTarget `max(controls.height-phone, platform floor)` on touch; 0 under a pointer.
 * @property dialogSurface sheet on a phone, modal otherwise.
 */
@Immutable
public class StapelLayout(
    public val sizeClass: StapelSizeClass,
    public val input: StapelInput,
    public val pageGutter: Dp,
    public val controlHeight: Dp,
    public val minTouchTarget: Dp,
    public val dialogSurface: DialogSurface,
)

internal expect val platformInput: StapelInput
internal expect val platformMinTouchTarget: Dp

/**
 * The layout for a window [windowWidth] wide (pure: no composition needed).
 *
 * @param input touch or pointer (the platform's by default).
 * @param platformTouchFloor the platform's minimum touch target (48 dp Android, 44 pt iOS).
 */
public fun stapelLayout(
    tokens: StapelTokens,
    windowWidth: Dp,
    input: StapelInput = platformInput,
    platformTouchFloor: Dp = platformMinTouchTarget,
): StapelLayout {
    val sizeClass = StapelSizeClass.of(windowWidth.value, tokens.breakpoints)
    val gutter = tokens.responsive.pageGutter
    return StapelLayout(
        sizeClass = sizeClass,
        input = input,
        pageGutter = when (sizeClass) {
            StapelSizeClass.Phone -> gutter.phone
            StapelSizeClass.Tablet -> gutter.tablet
            StapelSizeClass.Desktop -> gutter.desktop
        }.dp,
        controlHeight = (if (input == StapelInput.Touch) tokens.controls.heightPhone else tokens.controls.height).dp,
        minTouchTarget = if (input == StapelInput.Touch) maxOf(tokens.controls.heightPhone.dp, platformTouchFloor) else 0.dp,
        dialogSurface = if (sizeClass == StapelSizeClass.Phone) DialogSurface.Sheet else DialogSurface.Modal,
    )
}

/** The width of the window this composition is in. */
@Composable
public fun currentWindowWidth(): Dp {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { size.width.toDp() }
}

/** [stapelLayout] for the current window, recomputed when the width crosses into another size class. */
@Composable
public fun rememberStapelLayout(tokens: StapelTokens, windowWidth: Dp = currentWindowWidth()): StapelLayout =
    remember(tokens, windowWidth) { stapelLayout(tokens, windowWidth) }
