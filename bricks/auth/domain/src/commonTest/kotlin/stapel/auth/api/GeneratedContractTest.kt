package stapel.auth.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import stapel.auth.api.error.AuthApiError
import stapel.auth.api.error.EmailVerifyError
import stapel.auth.api.model.AuthResponse
import stapel.auth.api.model.AuthType
import stapel.auth.api.model.FirstLoginChallengeResponse
import stapel.auth.api.model.LoginResponse
import stapel.auth.api.model.PatchedPasskeyRename
import stapel.auth.api.model.TOTPChallengeResponse
import stapel.auth.api.model.User
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.Remediation
import stapel.result.TransportFailure
import stapel.wire.CODEGEN_RUNTIME_ABI
import stapel.wire.CacheKey
import stapel.wire.Maybe
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Proves the generated auth client against its pinned contract (ARCH-CODEGEN §11). */
class GeneratedContractTest {
    private val json = WireJson

    @Test
    fun generatedCodeTargetsThisRuntime() {
        assertEquals(CODEGEN_RUNTIME_ABI, AuthContract.RUNTIME_ABI)
    }

    /** JSON with object keys sorted and explicit nulls dropped (an omitted optional and `null` are equal). */
    private fun normal(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.filter { it.value !is JsonNull }.sortedBy { it.key }.associate { it.key to normal(it.value) })
        is JsonArray -> JsonArray(e.map(::normal))
        else -> e
    }

    private fun roundTrip(serializer: KSerializer<*>, text: String): String? = try {
        @Suppress("UNCHECKED_CAST")
        val s = serializer as KSerializer<Any?>
        val value = json.decodeFromString(s, text)
        val back = json.encodeToJsonElement(s, value)
        val original = json.parseToJsonElement(text)
        if (normal(back) == normal(original)) null else "re-encodes as $back"
    } catch (e: Exception) {
        "does not decode: ${e.message?.lineSequence()?.first()}"
    }

    /**
     * Every named example of the contract decodes into the generated type and re-encodes to the same
     * JSON. The examples that fail are defects of the contract's own examples (an example narrower than
     * its schema); the set is pinned exactly, so a new failure and a fixed one both turn this red.
     */
    @Test
    fun everyContractExampleRoundTripsExceptTheKnownSourceDefects() {
        val failures = AuthContractExamples.all.mapNotNull { ex ->
            roundTrip(ex.serializer, ex.json)?.let { "${ex.operationId} ${ex.label}" to it }
        }.toMap()
        assertTrue(AuthContractExamples.all.size >= 50, "examples: ${AuthContractExamples.all.size}")
        assertEquals(KNOWN_EXAMPLE_DEFECTS, failures.keys, failures.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    @Test
    fun loginUnionSelectsByTheStatusWireValueNotTheDeclaredMapping() {
        val user = """{"id":"u1","username":"a","auth_type":"email","is_email_verified":true,"is_phone_verified":false,"is_anonymous":false,"is_staff":false,"is_superuser":false,"oauth_provider":null,"created_at":"2026-01-02T03:04:05.123456Z","last_login":null}"""
        val ok = json.decodeFromString(LoginResponse.serializer(), """{"status":"LOGGED_IN","user":$user,"tokens":{"access":"a","refresh":"r"}}""")
        assertIs<AuthResponse>(ok)
        assertEquals("LOGGED_IN", ok.status.wire)
        assertIs<TOTPChallengeResponse>(json.decodeFromString(LoginResponse.serializer(), """{"status":"TOTP_REQUIRED","challenge_token":"t","expires_in":300}"""))
        assertIs<FirstLoginChallengeResponse>(
            json.decodeFromString(LoginResponse.serializer(), """{"status":"FIRST_LOGIN_REQUIRED","requires":"password_change","challenge_token":"t","expires_in":600}"""),
        )
        val future = json.decodeFromString(LoginResponse.serializer(), """{"status":"PASSKEY_REQUIRED","x":1}""")
        assertIs<LoginResponse.Unknown>(future)
        assertEquals("""{"status":"PASSKEY_REQUIRED","x":1}""", json.encodeToString(LoginResponse.serializer(), future))
    }

    @Test
    fun unknownEnumValuesAndAddedKeysAreTolerated() {
        val user = json.decodeFromString(
            User.serializer(),
            """{"id":"u1","username":"a","auth_type":"carrier_pigeon","is_email_verified":true,"is_phone_verified":false,"is_anonymous":false,"is_staff":false,"is_superuser":false,"oauth_provider":null,"created_at":"2026-01-02T03:04:05+03:00","last_login":null,"added_later":{"x":1}}""",
        )
        assertEquals(AuthType.Unknown("carrier_pigeon"), user.authType)
        assertEquals("\"carrier_pigeon\"", json.encodeToString(AuthType.serializer(), user.authType))
        assertEquals(AuthType.Email, AuthType.of("email"))
    }

    @Test
    fun patchModelsSendOnlyWhatWasSet() {
        assertEquals("{}", json.encodeToString(PatchedPasskeyRename.serializer(), PatchedPasskeyRename()))
        assertEquals("""{"device_name":"Laptop"}""", json.encodeToString(PatchedPasskeyRename.serializer(), PatchedPasskeyRename(Maybe.Present("Laptop"))))
        assertEquals(100, PatchedPasskeyRename.DEVICE_NAME_MAX_LENGTH)
    }

    private fun envelope(code: String, status: Int, params: Map<String, String> = emptyMap()) =
        ErrorEnvelope(ErrorCode(code), status, ErrorParams(params))

    @Test
    fun errorEnvelopesDecodeToTypedCodesWithTheirRemediationAndParams() {
        val e = AuthApiError.decode(envelope("error.400.invalid_code_attempts", 400, mapOf("attempts_remaining" to "2")))
        assertIs<AuthApiError.InvalidCodeAttempts>(e)
        assertEquals("2", e.attemptsRemaining)
        assertEquals(Remediation.FixInput, e.remediation)
        val unknown = AuthApiError.decode(envelope("error.418.teapot", 418))
        assertIs<AuthApiError.Unknown>(unknown)
        assertEquals(418, unknown.status)
    }

    @Test
    fun boundOperationErrorsNarrowToTheirCodesPlusUnknownAndTransport() {
        val decoder = AuthOperations.emailVerify.errors
        val bound: EmailVerifyError = decoder.domain(envelope("error.409.email_taken", 409))
        assertIs<AuthApiError.EmailTaken>(bound)
        // A real code of the brick that the contract does not bind to this operation is not trusted as typed.
        val unbound: EmailVerifyError = decoder.domain(envelope("error.404.sso_org_not_found", 404))
        assertIs<AuthApiError.Unknown>(unbound)
        val transport: EmailVerifyError = decoder.transport(TransportFailure.Offline())
        assertIs<AuthApiError.Transport>(transport)
        assertEquals(Remediation.Retry, transport.remediation)
    }

    @Test
    fun keysAndInvalidationFollowTheCachePlan() {
        assertEquals(CacheKey.of("auth", "me"), AuthKeys.getMe())
        assertTrue(AuthKeys.all.matches(AuthKeys.listSessions()))
        val afterRevoke = AuthInvalidation.after("auth_api_v1_sessions_destroy_2")
        assertTrue(afterRevoke.any { it.matches(AuthKeys.listSessions()) })
        assertTrue(afterRevoke.any { it.matches(AuthKeys.getSecurityStatus()) })
        assertTrue(afterRevoke.none { it.matches(AuthKeys.getMe()) })
        assertTrue(AuthInvalidation.after("auth_api_v1_logout_create").single().matches(AuthKeys.getMe()))
        assertTrue(AuthInvalidation.after("auth_api_v1_qr_confirm_create").isEmpty())
        assertTrue(AuthInvalidation.after("not_an_operation").single().matches(AuthKeys.getMe()))
        assertEquals(true, AuthKeys.hints["auth_api_v1_token_refresh_create"]?.sensitive)
        assertTrue(AuthKeys.getSecurityAudit(page = 2) != AuthKeys.getSecurityAudit(page = 3))
    }

    @Test
    fun specsCarryTheContractsAuthRequirementCaptchaAndStepUp() {
        assertEquals(stapel.wire.AuthRequirement.Required, AuthOperations.getMe.auth)
        assertEquals(stapel.wire.AuthRequirement.Optional, AuthOperations.emailVerify.auth)
        assertEquals("verification.settings", AuthOperations.updateVerificationPreferences.verificationScope)
        assertEquals("X-Captcha-Token", AuthOperations.createDsar.captcha?.header)
        assertEquals("/auth/api/v1/me/", AuthOperations.getMe.path)
        assertTrue(json.parseToJsonElement(AuthContractExamples.all.first().json).jsonObject.isNotEmpty())
        assertIs<JsonPrimitive>(JsonPrimitive(AuthContract.VERSION))
    }

    private companion object {
        /**
         * Contract examples that do not match their own schema. The one left is not stapel-auth's own:
         * stapel-gdpr's data-owner health example, whose falsy `alive` field the backend's docstring reader
         * drops (fix at the source, then re-pin).
         */
        val KNOWN_EXAMPLE_DEFECTS: Set<String> = setOf(
            "auth_api_v1_owners_health_list response 200 DataOwnerHealthDTO",
        )
    }
}
