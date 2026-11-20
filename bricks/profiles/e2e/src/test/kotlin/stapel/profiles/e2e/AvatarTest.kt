package stapel.profiles.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.result.StapelResult
import stapel.wire.MediaPurpose
import stapel.wire.MediaUploader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The avatar end to end: a real multipart upload to stapel-cdn through the generated stapel-cdn client
 * (`cdnModule()`), with upload progress, then one PATCH storing the reference with its source; the
 * answer decodes with the avatar's variant ladder (stapel-profiles 0.23.3 serves string tiers).
 */
class AvatarTest {
    @Test
    fun `A-1 the picture is uploaded to the CDN with progress and stored on the profile with its source`(): Unit = runBlocking {
        Device("a1").use { d ->
            d.signIn()
            // The upload alone, through the app's media uploader (stapel-cdn's generated client).
            val file = png()
            val progress = mutableListOf<Pair<Long, Long?>>()
            val stored = (d.app.koin.get<MediaUploader>().upload(MediaPurpose.Avatar, file) { sent, total -> progress += sent to total } as StapelResult.Ok).value
            assertTrue(Regex("avatar/[0-9a-f]{64}").matches(stored.ref), stored.ref)
            assertEquals("cdn", stored.source)
            assertTrue(progress.isNotEmpty(), "the upload reported no progress")
            val (sent, total) = progress.last()
            assertTrue(sent >= file.size!!, "the whole body was reported sent: $sent of a ${file.size}-byte file")
            assertTrue(total == null || total == sent, "the last report covers the body: $sent of $total")

            // Upload + PATCH as one operation, through the profiles domain.
            var reported = 0
            val me = (d.profiles.me.setAvatar(png()) { _, _ -> reported++ } as StapelResult.Ok).value
            assertTrue(reported > 0, "setAvatar's onProgress fired on the real pipeline")
            val server = Stand.get("/profiles/api/v1/me", d.bearer())
            assertEquals("cdn", server["avatar_source"]!!.jsonPrimitive.content)
            assertEquals(stored.ref, server["avatar"]!!.jsonPrimitive.content, "the same picture hashes to the same reference")
            assertEquals(stored.ref, me.avatarRef?.ref)
            val tiers = server["avatar_image"]!!.jsonObject["variants"]!!.jsonArray.map { it.jsonObject["tier"]!!.jsonPrimitive }
            assertTrue(tiers.isNotEmpty() && tiers.all { it.isString }, "tiers are strings on the wire: $tiers")
            val avatar = me.avatar ?: error("the decoded profile carries no avatar image")
            assertEquals(tiers.map { it.content }, avatar.variants.map { it.tier })
        }
    }
}
