package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import stapel.i18n.CoreUiStrings
import stapel.mvi.paging.AppendStatus
import stapel.mvi.paging.PagingState
import stapel.result.state.Resource
import stapel.ui.skin.ListStatusKind
import stapel.ui.skin.ListStatusSpec
import stapel.ui.skin.PullToRefreshSpec

/** Suffix of the test tag of a [LoadList]'s last-row status (appending, failed, end). */
public const val LOAD_LIST_APPEND_TAG: String = ".append"

/** Suffix of the test tag of a [LoadList]'s stale-rows status. */
public const val LOAD_LIST_STALE_TAG: String = ".stale"

/** The status row of a paged list through the skin ([ListStatusSpec] lists the duties). */
@Composable
public fun StapelListStatus(
    kind: ListStatusKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String? = null,
) {
    StapelTheme.skin.ListStatus(ListStatusSpec(kind, message, actionLabel, onAction, testTag), modifier)
}

/**
 * A lazy paged list over a presenter's [PagingState] (`stapel-mvi`'s `PagingMachine`), with every
 * arm drawn: the first load (progress), a failed first load ([ErrorState] with [onRetry]), "nothing
 * here" ([empty], only after a load that succeeded), the rows, the next page loading / failed (with
 * [onRetry]) / the end reached as the last row ([ListStatusKind]), held rows that are stale or failed
 * to refresh as the first row, and pull-to-refresh around it all when [onRefresh] is given (the
 * skin's `PullToRefresh`, spinning while `freshness.refreshing`).
 *
 * [onLoadMore] is called when the last visible row comes within [prefetchDistance] rows of the end
 * and [PagingState.canLoadMore] holds — also when the first page does not fill the screen; the
 * machine ignores calls it cannot serve. The list scrolls itself: do not put it inside a
 * `StapelPage(scrollable = true)`.
 *
 * @param key a row's stable key (keeps scroll position and state across pages and refreshes).
 * @param showEnd draw the "that's everything" row once the end is reached (not on an empty list).
 * @param testTag tags the list; its status rows get [LOAD_LIST_APPEND_TAG] / [LOAD_LIST_STALE_TAG] appended.
 */
@Composable
public fun <T> LoadList(
    state: PagingState<T>,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    key: ((T) -> Any)? = null,
    listState: LazyListState = rememberLazyListState(),
    prefetchDistance: Int = 3,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showEnd: Boolean = true,
    testTag: String? = null,
    item: @Composable LazyItemScope.(T) -> Unit,
) {
    when (val content = state.content) {
        is Resource.Loading -> Box(modifier.fillMaxSize().optionalTag(testTag), contentAlignment = Alignment.Center) {
            StapelProgress(label = stapelString(CoreUiStrings.Loading))
        }
        is Resource.Failed -> Box(modifier.fillMaxSize().optionalTag(testTag), contentAlignment = Alignment.TopCenter) {
            ErrorState(content.error, Modifier.fillMaxWidth(), onRetry = onRetry)
        }
        is Resource.Ready -> {
            val latest by rememberUpdatedState(state)
            val loadMore by rememberUpdatedState(onLoadMore)
            LaunchedEffect(listState, prefetchDistance) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index
                    last != null && last >= info.totalItemsCount - 1 - prefetchDistance && latest.canLoadMore
                }.distinctUntilChanged().collect { due -> if (due) loadMore() }
            }
            val freshness = content.freshness
            val rows = content.data
            val body: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxSize().optionalTag(testTag), state = listState, contentPadding = contentPadding) {
                    val showStale = freshness.refreshError != null || (freshness.stale && !freshness.refreshing)
                    if (showStale) {
                        item(key = "stapel.loadlist.stale", contentType = "stapel.loadlist.status") {
                            StapelListStatus(
                                ListStatusKind.Stale,
                                stapelString(CoreUiStrings.ShowingSavedData),
                                actionLabel = stapelString(if (onRefresh != null) CoreUiStrings.Refresh else CoreUiStrings.Retry),
                                onAction = onRefresh ?: onRetry,
                                testTag = testTag?.let { it + LOAD_LIST_STALE_TAG },
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = "stapel.loadlist.empty", contentType = "stapel.loadlist.empty") { empty() }
                    } else {
                        items(rows, key = key) { row -> item(row) }
                    }
                    val appendTag = testTag?.let { it + LOAD_LIST_APPEND_TAG }
                    when (val append = state.append) {
                        AppendStatus.Loading -> item(key = "stapel.loadlist.append", contentType = "stapel.loadlist.status") {
                            StapelListStatus(ListStatusKind.Appending, stapelString(CoreUiStrings.LoadingMore), testTag = appendTag)
                        }
                        is AppendStatus.Failed -> item(key = "stapel.loadlist.append", contentType = "stapel.loadlist.status") {
                            StapelListStatus(
                                ListStatusKind.AppendFailed,
                                stapelString(CoreUiStrings.LoadMoreFailed),
                                actionLabel = stapelString(CoreUiStrings.Retry),
                                onAction = onRetry,
                                testTag = appendTag,
                            )
                        }
                        AppendStatus.EndReached -> if (showEnd && rows.isNotEmpty()) {
                            item(key = "stapel.loadlist.append", contentType = "stapel.loadlist.status") {
                                StapelListStatus(ListStatusKind.End, stapelString(CoreUiStrings.EndOfList), testTag = appendTag)
                            }
                        }
                        AppendStatus.Idle -> Unit
                    }
                }
            }
            if (onRefresh != null) {
                StapelTheme.skin.PullToRefresh(
                    PullToRefreshSpec(freshness.refreshing, onRefresh, stapelString(CoreUiStrings.Refresh)),
                    modifier.fillMaxSize(),
                    body,
                )
            } else {
                Box(modifier.fillMaxSize()) { body() }
            }
        }
    }
}

private fun Modifier.optionalTag(tag: String?): Modifier = if (tag == null) this else testTag(tag)
