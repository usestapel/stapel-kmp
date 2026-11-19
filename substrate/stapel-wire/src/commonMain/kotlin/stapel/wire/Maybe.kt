package stapel.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A request field with three states: not sent ([Absent]), sent as `null` ([Present] of `null`) or sent
 * with a value. Generated PATCH models declare every field as `Maybe<T> = Maybe.Absent`, so a partial
 * update sends exactly the keys the caller set.
 *
 * Encoding relies on the wire JSON configuration ([WireJson]): a field equal to its default
 * ([Absent]) is omitted.
 */
@Serializable(with = MaybeSerializer::class)
public sealed interface Maybe<out T> {
    /** The key is left out of the request. */
    public data object Absent : Maybe<Nothing>

    /** The key is sent with [value]; `null` is sent as JSON `null` when `T` is nullable. */
    public data class Present<out T>(
        /** The value sent. */
        val value: T,
    ) : Maybe<T>

    /** Holder of [ofNullable]. */
    public companion object {
        /** [Present] of [value], or [Absent] when [value] is `null` (for fields that cannot be cleared). */
        public fun <T : Any> ofNullable(value: T?): Maybe<T> = if (value == null) Absent else Present(value)
    }
}

/** The value if [Maybe.Present], else `null`. */
public fun <T> Maybe<T>.getOrNull(): T? = (this as? Maybe.Present)?.value

/**
 * Serializer of [Maybe]: [Maybe.Present] is written as its value; [Maybe.Absent] must never reach the
 * encoder (it is the property default and is omitted). Decoding always yields [Maybe.Present].
 */
public class MaybeSerializer<T>(private val inner: KSerializer<T>) : KSerializer<Maybe<T>> {
    /** The inner value's descriptor. */
    override val descriptor: SerialDescriptor = inner.descriptor

    /** Writes the present value; refuses [Maybe.Absent]. */
    override fun serialize(encoder: Encoder, value: Maybe<T>) {
        when (value) {
            Maybe.Absent -> throw SerializationException(
                "Maybe.Absent reached the encoder: the property must default to Maybe.Absent and the Json " +
                    "configuration must not encode defaults (use WireJson).",
            )
            is Maybe.Present -> encoder.encodeSerializableValue(inner, value.value)
        }
    }

    /** Reads a value (or `null`) as [Maybe.Present]. */
    override fun deserialize(decoder: Decoder): Maybe<T> = Maybe.Present(decoder.decodeSerializableValue(inner))
}
