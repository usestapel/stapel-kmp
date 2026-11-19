package stapel.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A container colour and the colour of what sits on it.
 *
 * @property container fill.
 * @property content text and glyphs on [container].
 * @property outline the border ([Color.Transparent] when there is none).
 */
@Immutable
public class ContainerColors(public val container: Color, public val content: Color, public val outline: Color = Color.Transparent)

/**
 * Colour combinations derived once from the roles, so every skin answers "which fill does a subtle
 * warning badge use" the same way (no new tokens: functions of [StapelColors]).
 */
public object ComponentColors {
    /** A badge in [tone]: solid = the tone's base with its on-colour; subtle = tinted background with the tone's text. */
    public fun badge(colors: StapelColors, tone: StapelTone, solid: Boolean): ContainerColors = when {
        tone == StapelTone.Neutral && solid -> ContainerColors(colors.text, colors.surface)
        tone == StapelTone.Neutral -> ContainerColors(colors.surfaceSunken, colors.text)
        solid -> colors.status(tone).let { ContainerColors(it.base, it.on) }
        else -> colors.status(tone).let { ContainerColors(it.bg, it.base) }
    }

    /** An avatar's circle when it shows initials or a glyph. */
    public fun avatar(colors: StapelColors): ContainerColors = ContainerColors(colors.brandSubtle, colors.text)

    /** A chip: disabled, a status chip in [statusTone] (null for other kinds), selected, or the plain look. */
    public fun chip(colors: StapelColors, selected: Boolean, statusTone: StapelTone?, enabled: Boolean): ContainerColors = when {
        !enabled -> ContainerColors(colors.surfaceSunken, colors.textSubtle, colors.borderSubtle)
        statusTone != null && statusTone != StapelTone.Neutral -> colors.status(statusTone).let { ContainerColors(it.bg, colors.text, it.border) }
        statusTone != null -> ContainerColors(colors.surfaceSunken, colors.text, colors.borderSubtle)
        selected -> ContainerColors(colors.brandSubtle, colors.text, colors.brand)
        else -> ContainerColors(colors.surface, colors.text, colors.border)
    }
}
