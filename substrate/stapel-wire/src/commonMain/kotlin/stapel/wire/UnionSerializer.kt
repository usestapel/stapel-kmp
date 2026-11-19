package stapel.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/**
 * Base serializer of generated `oneOf` unions. The wire object is read whole, a variant is chosen by
 * [select] (a discriminator value or a key probe, decided by the generator), and the object is decoded
 * with that variant's own serializer. An object no rule recognizes becomes [unknown]'s value — the
 * union's `Unknown(raw)` variant for response unions — so a new server variant never crashes a client.
 *
 * @param serialName fully-qualified name of the union type.
 */
public abstract class UnionSerializer<T : Any>(serialName: String) : KSerializer<T> {
    /** A class descriptor named after the union (JSON-only serializer). */
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName)

    /** The serializer of the variant [obj] belongs to, or `null` when no rule matches. */
    protected abstract fun select(obj: JsonObject): KSerializer<out T>?

    /** The value for an unrecognized object; `null` (the default) makes decoding fail. */
    protected open fun unknown(obj: JsonObject): T? = null

    /** The serializer that writes [value], or `null` when [value] carries its raw JSON ([rawOf]). */
    protected abstract fun serializerOf(value: T): KSerializer<out T>?

    /** The raw object of an `Unknown` variant. */
    protected open fun rawOf(value: T): JsonObject? = null

    /** Reads the whole object, selects a variant and decodes with it. */
    override fun deserialize(decoder: Decoder): T {
        val json = decoder as? JsonDecoder ?: throw SerializationException("Wire types are JSON-only")
        val element = json.decodeJsonElement()
        val obj = element as? JsonObject
            ?: throw SerializationException("${descriptor.serialName}: expected an object, got $element")
        val variant = select(obj)
            ?: return unknown(obj)
                ?: throw SerializationException("${descriptor.serialName}: no variant matches $obj")
        return json.json.decodeFromJsonElement(variant, obj)
    }

    /** Writes the variant with its own serializer, or the raw object of an unknown variant. */
    override fun serialize(encoder: Encoder, value: T) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Wire types are JSON-only")
        val variant = serializerOf(value)
        if (variant == null) {
            val raw = rawOf(value) ?: throw SerializationException("${descriptor.serialName}: cannot encode $value")
            json.encodeJsonElement(raw)
            return
        }
        @Suppress("UNCHECKED_CAST")
        json.encodeJsonElement(json.json.encodeToJsonElement(variant as KSerializer<T>, value))
    }
}
