package stapel.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import stapel.tokens.Argb
import stapel.tokens.ColorRole
import stapel.tokens.ColorTokens

/** This [Argb] as a Compose [Color]. */
public fun Argb.toColor(): Color = Color(value.toInt())

/** The tone of a status-coloured element (banner, chip, snackbar, icon button). */
public enum class StapelTone {
    /** No status: neutral surfaces and text. */
    Neutral,

    /** Information. */
    Info,

    /** Success. */
    Success,

    /** Warning. */
    Warning,

    /** Error / danger. */
    Error,
}

/**
 * The colours of one status tone.
 *
 * @property base text and icons in the tone.
 * @property bg tinted background.
 * @property border outline.
 * @property on label on a solid [base] fill.
 */
@Immutable
public class StapelStatusColors(public val base: Color, public val bg: Color, public val border: Color, public val on: Color)

/**
 * The current mode's colour roles as Compose colours (§68 dictionary). Read through
 * `StapelTheme.colors`; never write a colour literal in a brick.
 *
 * @property tokens the mode's token colours this view reads.
 */
@Immutable
public class StapelColors(public val tokens: ColorTokens) {
    /** Role `surface`. */
    public val surface: Color get() = tokens.surface.toColor()

    /** Role `surface-raised`. */
    public val surfaceRaised: Color get() = tokens.surfaceRaised.toColor()

    /** Role `surface-sunken`. */
    public val surfaceSunken: Color get() = tokens.surfaceSunken.toColor()

    /** Role `surface-overlay`. */
    public val surfaceOverlay: Color get() = tokens.surfaceOverlay.toColor()

    /** Role `text`. */
    public val text: Color get() = tokens.text.toColor()

    /** Role `text-muted`. */
    public val textMuted: Color get() = tokens.textMuted.toColor()

    /** Role `text-subtle`. */
    public val textSubtle: Color get() = tokens.textSubtle.toColor()

    /** Role `text-on-accent`. */
    public val textOnAccent: Color get() = tokens.textOnAccent.toColor()

    /** Role `border`. */
    public val border: Color get() = tokens.border.toColor()

    /** Role `border-subtle`. */
    public val borderSubtle: Color get() = tokens.borderSubtle.toColor()

    /** Role `focus-ring`. */
    public val focusRing: Color get() = tokens.focusRing.toColor()

    /** Role `brand`. */
    public val brand: Color get() = tokens.brand.toColor()

    /** Role `brand-hover`. */
    public val brandHover: Color get() = tokens.brandHover.toColor()

    /** Role `brand-active`. */
    public val brandActive: Color get() = tokens.brandActive.toColor()

    /** Role `brand-subtle`. */
    public val brandSubtle: Color get() = tokens.brandSubtle.toColor()

    /** Role `link`. */
    public val link: Color get() = tokens.link.toColor()

    /** Role `link-hover`. */
    public val linkHover: Color get() = tokens.linkHover.toColor()

    /** Role `success`. */
    public val success: Color get() = tokens.success.toColor()

    /** Role `success-bg`. */
    public val successBg: Color get() = tokens.successBg.toColor()

    /** Role `success-border`. */
    public val successBorder: Color get() = tokens.successBorder.toColor()

    /** Role `success-on`. */
    public val successOn: Color get() = tokens.successOn.toColor()

    /** Role `warning`. */
    public val warning: Color get() = tokens.warning.toColor()

    /** Role `warning-bg`. */
    public val warningBg: Color get() = tokens.warningBg.toColor()

    /** Role `warning-border`. */
    public val warningBorder: Color get() = tokens.warningBorder.toColor()

    /** Role `warning-on`. */
    public val warningOn: Color get() = tokens.warningOn.toColor()

    /** Role `error`. */
    public val error: Color get() = tokens.error.toColor()

    /** Role `error-bg`. */
    public val errorBg: Color get() = tokens.errorBg.toColor()

    /** Role `error-border`. */
    public val errorBorder: Color get() = tokens.errorBorder.toColor()

    /** Role `error-on`. */
    public val errorOn: Color get() = tokens.errorOn.toColor()

    /** Role `info`. */
    public val info: Color get() = tokens.info.toColor()

    /** Role `info-bg`. */
    public val infoBg: Color get() = tokens.infoBg.toColor()

    /** Role `info-border`. */
    public val infoBorder: Color get() = tokens.infoBorder.toColor()

    /** Role `info-on`. */
    public val infoOn: Color get() = tokens.infoOn.toColor()

    /** The colour of [role]. */
    public operator fun get(role: ColorRole): Color = tokens[role].toColor()

    /** A canon or host role by key, or null. */
    public fun getOrNull(key: String): Color? = tokens.getOrNull(key)?.toColor()

    /**
     * The colours of [tone]. [StapelTone.Neutral] is `text` on `surface-sunken` with `border`, and
     * `surface` as the label on a solid `text` fill.
     */
    public fun status(tone: StapelTone): StapelStatusColors {
        val family = when (tone) {
            StapelTone.Neutral -> return StapelStatusColors(text, surfaceSunken, border, surface)
            StapelTone.Info -> "info"
            StapelTone.Success -> "success"
            StapelTone.Warning -> "warning"
            StapelTone.Error -> "error"
        }
        val s = tokens.status(family) ?: error("status family $family missing")
        return StapelStatusColors(s.base.toColor(), s.bg.toColor(), s.border.toColor(), s.on.toColor())
    }
}
