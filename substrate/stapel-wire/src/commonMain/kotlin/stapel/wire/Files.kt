package stapel.wire

import kotlinx.io.RawSink
import kotlinx.io.RawSource

/**
 * A file sent as one part of a multipart request (`format: binary` in a request body).
 *
 * @property fileName file name reported to the server.
 * @property contentType media type, or `null` to let the transport pick `application/octet-stream`.
 * @property size length in bytes when known (enables upload progress totals).
 * @property open opens a fresh source over the bytes; the transport closes it. Called once per attempt.
 */
public class FilePart(
    public val fileName: String,
    public val contentType: String?,
    public val size: Long?,
    public val open: () -> RawSource,
)

/**
 * A binary response body (`application/octet-stream`, `application/zip`). Valid only inside the
 * `consume` block of the download call that produced it.
 */
public interface BinaryBody {
    /** Media type of the response, when the server sent one. */
    public val contentType: String?

    /** Length in bytes, when the server sent one. */
    public val contentLength: Long?

    /** Reads the whole body into memory. */
    public suspend fun readBytes(): ByteArray

    /** Streams the body into [sink] without holding it in memory. */
    public suspend fun writeTo(sink: RawSink)
}
