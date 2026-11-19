package stapel.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A value of a contract enum. Generated enums are sealed interfaces with one object per known value
 * and an `Unknown(wire)` class, so a value added by a newer server round-trips instead of failing.
 */
public interface WireEnum {
    /** The value exactly as it appears on the wire. */
    public val wire: String
}

/**
 * Base serializer of generated enums: writes [WireEnum.wire] as a JSON string and reads any string
 * through [of], which maps unknown values to the enum's `Unknown` class.
 *
 * @param serialName fully-qualified name of the enum type.
 * @param of the enum's `of(wire)` factory.
 */
public abstract class WireEnumSerializer<T : WireEnum>(
    serialName: String,
    private val of: (String) -> T,
) : KSerializer<T> {
    /** A string descriptor named after the enum. */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    /** Writes the wire value. */
    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.wire)
    }

    /** Reads any string; unknown values become the enum's `Unknown`. */
    override fun deserialize(decoder: Decoder): T = of(decoder.decodeString())
}

/**
 * Serializer for the contract's `oneOf [XEnum, BlankEnum, NullEnum]` fields: `""` and `null` both
 * decode to `null`. A `null` value is written as JSON `null` when [nullAsBlank] is false (the union
 * admits `null`) and as `""` otherwise.
 *
 * @param inner serializer of the enum itself.
 * @param nullAsBlank whether `null` is written as the empty string.
 */
public abstract class BlankAsNullSerializer<T : Any>(
    private val inner: KSerializer<T>,
    private val nullAsBlank: Boolean,
) : KSerializer<T?> {
    /** A string descriptor derived from the enum's. */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        inner.descriptor.serialName + "?blank",
        PrimitiveKind.STRING,
    )

    /** Writes the value, or `null` / `""` per the union. */
    override fun serialize(encoder: Encoder, value: T?) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Wire types are JSON-only")
        when {
            value != null -> json.encodeSerializableValue(inner, value)
            nullAsBlank -> json.encodeString("")
            else -> json.encodeJsonElement(JsonNull)
        }
    }

    /** Reads `""` and `null` as `null`, anything else with the enum's serializer. */
    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("Wire types are JSON-only")
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        if (element is JsonPrimitive && element.isString && element.content.isEmpty()) return null
        return json.json.decodeFromJsonElement(inner, element)
    }
}
