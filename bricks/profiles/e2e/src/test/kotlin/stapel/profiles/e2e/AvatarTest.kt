package stapel.profiles.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.profiles.avatar.CdnAvatarUploader
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.result.transportFailureOrNull
import stapel.wire.ApiTransport
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The avatar end to end: a real multipart upload to stapel-cdn, then one PATCH storing the reference
 * with its source. The server stores it; the answer does not decode, because stapel-profiles 0.23.1
 * serves the variant ladder with integer tiers while its contract says string (a backend defect,
 * reported upstream). The defect is pinned here: when the backend is fixed this test fails and is
 * flipped to expect a decoded avatar.
 */
class AvatarTest {
    @Test
    fun `A-1 the picture is uploaded to the CDN and stored on the profile with its source`(): Unit = runBlocking {
        Device("a1").use { d ->
            d.signIn()
            // The upload alone, through the app's authenticated transport.
            val uploader = CdnAvatarUploader(d.app.koin.get<ApiTransport>())
            val ref = (uploader.upload(png(), null) as StapelResult.Ok).value
            assertTrue(Regex("avatar/[0-9a-f]{64}").matches(ref.ref), ref.ref)
            assertEquals("cdn", ref.source)

            // Upload + PATCH as one operation.
            val result = d.profiles.me.setAvatar(png())
            val server = Stand.get("/profiles/api/v1/me", d.bearer())
            assertEquals("cdn", server["avatar_source"]!!.jsonPrimitive.content)
            assertEquals(ref.ref, server["avatar"]!!.jsonPrimitive.content, "the same picture hashes to the same reference")
            val tier = server["avatar_image"]!!.jsonObject["variants"]!!.jsonArray.first().jsonObject["tier"]!!.jsonPrimitive
            when (result) {
                is StapelResult.Ok -> fail("stapel-profiles now answers a decodable avatar (tier '${tier.content}', quoted=${tier.isString}): the backend defect is fixed — expect Ok here and assert the avatar image")
                is StapelResult.Err -> {
                    assertTrue(!tier.isString && tier.int > 0, "the defect: tier served as a number")
                    assertIs<TransportFailure.ContractViolation>(result.error.cause?.transportFailureOrNull(), "refused as a contract violation, not coerced: ${result.error}")
                }
            }
        }
    }
}
