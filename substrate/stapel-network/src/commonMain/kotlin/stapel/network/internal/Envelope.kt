package stapel.network.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.VerificationChallenge
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A parsed error answer: the envelope and, for a step-up 403, its challenge. */
internal class DecodedError(val envelope: ErrorEnvelope, val challenge: VerificationChallenge?)

/**
 * The Stapel error envelope (`localizable_error`, `error`, `error_language` or legacy `language`,
 * `params`, and a top-level `verification` object on a step-up 403). Anything else — an HTML page
 * from a proxy, an empty body — becomes the synthesized `stapel.http.<status>`.
 */
internal fun decodeError(status: Int, body: ByteArray, requestId: String?): DecodedError {
    val root = parseObject(body) ?: return DecodedError(ErrorEnvelope.synthesized(status, requestId), null)
    val code = (root["localizable_error"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        ?: return DecodedError(ErrorEnvelope.synthesized(status, requestId), null)
    val envelope = ErrorEnvelope(
        code = ErrorCode(code),
        status = status,
        params = (root["params"] as? JsonObject)?.let(::flatten) ?: ErrorParams.Empty,
        serverText = root.string("error"),
        serverLanguage = root.string("error_language") ?: root.string("language"),
        requestId = requestId,
    )
    val verification = root["verification"] as? JsonObject
    val challengeId = verification?.string("challenge_id")
    val challenge = if (status == 403 && verification != null && challengeId != null) {
        VerificationChallenge(
            challengeId = challengeId,
            scope = verification.string("scope"),
            factors = (verification["factors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?: emptyList(),
            raw = flatten(verification),
        )
    } else {
        null
    }
    return DecodedError(envelope, challenge)
}

/** `Retry-After` in delta-seconds form (the HTTP-date form is ignored). */
internal fun parseRetryAfter(value: String?): Duration? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds

private fun parseObject(body: ByteArray): JsonObject? {
    if (body.isEmpty()) return null
    return try {
        Json.parseToJsonElement(body.decodeToString()) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun flatten(obj: JsonObject): ErrorParams = ErrorParams(
    obj.mapNotNull { (k, v) -> text(v)?.let { k to it } }.toMap(),
)

private fun text(value: JsonElement): String? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> value.content
    else -> value.toString()
}
