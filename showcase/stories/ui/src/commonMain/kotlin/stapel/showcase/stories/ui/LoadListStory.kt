package stapel.showcase.stories.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import stapel.mvi.paging.AppendStatus
import stapel.mvi.paging.PagingState
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Origin
import stapel.result.state.Resource
import stapel.showcase.Knob
import stapel.showcase.Story
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.showcase.StoryTarget
import stapel.ui.EmptyState
import stapel.ui.LoadList
import stapel.ui.StapelIconIds
import stapel.ui.StapelListItem

/** The arms of a paged list the story can show. */
internal enum class PagedListArm { Rows, Appending, AppendFailed, End, Stale, Empty, Failed, Loading }

private val arm = Knob.enumOf("arm", "State", PagedListArm.Appending)

private val orders = (1..4).map { "Order #10$it" }

private fun pagingStateOf(arm: PagedListArm): PagingState<String> {
    val ready = Resource.Ready(orders, Freshness(Origin.Network, 0))
    return when (arm) {
        PagedListArm.Rows -> PagingState(ready)
        PagedListArm.Appending -> PagingState(ready, AppendStatus.Loading)
        PagedListArm.AppendFailed -> PagingState(ready, AppendStatus.Failed(TransportFailure.Offline()))
        PagedListArm.End -> PagingState(ready, AppendStatus.EndReached)
        PagedListArm.Stale -> PagingState(Resource.Ready(orders, Freshness(Origin.Disk, 0, stale = true, refreshError = TransportFailure.Offline())))
        PagedListArm.Empty -> PagingState(Resource.Ready(emptyList(), Freshness(Origin.Network, 0)), AppendStatus.EndReached)
        PagedListArm.Failed -> PagingState(Resource.Failed(TransportFailure.Offline()))
        PagedListArm.Loading -> PagingState()
    }
}

/** The lazy paged list over a `PagingState`, every arm a state. */
internal val loadList = Story(
    "load-list", "Paged list", "Composites", StoryKind.Composite,
    description = "A lazy list over a presenter's PagingState: first load, failure with retry, empty only after a load, " +
        "the next page loading / failed / the end as the last row, stale rows announced as the first row, pull to refresh.",
    knobs = listOf(arm),
    states = listOf(
        StoryState("default", "Loading more"),
        StoryState("append-failed", "Next page failed", mapOf("arm" to PagedListArm.AppendFailed)),
        StoryState("end", "The end", mapOf("arm" to PagedListArm.End)),
        StoryState("stale", "Stale rows", mapOf("arm" to PagedListArm.Stale)),
        StoryState("empty", "Empty", mapOf("arm" to PagedListArm.Empty)),
        StoryState("failed", "First load failed", mapOf("arm" to PagedListArm.Failed)),
        StoryState("rtl", "Stale, right to left", mapOf("arm" to PagedListArm.Stale), rtl = true, locale = "ar"),
    ),
    covers = setOf(StoryTarget.Slot("ListStatus")),
    snippet = "val state by presenter.state.collectAsState()\n" +
        "LoadList(state.orders, presenter::loadMore, presenter::retry, empty = { EmptyState(\"No orders yet\") }, onRefresh = presenter::refresh, key = { it.id }) { OrderRow(it) }",
) {
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        LoadList(
            state = pagingStateOf(value(arm)),
            onLoadMore = { event("load more") },
            onRetry = { event("retry") },
            empty = { EmptyState("No orders yet", description = "Your orders show up here.", icon = StapelIconIds.Info) },
            onRefresh = { event("refresh") },
            key = { it },
        ) { order -> StapelListItem(order, supporting = "Delivered", onClick = { event(order) }) }
    }
}
