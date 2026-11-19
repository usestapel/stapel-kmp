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
 * A decimal number kept as its exact text (`format: decimal` in the contract, e.g. prices).
 *
 * Never converted to a binary floating-point number: `"19.990"` decodes and encodes byte-for-byte.
 * Arithmetic belongs to the domain library that owns the meaning (currency, quantity).
 *
 * @property text the canonical text: optional `-`, digits, optional `.` and digits.
 */
@Serializable(with = DecimalSerializer::class)
@JvmInline
public value class Decimal(public val text: String) {
    init {
        require(DECIMAL_PATTERN.matches(text)) { "Not a decimal number: \"$text\"" }
    }

    /** The exact text. */
    override fun toString(): String = text
}

private val DECIMAL_PATTERN = Regex("-?[0-9]+(\\.[0-9]+)?")

/** Reads a decimal from a JSON string or a JSON number (keeping the number's text); writes a string. */
internal object DecimalSerializer : KSerializer<Decimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("stapel.wire.Decimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Decimal) {
        encoder.encodeString(value.text)
    }

    override fun deserialize(decoder: Decoder): Decimal {
        val text = if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            (element as? JsonPrimitive)?.content ?: throw SerializationException("Expected a decimal, got $element")
        } else {
            decoder.decodeString()
        }
        return try {
            Decimal(text)
        } catch (e: IllegalArgumentException) {
            throw SerializationException(e.message, e)
        }
    }
}
