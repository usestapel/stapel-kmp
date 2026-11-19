package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The title of a chrome that names the screen it hosts: an app shell's top bar, or a [StapelPage]
 * drawing its own bar from `title`. A hosted screen publishes its title here with [StapelScreenTitle]
 * instead of drawing a heading, so the title appears once. The chrome shows [current] when it is set
 * and its own label (the destination's) otherwise.
 *
 * The rule, for every screen of every brick: the chrome owns the title when the screen is hosted in a
 * chrome with a title ([LocalStapelChromeTitle] provided); the screen draws its own heading only when
 * hosted bare.
 */
@Stable
public class StapelChromeTitle {
    private val published = mutableStateListOf<Pair<Any, String>>()

    /** The title the most recently composed hosted screen published; null = none. */
    public val current: String? get() = published.lastOrNull()?.second

    internal fun publish(owner: Any, title: String) {
        val index = published.indexOfFirst { it.first === owner }
        if (index >= 0) published[index] = owner to title else published += owner to title
    }

    internal fun withdraw(owner: Any) {
        published.removeAll { it.first === owner }
    }
}

/** The chrome title the current screen is hosted under; null = hosted bare (the screen draws its heading). */
public val LocalStapelChromeTitle: ProvidableCompositionLocal<StapelChromeTitle?> = staticCompositionLocalOf { null }

/**
 * A screen's title: published to the hosting chrome ([LocalStapelChromeTitle]) when there is one, or
 * drawn as the page heading (heading semantics, `type.heading`) when the screen is hosted bare.
 *
 * Dialogs and sheets name themselves through their own `title`; never call this inside one.
 *
 * @param testTag tag of the drawn heading (absent when the chrome shows the title).
 */
@Composable
public fun StapelScreenTitle(text: String, modifier: Modifier = Modifier, testTag: String? = null) {
    val chrome = LocalStapelChromeTitle.current
    if (chrome == null) {
        StapelText(
            text,
            modifier.semantics { heading() }.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            style = StapelTheme.type.heading,
        )
    } else {
        val owner = remember { Any() }
        DisposableEffect(chrome, text) {
            chrome.publish(owner, text)
            onDispose { chrome.withdraw(owner) }
        }
    }
}
