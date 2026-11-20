package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.mvi.paging.AppendStatus
import stapel.mvi.paging.PagingMachine
import stapel.mvi.paging.PagingState
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.tokens.ThemeMode
import stapel.ui.skin.FoundationSkin
import stapel.wire.Page
import stapel.wire.PageCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private data class NumberPage(override val items: List<Int>, override val nextCursor: PageCursor?, override val total: Int?) : Page<Int> {
    override val prevCursor: PageCursor? get() = null
}

private object Epoch : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

/** The lazy paged list: every arm drawn, appends asked at the end, status rows wired to the callbacks. */
@OptIn(ExperimentalTestApi::class)
class LoadListTest {
    private var loadMore = 0
    private var retry = 0
    private var refresh = 0

    private fun ready(rows: List<Int>, freshness: Freshness = Freshness.network(1)) = Resource.Ready(rows, freshness)

    private fun ComposeUiTest.show(state: () -> PagingState<Int>, refreshable: Boolean = false) {
        val strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, reduceMotion = true) {
                Box(Modifier.size(390.dp, 600.dp)) {
                    LoadList(
                        state = state(),
                        onLoadMore = { loadMore++ },
                        onRetry = { retry++ },
                        empty = { StapelText("Nothing yet", Modifier.testTag("empty")) },
                        onRefresh = if (refreshable) ({ refresh++ }) else null,
                        key = { it },
                        testTag = "list",
                    ) { row -> Row(row) }
                }
            }
        }
        waitForIdle()
    }

    @Composable
    private fun Row(n: Int) {
        StapelText("Row $n", Modifier.fillMaxWidth().height(60.dp).testTag("row.$n"))
    }

    @Test
    fun loadingFailedAndEmptyAreThreeDifferentArms() = runComposeUiTest {
        var state by mutableStateOf(PagingState<Int>())
        show({ state })
        assertTrue(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("empty").assertDoesNotExist()

        state = PagingState(Resource.Failed(TransportFailure.Offline()))
        waitForIdle()
        onNodeWithTag("empty").assertDoesNotExist()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retry)

        state = PagingState(ready(emptyList()), AppendStatus.EndReached)
        waitForIdle()
        onNodeWithTag("empty").assertExists()
        onNodeWithText("That's everything").assertDoesNotExist()
        assertEquals(0, loadMore, "an empty list asked for more")
    }

    @Test
    fun scrollingToTheEndAsksForMoreOnlyWhenItCan() = runComposeUiTest {
        var state by mutableStateOf(PagingState(ready((1..30).toList())))
        show({ state })
        assertEquals(0, loadMore, "asked for more with the end out of sight")
        onNodeWithTag("list").performScrollToIndex(29)
        waitForIdle()
        assertEquals(1, loadMore)

        state = state.copy(append = AppendStatus.Loading)
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(30)
        onNode(hasTestTag("list.append")).assertExists()
        onNodeWithText("Loading more…", useUnmergedTree = true).assertExists()
        assertEquals(1, loadMore, "asked again while a page was loading")

        state = state.copy(append = AppendStatus.Failed(TransportFailure.Offline()))
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(30)
        onNode(hasText("Try again") and hasAnyAncestor(hasTestTag("list.append"))).performClick()
        assertEquals(1, retry)
        assertEquals(1, loadMore, "a failed page was asked again by scrolling instead of by retry")

        state = state.copy(append = AppendStatus.EndReached)
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(30)
        onNodeWithText("That's everything", useUnmergedTree = true).assertExists()
        assertEquals(1, loadMore)
    }

    @Test
    fun aShortFirstPageAsksForMoreAtOnce() = runComposeUiTest {
        show({ PagingState(ready(listOf(1, 2))) })
        assertEquals(1, loadMore)
    }

    @Test
    fun heldRowsThatAreStaleOrFailedToRefreshSaySo() = runComposeUiTest {
        var state by mutableStateOf(PagingState(ready(listOf(1, 2), Freshness(Origin.Disk, 1, stale = true, refreshing = true))))
        show({ state }, refreshable = true)
        onNodeWithTag("list.stale").assertDoesNotExist()

        state = PagingState(ready(listOf(1, 2), Freshness(Origin.Disk, 1, refreshError = TransportFailure.Offline())))
        waitForIdle()
        onNodeWithText("Showing saved data", useUnmergedTree = true).assertExists()
        onNode(hasText("Refresh") and hasAnyAncestor(hasTestTag("list.stale"))).performClick()
        assertEquals(1, refresh)

        state = PagingState(ready(listOf(1, 2)))
        waitForIdle()
        onNodeWithTag("list.stale").assertDoesNotExist()
    }

    @Test
    fun pullToRefreshIsTheSkinsAndReachableWithoutAGesture() = runComposeUiTest {
        show({ PagingState(ready((1..30).toList())) }, refreshable = true)
        val holder = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes()
            .single { n -> n.config[SemanticsActions.CustomActions].any { it.label == "Refresh" } }
        runOnIdle { holder.config[SemanticsActions.CustomActions].single { it.label == "Refresh" }.action() }
        assertEquals(1, refresh)
    }

    @Test
    fun withThePagingMachineScrollingWalksToTheEnd() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pages = (0 until 4).map { p -> NumberPage((p * 10 + 1..p * 10 + 10).toList(), if (p < 3) PageCursor("${p + 1}") else null, 40) }
        var failOnce = true
        val machine = PagingMachine<Int, StapelError>(scope, Epoch, { it }) { cursor ->
            val index = cursor?.text?.toInt() ?: 0
            if (index == 2 && failOnce) {
                failOnce = false
                StapelResult.Err(TransportFailure.Offline())
            } else {
                StapelResult.Ok(pages[index])
            }
        }
        machine.start()
        val strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, reduceMotion = true) {
                val state by machine.state.collectAsState()
                Box(Modifier.size(390.dp, 600.dp)) {
                    LoadList(state, machine::loadMore, machine::retry, { StapelText("Nothing yet") }, onRefresh = machine::refresh, key = { it }, testTag = "list") { Row(it) }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(9)
        waitForIdle()
        assertEquals(20, machine.state.value.items.size)
        onNodeWithTag("list").performScrollToIndex(20)
        waitForIdle()
        onNode(hasText("Try again") and hasAnyAncestor(hasTestTag("list.append"))).performClick()
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(machine.state.value.items.size - 1)
        waitForIdle()
        assertEquals((1..40).toList(), machine.state.value.items)
        assertEquals(AppendStatus.EndReached, machine.state.value.append)
        onNodeWithTag("list").performScrollToIndex(40)
        onNodeWithText("That's everything", useUnmergedTree = true).assertExists()
        scope.cancel()
    }
}
