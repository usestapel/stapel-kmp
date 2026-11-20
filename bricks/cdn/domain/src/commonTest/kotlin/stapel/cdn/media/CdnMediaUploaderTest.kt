package stapel.cdn.media

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import stapel.cdn.api.CdnApiClient
import stapel.cdn.api.CdnContractExamples
import stapel.cdn.api.error.CdnApiError
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.StapelResult
import stapel.testing.FakeAnswer
import stapel.testing.FakeApiTransport
import stapel.wire.FilePart
import stapel.wire.FormPart
import stapel.wire.MediaPurpose
import stapel.wire.StoredMedia
import stapel.wire.WireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CdnMediaUploaderTest {
    private companion object {
        const val HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    }

    private val file = FilePart("a.png", "image/png", 3) { Buffer().apply { write(byteArrayOf(1, 2, 3)) } }

    /** The contract's own example answer of [operationId] (stapel-cdn 0.31.3 shows the real `<type>/<hash>` prefix). */
    private fun uploaded(operationId: String, label: String): Any {
        val example = CdnContractExamples.all.single { it.operationId == operationId && it.label == label }
        @Suppress("UNCHECKED_CAST")
        val serializer = example.serializer as kotlinx.serialization.KSerializer<Any>
        return WireJson.decodeFromString(serializer, example.json)
    }

    @Test
    fun anAvatarGoesToTheAvatarUploadWithItsProgressAndComesBackAsTheCdnReference() = runTest {
        val wire = FakeApiTransport()
        wire.on("upload_avatar") { FakeAnswer.Value(uploaded("upload_avatar", "response 201 AvatarUploaded")) }
        val listener: (Long, Long?) -> Unit = { _, _ -> }
        val r = CdnMediaUploader(CdnApiClient(wire)).upload(MediaPurpose.Avatar, file, listener)
        assertEquals(StapelResult.Ok(StoredMedia("avatar/$HASH", "cdn")), r)
        val (id, call) = wire.calls.single()
        assertEquals("upload_avatar", id)
        assertSame(listener, call.onProgress, "the caller's progress listener reaches the transport")
        val part = assertIs<FormPart.File>(call.parts.single())
        assertEquals("file", part.name)
        assertSame(file, part.file)
    }

    @Test
    fun anImageGoesToTheImageUploadAsABinaryPartWithTheConfiguredAssetType() = runTest {
        val wire = FakeApiTransport()
        wire.on("upload_image") { FakeAnswer.Value(uploaded("upload_image", "response 201 ImageUploaded")) }
        val listener: (Long, Long?) -> Unit = { _, _ -> }
        val r = CdnMediaUploader(CdnApiClient(wire), imageType = "product").upload(MediaPurpose.Image, file, listener)
        val stored = assertIs<StapelResult.Ok<StoredMedia>>(r).value
        assertTrue(Regex("[^/]+/[0-9a-f]{64}").matches(stored.ref), stored.ref)
        assertEquals("cdn", stored.source)
        val (id, call) = wire.calls.single()
        assertEquals("upload_image", id)
        assertSame(listener, call.onProgress)
        val part = assertIs<FormPart.File>(call.parts.single { it.name == "file" })
        assertSame(file, part.file, "the picture travels as a file part (stapel-cdn 0.31.3: binary), not as a string")
        assertEquals("product", assertIs<FormPart.Text>(call.parts.single { it.name == "type" }).value)
    }

    @Test
    fun anImageWithoutAConfiguredTypeLeavesTheChoiceToTheDeployment() = runTest {
        val wire = FakeApiTransport()
        wire.on("upload_image") { FakeAnswer.Value(uploaded("upload_image", "response 201 ImageUploaded")) }
        CdnMediaUploader(CdnApiClient(wire)).upload(MediaPurpose.Image, file, null)
        assertTrue(wire.calls.single().second.parts.none { it.name == "type" })
    }

    @Test
    fun aRefusedFileComesBackAsTheCdnError() = runTest {
        val wire = FakeApiTransport()
        wire.on("upload_avatar") { FakeAnswer.Refusal(ErrorEnvelope(ErrorCode("error.400.invalid_format"), 400, ErrorParams.Empty)) }
        val r = CdnMediaUploader(CdnApiClient(wire)).upload(MediaPurpose.Avatar, file, null)
        val error = assertIs<CdnApiError>((r as StapelResult.Err).error)
        assertEquals(400, error.status)
    }

    @Test
    fun aPurposeWithoutAnEndpointIsRefusedBeforeAnythingIsSent() = runTest {
        val wire = FakeApiTransport()
        assertFailsWith<IllegalArgumentException> { CdnMediaUploader(CdnApiClient(wire)).upload(MediaPurpose("banner"), file, null) }
        assertTrue(wire.calls.isEmpty())
    }
}
