package stapel.probe.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Test tag of the text node rendered by [ProbeLabel]. */
public const val PROBE_LABEL_TAG: String = "stapel.probe.label"

/**
 * Renders the slug of [parts] as plain text (no design system).
 *
 * @param parts words trimmed, lower-cased and joined with dashes (blank parts dropped).
 * @param modifier modifier applied to the text node.
 */
@Composable
public fun ProbeLabel(vararg parts: String, modifier: Modifier = Modifier) {
    BasicText(text = slug(parts), modifier = modifier.testTag(PROBE_LABEL_TAG))
}

private fun slug(parts: Array<out String>): String =
    parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("-") { it.lowercase() }
