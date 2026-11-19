package stapel.showcase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StoryCanvasTest {
    private val label = Knob.Text("label", "Label", "Live default")
    private val loud = Knob.Flag("loud", "Loud")
    private val story = Story(
        "probe", "Probe", "Tests",
        knobs = listOf(label, loud),
        states = listOf(StoryState("default", "Default"), StoryState("fixed", "Fixed", mapOf("label" to "From state"))),
        anatomy = listOf(AnatomyPart("text", "Text")),
    ) {
        Column {
            BasicText(value(label) + if (value(loud)) "!" else "", Modifier.testTag("probe.text").anatomy("text").clickable { event("tap") })
            BasicText(state.id, Modifier.testTag("probe.state").size(10.dp))
        }
    }

    @Test
    fun valuesFollowOverridesThenStateThenLiveKnobs() = runComposeUiTest {
        val values = KnobValues(story.knobs)
        val events = mutableListOf<String>()
        var state by androidx.compose.runtime.mutableStateOf(story.states[0])
        var overrides by androidx.compose.runtime.mutableStateOf(emptyMap<String, Any?>())
        setContent { StoryCanvas(story, state, values, overrides = overrides, onEvent = { events += it }, showAnatomy = true) }
        onNodeWithTag("probe.text").assertTextEquals("Live default")
        values[label] = "Edited"
        waitForIdle()
        onNodeWithTag("probe.text").assertTextEquals("Edited")
        state = story.states[1]
        waitForIdle()
        onNodeWithTag("probe.text").assertTextEquals("From state")
        onNodeWithTag("probe.state").assertTextEquals("fixed")
        overrides = mapOf("label" to "From cell", "loud" to true)
        waitForIdle()
        onNodeWithTag("probe.text").assertTextEquals("From cell!")
        onNodeWithTag("probe.text").performClick()
        assertEquals(listOf("tap"), events)
        onNodeWithTag("stapel.showcase.anatomy.text").assertExists()
        values.reset()
        overrides = emptyMap()
        state = story.states[0]
        waitForIdle()
        onNodeWithTag("probe.text").assertTextEquals("Live default")
    }

    @Test
    fun anatomyIsDrawnOnlyWhenAsked() = runComposeUiTest {
        setContent { StoryCanvas(story) }
        onNodeWithTag(STORY_CANVAS_TAG).assertExists()
        onNodeWithTag("stapel.showcase.anatomy.text").assertDoesNotExist()
    }
}
