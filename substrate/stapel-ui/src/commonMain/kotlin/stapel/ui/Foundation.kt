package stapel.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.i18n.PluralKey
import stapel.i18n.StringKey

/** The colour text and icons take by default inside a surface (set by surfaces and filled controls). */
public val LocalStapelContentColor: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Unspecified }

/**
 * Text in a theme style and the current content colour.
 *
 * @param style a [StapelTheme.type] style; `body` by default.
 * @param color `LocalStapelContentColor` by default.
 */
@Composable
public fun StapelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = StapelTheme.type.body,
    color: Color = LocalStapelContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val resolved = if (color == Color.Unspecified) StapelTheme.colors.text else color
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = resolved, textAlign = textAlign ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * An icon from the theme's icon set; an id the set does not draw renders the generic square.
 *
 * @param contentDescription null for a decorative icon.
 */
@Composable
public fun StapelIconView(
    icon: StapelIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalStapelContentColor.current,
) {
    val vector = stapelIconVector(icon)
    val resolved = if (tint == Color.Unspecified) StapelTheme.colors.text else tint
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(resolved),
    )
}

/** An inline link in the `link` role (`link-hover` while focused). */
@Composable
public fun StapelLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = StapelTheme.colors
    StapelText(
        text = text,
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .stapelFocusRing(interaction),
        style = StapelTheme.type.body.copy(textDecoration = TextDecoration.Underline),
        color = if (focused) colors.linkHover else colors.link,
    )
}

/** Draws the `focus-ring` outline while [interactionSource] reports focus. */
@Composable
public fun Modifier.stapelFocusRing(interactionSource: MutableInteractionSource, shape: Shape? = null): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) this.border(2.dp, StapelTheme.colors.focusRing, shape ?: StapelTheme.shapes.md) else this
}

/** [key] in the current locale; recomposes when the locale changes. */
@Composable
public fun stapelString(key: StringKey, vararg params: Pair<String, Any?>): String {
    val strings = LocalStapelStrings.current
    val locale by strings.locale.collectAsState()
    return remember(strings, locale, key, *params) { strings.t(key, *params) }
}

/** The plural form of [key] for [count]; recomposes when the locale changes. */
@Composable
public fun stapelPlural(key: PluralKey, count: Long, vararg params: Pair<String, Any?>): String {
    val strings = LocalStapelStrings.current
    val locale by strings.locale.collectAsState()
    return remember(strings, locale, key, count, *params) { strings.tPlural(key, count, *params) }
}

/** Sets an accessible name on a node (for wrappers that draw several children). */
internal fun Modifier.accessibleName(name: String?): Modifier = if (name == null) this else semantics { contentDescription = name }
