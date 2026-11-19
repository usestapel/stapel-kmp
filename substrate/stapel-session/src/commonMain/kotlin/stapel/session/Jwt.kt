package stapel.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The `exp` claim of a JWT, read WITHOUT verifying the signature (the client only schedules refreshes
 * with it; the server is the judge). Null for anything that is not a three-part JWT with a numeric `exp`.
 */
public fun jwtExpiry(token: String): Instant? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    val payload = try {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString()
    } catch (_: IllegalArgumentException) {
        return null
    }
    val json = try {
        Json.parseToJsonElement(payload) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    val exp = (json["exp"] ?: return null).let { element ->
        try {
            element.jsonPrimitive.longOrNull
        } catch (_: IllegalArgumentException) {
            null
        }
    } ?: return null
    return Instant.fromEpochSeconds(exp)
}
