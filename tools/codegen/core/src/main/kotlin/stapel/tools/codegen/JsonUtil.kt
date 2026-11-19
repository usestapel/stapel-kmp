package stapel.tools.codegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest

internal val parseJson: Json = Json { isLenient = false }

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.scalarText(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

internal fun JsonObject?.orEmptyObj(): JsonObject = this ?: JsonObject(emptyMap())

internal fun JsonElement.asObj(): JsonObject? = this as? JsonObject

/** Lower-case hex SHA-256 of [bytes]. */
fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Compact single-line JSON with keys sorted recursively: the canonical form for examples and hashes. */
internal fun canonicalJson(element: JsonElement): String = sortKeys(element).toString()

internal fun sortKeys(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sortKeys(it.value) })
    is JsonArray -> JsonArray(element.map(::sortKeys))
    else -> element
}

private val prettyJson = Json { prettyPrint = true }

/** Pretty JSON with sorted keys and a trailing newline: the committed form of every file we write. */
fun prettyJson(element: JsonElement): String = prettyJson.encodeToString(JsonElement.serializer(), sortKeys(element)) + "\n"

/** Pretty JSON keeping the insertion order of [element] (for files whose order is meaningful). */
fun prettyJsonOrdered(element: JsonElement): String = prettyJson.encodeToString(JsonElement.serializer(), element) + "\n"

/** Parses [text] as a JSON object. */
fun parseJsonObject(text: String): JsonObject = parseJson.parseToJsonElement(text) as JsonObject
