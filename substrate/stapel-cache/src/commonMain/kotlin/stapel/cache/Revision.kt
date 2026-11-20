package stapel.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.wire.CacheKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A collection kept in sync by revision deltas (the backend's revision pagination: `min_revision`,
 * `max_revision`, `page`, `page_size`; a response carries rows, deleted ids and the table's global
 * maximum revision). Generated per operation by the codegen, or written by a brick domain library.
 *
 * @property key the collection's key; its `scope` picks the partition (public catalogues are app-scoped).
 * @property itemSerializer encodes one row for storage.
 * @property schemaHash hash of the row schema; a collection stored under another hash is dropped on
 *   read and synced from scratch.
 * @property itemId the row's identity (what `deleted_ids` name).
 * @property pageSize rows asked for per page.
 * @property maxPages the page budget of one sync; a walk that needs more is truncated, publishes
 *   nothing and leaves the stored revision where it was.
 * @property policy when a subscription syncs ([ReadPolicy.StaleWhileRevalidate]'s `freshFor`, a
 *   [ReadPolicy.CacheFirst]'s `freshFor`; [ReadPolicy.NetworkFirst] always) and how old a held
 *   collection may be to be shown (`retainFor`). Held rows are shown while a sync runs.
 *   [ReadPolicy.NetworkOnly] is refused: a revision collection is a stored snapshot by definition.
 */
public class RevisionSpec<T>(
    public val key: CacheKey,
    public val itemSerializer: KSerializer<T>,
    public val schemaHash: String,
    public val itemId: (T) -> String,
    public val pageSize: Int = 100,
    public val maxPages: Int = 1000,
    public val policy: ReadPolicy = ReadPolicy.StaleWhileRevalidate(5.minutes, Duration.INFINITE),
) {
    init {
        require(pageSize > 0 && maxPages > 0) { "pageSize and maxPages must be positive" }
        require(policy != ReadPolicy.NetworkOnly) { "a revision collection cannot be NetworkOnly" }
    }

    /** Debug form. */
    override fun toString(): String = "RevisionSpec($key, pageSize=$pageSize, maxPages=$maxPages, policy=$policy)"
}

/**
 * One page request of a revision sync.
 *
 * @property minRevision rows with a revision above this (`null` on a full sync: every row).
 * @property maxRevision rows with a revision up to this; pinned to the first page's global maximum so
 *   a walk sees one consistent revision range (`null` on the first page).
 * @property page 1-based page number.
 * @property pageSize rows per page.
 */
public data class RevisionRequest(val minRevision: Long?, val maxRevision: Long?, val page: Int, val pageSize: Int)

/**
 * One page of a revision sync response.
 *
 * @property results rows of this page.
 * @property deletedIds ids deleted since the request's `minRevision`.
 * @property hasNext another page follows.
 * @property globalMax the highest revision in the table when the page was served.
 */
public class RevisionPage<out T>(
    public val results: List<T>,
    public val deletedIds: List<String>,
    public val hasNext: Boolean,
    public val globalMax: Long,
)

/**
 * What one [RevisionCollection.sync] did.
 *
 * @property fullSync it walked the whole table (no stored revision, or the stored one was dropped).
 * @property pages pages requested.
 * @property truncated the page budget ran out; nothing was published and the revision did not move.
 * @property revision the stored revision after the sync.
 */
public data class SyncReport(val fullSync: Boolean, val pages: Int, val truncated: Boolean, val revision: Long?)

/**
 * A revision-synced collection. Rows are stored as a list (head + rows) plus the last synced
 * revision as a cursor, in the key's partition; with the memory store the first sync of a process
 * is a full one and later syncs are deltas.
 *
 * Rules: a sync publishes on completion only (subscribers never see half a walk); a failed or
 * truncated walk leaves the held rows and the stored revision untouched; a delta updates rows in
 * place, appends new ids and drops deleted ones; one sync runs at a time per collection.
 */
public interface RevisionCollection<T> {
    /** The rows as a [Resource]; subscribing syncs per the spec's policy. */
    public val state: Flow<Resource<List<T>>>

    /** Runs one sync now (waits for the account for a user-scoped key). */
    public suspend fun sync(): StapelResult<SyncReport, StapelError>
}
