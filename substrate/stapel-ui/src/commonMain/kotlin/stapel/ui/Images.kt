package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * Turns an image source (a URL, a content id) into a painter. `stapel-ui` loads nothing itself: an
 * app (or an image library's adapter) provides one through [LocalStapelImages]. A slot asks it for
 * every image it shows and draws its fallback (initials, a glyph) while the answer is null — loading,
 * failed, or no loader at all.
 */
public fun interface StapelImages {
    /** The painter for [source], or null while it is not available. */
    @Composable
    public fun painter(source: String): Painter?
}

/** The image loader of the subtree; the default draws nothing (every slot shows its fallback). */
public val LocalStapelImages: ProvidableCompositionLocal<StapelImages> = staticCompositionLocalOf { StapelImages { null } }

/**
 * The initials of [name] for an avatar: the first letter of the first two words, upper-cased
 * ("Ada Lovelace" → "AL", "ada" → "A"); empty for a blank name.
 */
public fun avatarInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
