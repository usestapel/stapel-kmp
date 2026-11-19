package stapel.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline

/**
 * An opaque position in a paginated list: an anchor, a page number or an offset, depending on the
 * operation's [PageStyle]. Decodes from a JSON string or number; callers never parse it.
 *
 * @property text the position as sent back to the server.
 */
@Serializable(with = PageCursorSerializer::class)
@JvmInline
public value class PageCursor(public val text: String) {
    /** The cursor text. */
    override fun toString(): String = text
}

internal object PageCursorSerializer : KSerializer<PageCursor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("stapel.wire.PageCursor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PageCursor) {
        encoder.encodeString(value.text)
    }

    override fun deserialize(decoder: Decoder): PageCursor {
        if (decoder !is JsonDecoder) return PageCursor(decoder.decodeString())
        val element = decoder.decodeJsonElement()
        return PageCursor((element as? JsonPrimitive)?.content ?: throw SerializationException("Expected a cursor, got $element"))
    }
}

/** The four pagination envelopes the contracts declare. */
public enum class PageStyle {
    /** `{items, next_anchor, prev_anchor, has_next, has_prev, count}` with `anchor` + `direction` parameters. */
    Anchor,

    /** `{results, count, next}` with a `page` parameter. */
    PageNumber,

    /** `{results, pagination{page, has_next, has_previous, total_count…}, revisions{…}}` with `page`. */
    RevisionPage,

    /** `{count, limit, offset, results}` with `limit` + `offset` parameters. */
    LimitOffset,
}

/**
 * One page of a paginated response. Every generated envelope implements it with computed properties
 * while still exposing all its wire fields (facets, unread counts, revisions).
 */
public interface Page<out T> {
    /** Rows of this page in server order. */
    public val items: List<T>

    /** Where the next page starts, or `null` on the last page. */
    public val nextCursor: PageCursor?

    /** Where the previous page starts, or `null` on the first page or when the style has no way back. */
    public val prevCursor: PageCursor?

    /** Total number of rows across pages, when the server reports it. */
    public val total: Int?
}
