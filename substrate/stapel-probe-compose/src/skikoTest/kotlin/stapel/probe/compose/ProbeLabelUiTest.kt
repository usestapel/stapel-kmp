package stapel.probe.compose

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProbeLabelUiTest {
    @Test
    fun rendersTheSlug() = runComposeUiTest {
        setContent { ProbeLabel("Stapel", "Compose") }
        onNodeWithTag(PROBE_LABEL_TAG).assertTextEquals("stapel-compose")
    }
}
