package stapel.notifications.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import stapel.notifications.api.model.DeviceTokenRequest
import stapel.notifications.api.model.DeviceUnregisterRequest
import stapel.wire.CODEGEN_RUNTIME_ABI
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the generated notifications client against its pinned contract (stapel-notifications 0.31.1, profile 2). */
class GeneratedContractTest {
    @Test
    fun generatedCodeTargetsThisRuntimeAndThePin() {
        assertEquals(CODEGEN_RUNTIME_ABI, NotificationsContract.RUNTIME_ABI)
        assertEquals("0.31.1", NotificationsContract.VERSION)
        assertEquals(2, NotificationsContract.PROFILE)
    }

    private fun normal(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.filter { it.value !is JsonNull }.sortedBy { it.key }.associate { it.key to normal(it.value) })
        is JsonArray -> JsonArray(e.map(::normal))
        else -> e
    }

    @Test
    fun everyContractExampleRoundTrips() {
        val failures = NotificationsContractExamples.all.mapNotNull { ex ->
            try {
                @Suppress("UNCHECKED_CAST")
                val s = ex.serializer as KSerializer<Any?>
                val back = WireJson.encodeToJsonElement(s, WireJson.decodeFromString(s, ex.json))
                if (normal(back) == normal(WireJson.parseToJsonElement(ex.json))) null else "${ex.label}: re-encodes as $back"
            } catch (e: SerializationException) {
                "${ex.label}: ${e.message?.lineSequence()?.first()}"
            }
        }
        assertEquals(4, NotificationsContractExamples.all.size)
        assertEquals(emptyList(), failures)
    }

    @Test
    fun theDeviceCallsCarryTheInstallationIdOnTheWire() {
        val register = WireJson.encodeToJsonElement(DeviceTokenRequest.serializer(), DeviceTokenRequest("t", "ios", "install-1")) as JsonObject
        assertEquals(setOf("token", "platform", "device_id"), register.filterValues { it !is JsonNull }.keys)
        val unregister = WireJson.encodeToJsonElement(DeviceUnregisterRequest.serializer(), DeviceUnregisterRequest(deviceId = "install-1")) as JsonObject
        assertEquals(setOf("device_id"), unregister.filterValues { it !is JsonNull }.keys)
    }
}
