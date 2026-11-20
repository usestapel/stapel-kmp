package stapel.cdn.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.CODEGEN_RUNTIME_ABI
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves the generated stapel-cdn client against its pinned contract (stapel-cdn 0.31.2, profile 2). */
class GeneratedContractTest {
    @Test
    fun generatedCodeTargetsThisRuntimeAndThePin() {
        assertEquals(CODEGEN_RUNTIME_ABI, CdnContract.RUNTIME_ABI)
        assertEquals("0.31.3", CdnContract.VERSION)
        assertEquals(2, CdnContract.PROFILE)
    }

    private fun normal(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.filter { it.value !is JsonNull }.sortedBy { it.key }.associate { it.key to normal(it.value) })
        is JsonArray -> JsonArray(e.map(::normal))
        else -> e
    }

    @Test
    fun everyContractExampleRoundTrips() {
        val failures = CdnContractExamples.all.mapNotNull { ex ->
            try {
                @Suppress("UNCHECKED_CAST")
                val s = ex.serializer as KSerializer<Any?>
                val back = WireJson.encodeToJsonElement(s, WireJson.decodeFromString(s, ex.json))
                if (normal(back) == normal(WireJson.parseToJsonElement(ex.json))) null else "${ex.label}: re-encodes as $back"
            } catch (e: SerializationException) {
                "${ex.label}: ${e.message?.lineSequence()?.first()}"
            }
        }
        assertTrue(CdnContractExamples.all.isNotEmpty())
        assertEquals(emptyList(), failures)
    }

    @Test
    fun theAvatarUploadIsAnAuthenticatedMultipartPost() {
        val op = CdnOperations.uploadAvatar
        assertEquals("POST", op.method)
        assertEquals("/cdn/api/v1/upload/avatar/", op.path)
        assertEquals("cdn", op.module)
        assertEquals(AuthRequirement.Required, op.auth)
        assertEquals(BodyEncoding.Multipart, op.bodyEncoding)
    }
}
