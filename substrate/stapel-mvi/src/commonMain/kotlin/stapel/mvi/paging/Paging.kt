package stapel.mvi.paging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.state.Freshness
import stapel.result.state.Resource
import stapel.wire.Page
import stapel.wire.PageCursor
import kotlin.time.Clock

/** Where the next page of a paged list stands. */
public sealed interface AppendStatus {
    /** Nothing in flight; more rows may exist. */
    public data object Idle : AppendStatus

    /** The next page is loading. */
    public data object Loading : AppendStatus

    /**
     * The next page failed; the rows stay and the same page is asked again on retry.
     *
     * @property error why it failed.
     */
    public data class Failed(val error: StapelError) : AppendStatus

    /** The last page is on screen. */
    public data object EndReached : AppendStatus
}

/**
 * The state of a paged list, as a presenter holds it and a `LoadList` draws it.
 *
 * @property content the first load as a [Resource]: `Loading` and `Failed` only while nothing is
 *   held; once rows are held it stays `Ready` and [Freshness] carries a pull-to-refresh in flight
 *   (`refreshing`), held rows older than the source wanted (`stale`) and a failed refresh
 *   (`refreshError`). An empty `Ready` is "nothing here" — reachable only from a successful load.
 * @property append the next page.
 * @property total the row count the server reports, when it does.
 */
public data class PagingState<out T>(
    val content: Resource<List<T>> = Resource.Loading,
    val append: AppendStatus = AppendStatus.Idle,
    val total: Int? = null,
) {
    /** The rows held now (empty while loading or failed). */
    val items: List<T> get() = (content as? Resource.Ready)?.data.orEmpty()

    /** True when a load succeeded and returned no rows. */
    val isEmpty: Boolean get() = content is Resource.Ready && content.data.isEmpty()

    /** True when another page can be asked for now: rows held and not refreshing, nothing in flight, not at the end. */
    val canLoadMore: Boolean get() = content is Resource.Ready && !content.freshness.refreshing && append == AppendStatus.Idle
}

/**
 * The paging state machine a presenter owns: first load, append with retry, end detection,
 * pull-to-refresh and stale-while-revalidate, over the wire [Page] of any of the four pagination
 * styles (anchor, page number, revision page, limit/offset — the generated envelope supplies
 * [Page.nextCursor]). Pure: no Compose, no lifecycle; all work runs in [scope].
 *
 * Rules (each a test): an append is asked only with rows held and nothing in flight, and only until
 * a page has no next cursor; a failed append keeps the rows and asks the same cursor again on
 * [retry]; an id already present (by [itemKey]) is replaced in place, never duplicated; [refresh]
 * keeps the rows on screen marked `refreshing`, replaces them with the head page on success and
 * keeps them with `refreshError` on failure; a refresh cancels an append in flight (its request,
 * not only its answer: the late answer is never applied, and the list is not left `Loading` when the
 * refresh fails).
 *
 * @param scope where loads run (the presenter's scope).
 * @param clock stamps [Freshness.fetchedAtMillis].
 * @param itemKey the identity of a row.
 * @param fetch loads the page at a cursor (`null` = the head).
 */
public class PagingMachine<T, E : StapelError>(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val itemKey: (T) -> Any,
    private val fetch: suspend (cursor: PageCursor?) -> StapelResult<Page<T>, E>,
) {
    private val mutable = MutableStateFlow(PagingState<T>())
    private var next: PageCursor? = null
    private var job: Job? = null
    private var generation = 0

    /** The current state. */
    public val state: StateFlow<PagingState<T>> = mutable.asStateFlow()

    /**
     * Starts the first load. With [held] (rows a cache already had) they are shown at once,
     * marked stale and refreshing, while the head page revalidates them; a failed revalidation
     * keeps them with `refreshError`. Calling it again restarts from the head.
     */
    public fun start(held: Resource.Ready<List<T>>? = null) {
        mutable.value = if (held == null) PagingState() else PagingState(held.copy(freshness = held.freshness.copy(stale = true, refreshing = true)))
        loadHead()
    }

    /** Pull-to-refresh: re-reads the head page, keeping the rows on screen meanwhile. */
    public fun refresh() {
        val content = mutable.value.content
        if (content is Resource.Ready) {
            mutable.update { it.copy(content = content.copy(freshness = content.freshness.copy(refreshing = true))) }
        } else {
            mutable.update { it.copy(content = Resource.Loading) }
        }
        loadHead()
    }

    /** Asks for the next page when [PagingState.canLoadMore]; otherwise does nothing. */
    public fun loadMore() {
        if (!mutable.value.canLoadMore) return
        val cursor = next ?: return
        val token = generation
        mutable.update { it.copy(append = AppendStatus.Loading) }
        job = scope.launch {
            val result = call(cursor)
            if (token != generation) return@launch
            when (result) {
                is StapelResult.Ok -> {
                    val page = result.value
                    next = page.nextCursor
                    mutable.update { s ->
                        val ready = s.content as? Resource.Ready ?: return@update s
                        s.copy(
                            content = ready.copy(data = merge(ready.data, page.items)),
                            append = if (page.nextCursor == null) AppendStatus.EndReached else AppendStatus.Idle,
                            total = page.total ?: s.total,
                        )
                    }
                }
                is StapelResult.Err -> mutable.update { it.copy(append = AppendStatus.Failed(result.error)) }
            }
        }
    }

    /** Retries what failed: the first load, or the next page (same cursor). */
    public fun retry() {
        val s = mutable.value
        when {
            s.content is Resource.Failed -> refresh()
            s.append is AppendStatus.Failed -> {
                mutable.update { it.copy(append = AppendStatus.Idle) }
                loadMore()
            }
            s.content is Resource.Ready && s.content.freshness.refreshError != null -> refresh()
        }
    }

    private fun loadHead() {
        generation++
        val token = generation
        job?.cancel()
        // The append in flight (if any) was just cancelled: it is not loading any more.
        mutable.update { if (it.append == AppendStatus.Loading) it.copy(append = AppendStatus.Idle) else it }
        job = scope.launch {
            val result = call(null)
            if (token != generation) return@launch
            when (result) {
                is StapelResult.Ok -> {
                    val page = result.value
                    next = page.nextCursor
                    mutable.value = PagingState(
                        content = Resource.Ready(merge(emptyList(), page.items), Freshness.network(clock.now().toEpochMilliseconds())),
                        append = if (page.nextCursor == null) AppendStatus.EndReached else AppendStatus.Idle,
                        total = page.total,
                    )
                }
                is StapelResult.Err -> mutable.update { s ->
                    when (val c = s.content) {
                        is Resource.Ready -> s.copy(content = c.copy(freshness = c.freshness.copy(refreshing = false, refreshError = result.error)))
                        else -> s.copy(content = Resource.Failed(result.error))
                    }
                }
            }
        }
    }

    private suspend fun call(cursor: PageCursor?): StapelResult<Page<T>, StapelError> = try {
        fetch(cursor)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        StapelResult.Err(TransportFailure.Unexpected(e.message ?: e::class.simpleName ?: "page fetch threw"))
    }

    /** Appends [page] to [rows]; an id already present is replaced in place (the later page wins). */
    private fun merge(rows: List<T>, page: List<T>): List<T> {
        val byKey = LinkedHashMap<Any, T>()
        rows.forEach { byKey[itemKey(it)] = it }
        page.forEach { byKey[itemKey(it)] = it }
        return byKey.values.toList()
    }
}
