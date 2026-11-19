package stapel.wire

import stapel.result.StapelError
import stapel.result.StapelResult

/**
 * Executes contract operations. Implemented by `stapel-network` (Ktor, recover-once pipeline) and by
 * test fakes; generated `<Brick>ApiClient` classes call nothing else.
 *
 * Contract for implementations: never throw for an HTTP, transport or decoding outcome (return
 * [StapelResult.Err] built through [OperationSpec.errors]); let `CancellationException` propagate;
 * resolve 401 refresh and step-up challenges before returning; encode and decode with [WireJson].
 */
public interface ApiTransport {
    /** Executes [spec] with [call] and decodes the success per [OperationSpec.responseKind]. */
    public suspend fun <B, T, E : StapelError> execute(
        spec: OperationSpec<B, T, E>,
        call: OperationCall<B>,
    ): StapelResult<T, E>

    /**
     * Executes a binary-response operation; the body is valid only inside [consume], whose value
     * becomes the result.
     */
    public suspend fun <B, R, E : StapelError> download(
        spec: OperationSpec<B, Unit, E>,
        call: OperationCall<B>,
        consume: suspend (BinaryBody) -> R,
    ): StapelResult<R, E>

    /**
     * The absolute URL of a browser-redirect operation (OAuth, SSO, magic link), for handing to a
     * browser or custom tab. No request is made.
     */
    public fun url(spec: OperationSpec<*, *, *>, call: OperationCall<*>): String
}

/**
 * Walks a paginated operation page by page. Generated `<operation>Pager(…)` extensions build one with
 * a [fetch] that owns the cursor parameter.
 *
 * Not thread-safe: one collector drives it.
 *
 * @param T row type.
 * @param E the operation's error type.
 */
public class Pager<T, E : StapelError>(
    private val fetch: suspend (cursor: PageCursor?) -> StapelResult<Page<T>, E>,
) {
    private var cursor: PageCursor? = null
    private var started = false
    private var exhausted = false

    /** Whether [next] can return another page. */
    public val hasNext: Boolean get() = !exhausted

    /** Total rows reported by the last page, when the server reports one. */
    public var total: Int? = null
        private set

    /**
     * Fetches the next page and returns its rows. After the last page [hasNext] is false and further
     * calls return an empty list without a request. A failure leaves the position unchanged, so the
     * same call can be retried.
     */
    public suspend fun next(): StapelResult<List<T>, E> {
        if (exhausted) return StapelResult.Ok(emptyList())
        return when (val result = fetch(if (started) cursor else null)) {
            is StapelResult.Ok -> {
                val page = result.value
                started = true
                cursor = page.nextCursor
                exhausted = page.nextCursor == null
                total = page.total
                StapelResult.Ok(page.items)
            }
            is StapelResult.Err -> result
        }
    }

    /** Starts again from the first page. */
    public fun reset() {
        cursor = null
        started = false
        exhausted = false
        total = null
    }
}
