package stapel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.ControlSize
import stapel.ui.skin.IconButtonSpec
import stapel.ui.skin.optionalTestTag

/** Size step of a [SectionHeader]: which type style the title takes. */
public enum class SectionHeaderSize {
    /** A caption-sized group label (dense settings). */
    XSmall,

    /** A label-sized header. */
    Small,

    /** A section title (the default). */
    Medium,

    /** A page-section heading. */
    Large,

    /** A display heading (a feed's first section). */
    XLarge,
}

/** What a [SectionHeader] shows at its end, as data. */
public sealed interface SectionHeaderAction {
    /** A text link ("See all"). */
    public class Link(
        /** Visible label and accessible name. */
        public val label: String,
        /** The action. */
        public val onClick: () -> Unit,
    ) : SectionHeaderAction

    /** An icon button. */
    public class Icon(
        /** The button. */
        public val spec: IconButtonSpec,
    ) : SectionHeaderAction

    /** A chip (a filter, a status). */
    public class Chip(
        /** The chip. */
        public val spec: ChipSpec,
    ) : SectionHeaderAction

    /** An avatar (the section's owner). */
    public class Avatar(
        /** The avatar. */
        public val spec: AvatarSpec,
    ) : SectionHeaderAction

    /** A badge (a count). */
    public class Badge(
        /** The badge. */
        public val spec: BadgeSpec,
    ) : SectionHeaderAction
}

/**
 * The header of a group of content: an optional [caption] above, the [title] (a heading), an optional
 * [description] below, and one [action] at the end, all drawn through the skin's slots. The title
 * wraps; nothing in a header is ever truncated.
 */
@Composable
public fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    size: SectionHeaderSize = SectionHeaderSize.Medium,
    caption: String? = null,
    description: String? = null,
    action: SectionHeaderAction? = null,
    testTag: String? = null,
) {
    val type = StapelTheme.type
    val colors = StapelTheme.colors
    val style: TextStyle = when (size) {
        SectionHeaderSize.XSmall -> type.caption
        SectionHeaderSize.Small -> type.label
        SectionHeaderSize.Medium -> type.title
        SectionHeaderSize.Large -> type.heading
        SectionHeaderSize.XLarge -> type.display
    }
    Row(
        modifier.fillMaxWidth().optionalTestTag(testTag).padding(vertical = StapelTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
            caption?.let { StapelText(it, style = type.caption, color = colors.textMuted) }
            StapelText(
                title,
                style = style,
                color = if (size == SectionHeaderSize.XSmall) colors.textMuted else colors.text,
                modifier = Modifier.semantics { heading() },
            )
            description?.let { StapelText(it, style = type.bodySmall, color = colors.textMuted) }
        }
        val skin = StapelTheme.skin
        when (action) {
            is SectionHeaderAction.Link -> skin.Button(ButtonSpec(action.label, action.onClick, ButtonVariant.Text, ControlSize.Small), Modifier)
            is SectionHeaderAction.Icon -> skin.IconButton(action.spec, Modifier)
            is SectionHeaderAction.Chip -> skin.Chip(action.spec, Modifier)
            is SectionHeaderAction.Avatar -> skin.Avatar(action.spec, Modifier)
            is SectionHeaderAction.Badge -> skin.Badge(action.spec, Modifier) {}
            null -> Unit
        }
    }
}

/** How much room a [StatusPage] takes. */
public enum class StatusSize(
    /** Diameter of the illustration circle in dp. */
    public val illustrationDp: Int,
) {
    /** Inline in a list or a card (the [EmptyState] default). */
    Compact(40),

    /** A section of a screen. */
    Regular(64),

    /** The whole screen: centred vertically, actions full width. */
    Full(96),
}

/**
 * A result or status screen (order placed, payment failed, quiz passed, a page-level error): an
 * illustration glyph in a [tone] circle, the [title] (a heading), a [description], up to two actions
 * (primary above secondary) and a quiet [footer] line. Everything wraps; at [StatusSize.Full] it is
 * centred in the available height and the actions fill the width.
 */
@Composable
public fun StatusPage(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: StapelIcon? = null,
    tone: StapelTone = StapelTone.Neutral,
    primaryAction: ButtonSpec? = null,
    secondaryAction: ButtonSpec? = null,
    footer: String? = null,
    size: StatusSize = StatusSize.Regular,
    testTag: String? = null,
) {
    val colors = StapelTheme.colors
    val type = StapelTheme.type
    val full = size == StatusSize.Full
    val circle: Dp = size.illustrationDp.dp
    Column(
        modifier = modifier.fillMaxWidth().then(if (full) Modifier.fillMaxSize() else Modifier).optionalTestTag(testTag)
            .padding(vertical = if (size == StatusSize.Compact) StapelTheme.spacing.s6 else StapelTheme.spacing.s7),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3, if (full) Alignment.CenterVertically else Alignment.Top),
    ) {
        icon?.let {
            if (size == StatusSize.Compact) {
                StapelIconView(it, null, size = circle, tint = if (tone == StapelTone.Neutral) colors.textSubtle else colors.status(tone).base)
            } else {
                val bg = if (tone == StapelTone.Neutral) colors.surfaceSunken else colors.status(tone).bg
                val fg = if (tone == StapelTone.Neutral) colors.textMuted else colors.status(tone).base
                Box(Modifier.size(circle).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                    StapelIconView(it, null, size = circle * 0.5f, tint = fg)
                }
            }
        }
        StapelText(
            title,
            style = if (size == StatusSize.Compact) type.title else type.heading,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        description?.let { StapelText(it, color = colors.textMuted, textAlign = TextAlign.Center) }
        val actionModifier = if (full) Modifier.fillMaxWidth() else Modifier
        if (primaryAction != null || secondaryAction != null) {
            Column(
                Modifier.padding(top = StapelTheme.spacing.s2).then(if (full) Modifier.fillMaxWidth() else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2),
            ) {
                primaryAction?.let { StapelTheme.skin.Button(it, actionModifier) }
                secondaryAction?.let { StapelTheme.skin.Button(it, actionModifier) }
            }
        }
        footer?.let { StapelText(it, style = type.caption, color = colors.textMuted, textAlign = TextAlign.Center) }
    }
}
