package stapel.probe.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import stapel.probe.probeSlug

/** Test tag of the text node rendered by [ProbeLabel]. */
public const val PROBE_LABEL_TAG: String = "stapel.probe.label"

/**
 * Renders the slug of [parts] as plain text (no design system).
 *
 * @param parts words joined by [probeSlug].
 * @param modifier modifier applied to the text node.
 */
@Composable
public fun ProbeLabel(vararg parts: String, modifier: Modifier = Modifier) {
    BasicText(text = probeSlug(*parts), modifier = modifier.testTag(PROBE_LABEL_TAG))
}
