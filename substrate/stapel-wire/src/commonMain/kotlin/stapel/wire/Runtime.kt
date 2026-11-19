package stapel.wire

import kotlinx.serialization.json.Json

/**
 * Version of the contract between generated code and this runtime. The generator stamps the value it
 * targets into every generated file header and into a constant each brick's tests compare with this
 * one; a mismatch means the brick was generated for another runtime.
 */
public const val CODEGEN_RUNTIME_ABI: Int = 1

/**
 * The JSON configuration generated serializers are written for: unknown keys ignored (additive server
 * changes are safe), defaults not encoded (an omitted optional field and `Maybe.Absent` stay off the
 * wire), `null` written explicitly when set, no coercion, no lenient parsing. Transports encode and
 * decode generated models only with this instance.
 */
public val WireJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = false
    isLenient = false
}
