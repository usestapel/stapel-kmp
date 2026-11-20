package stapel.profiles.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import stapel.profiles.api.error.ProfilesApiError
import stapel.profiles.api.error.RequestContactVerificationError
import stapel.profiles.api.model.ProfileResponse
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.wire.AuthRequirement
import stapel.wire.CODEGEN_RUNTIME_ABI
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Proves the generated profiles client against its pinned contract (stapel-profiles 0.23.1, profile 2). */
class GeneratedContractTest {
    @Test
    fun generatedCodeTargetsThisRuntimeAndThePin() {
        assertEquals(CODEGEN_RUNTIME_ABI, ProfilesContract.RUNTIME_ABI)
        assertEquals("0.23.1", ProfilesContract.VERSION)
        assertEquals(2, ProfilesContract.PROFILE)
    }

    private fun normal(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.filter { it.value !is JsonNull }.sortedBy { it.key }.associate { it.key to normal(it.value) })
        is JsonArray -> JsonArray(e.map(::normal))
        else -> e
    }

    @Test
    fun everyContractExampleRoundTrips() {
        val failures = ProfilesContractExamples.all.mapNotNull { ex ->
            try {
                @Suppress("UNCHECKED_CAST")
                val s = ex.serializer as KSerializer<Any?>
                val back = WireJson.encodeToJsonElement(s, WireJson.decodeFromString(s, ex.json))
                if (normal(back) == normal(WireJson.parseToJsonElement(ex.json))) null else "${ex.label}: re-encodes as $back"
            } catch (e: SerializationException) {
                "${ex.label}: ${e.message?.lineSequence()?.first()}"
            }
        }
        assertTrue(ProfilesContractExamples.all.size >= 20, "examples: ${ProfilesContractExamples.all.size}")
        assertEquals(emptyList(), failures)
    }

    @Test
    fun securityFollowsTheContract() {
        assertEquals(AuthRequirement.Required, ProfilesOperations.getMyProfile.auth)
        assertEquals(AuthRequirement.Optional, ProfilesOperations.getProfile.auth)
        assertEquals(AuthRequirement.Optional, ProfilesOperations.listLanguages.auth)
        assertEquals("/profiles/api/v1/me", ProfilesOperations.updateMyProfile.path)
    }

    @Test
    fun boundCodesAreTypedAndTheRateLimitCarriesItsTypedWait() {
        val decoder = ProfilesOperations.requestContactVerification.errors
        val limited: RequestContactVerificationError = decoder.domain(
            ErrorEnvelope(ErrorCode("error.429.contacts_code_rate"), 429, ErrorParams(mapOf("retry_after" to "60"))),
        )
        assertIs<ProfilesApiError.ContactsCodeRate>(limited)
        val generic = assertIs<ProfilesApiError.RateLimit>(
            ProfilesApiError.decode(ErrorEnvelope(ErrorCode("error.429.rate_limit"), 429, ErrorParams(mapOf("retry_after" to "90")))),
        )
        assertEquals(90, generic.retryAfter)
    }

    @Test
    fun avatarVariantTiersAreStringsOnTheWireAsTheContractDeclares() {
        // stapel-profiles 0.23.1 declares VariantMetaDTO.tier a string ("320" / "original") and the
        // client holds it to that. Its /me serializer emits the avatar ladder with integer tiers (a
        // backend defect, reported upstream): such an answer is refused, not coerced.
        val withString = ME.replace("TIER", "\"120\"")
        assertEquals("120", WireJson.decodeFromString(ProfileResponse.serializer(), withString).avatarImage?.variants?.single()?.tier)
        assertFailsWith<SerializationException> { WireJson.decodeFromString(ProfileResponse.serializer(), ME.replace("TIER", "120")) }
    }

    private companion object {
        const val ME = """{"user_id":"u1","display_name":"Ada","theme":"system","avatar_source":"cdn","avatar":"avatar/x",
            "avatar_image":{"source":"cdn","url":"/avatar/x/a.png","mime":"image/png","width":128,"height":128,"aspect":1.0,"square":true,
            "preview_b64":null,"variants":[{"tier":TIER,"branch":null,"url":"/avatar/x/120.webp","width":120,"height":120}]},
            "location_id":null,"location_display_name_narrow":"","location_display_name_broad":"","app_language":null,"understands":[],
            "use_device_language":true,"auto_detected_language":"","auto_translate_content":false,"email_messages":true,"email_system":true,
            "push_messages":true,"push_system":true,"essential_cookies_accepted":false,"initial_setup_passed":true,"followers_count":0,
            "following_count":0,"created_at":"2026-01-02T03:04:05Z","updated_at":"2026-01-02T03:04:05Z"}"""
    }
}
