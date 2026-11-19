package stapel.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The access-token claim that names the account: `user_id`, the primary key as a string. The
 * backend's token handler copies the serialized user (whose identifier field defaults to
 * `user_id`) into every access and refresh token; guests carry it too (`is_anonymous: true`).
 */
public const val ACCOUNT_ID_CLAIM: String = "user_id"

/**
 * The `exp` claim of a JWT, read WITHOUT verifying the signature (the client only schedules refreshes
 * with it; the server is the judge). Null for anything that is not a three-part JWT with a numeric `exp`.
 */
public fun jwtExpiry(token: String): Instant? {
    val exp = jwtPayload(token)?.get("exp") ?: return null
    val seconds = try {
        exp.jsonPrimitive.longOrNull
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    return Instant.fromEpochSeconds(seconds)
}

/** The `iat` claim of a JWT, read without verification; null when absent or not numeric. */
internal fun jwtIssuedAt(token: String): Instant? {
    val iat = jwtPayload(token)?.get("iat") ?: return null
    val seconds = try {
        iat.jsonPrimitive.longOrNull
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    return Instant.fromEpochSeconds(seconds)
}

/**
 * The [ACCOUNT_ID_CLAIM] of a JWT, read WITHOUT verifying the signature (it only names local
 * partitions; the server is the judge). Null when the token is not a JWT or carries no non-empty
 * string or number under that claim.
 */
public fun jwtAccountId(token: String): String? {
    val claim = jwtPayload(token)?.get(ACCOUNT_ID_CLAIM) as? JsonPrimitive ?: return null
    if (claim is JsonNull) return null
    return claim.content.takeIf { it.isNotEmpty() }
}

private fun jwtPayload(token: String): JsonObject? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    val payload = try {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString()
    } catch (_: IllegalArgumentException) {
        return null
    }
    return try {
        Json.parseToJsonElement(payload) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
}
