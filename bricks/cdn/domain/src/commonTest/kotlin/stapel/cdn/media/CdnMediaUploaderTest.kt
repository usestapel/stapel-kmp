package stapel.cdn.media

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import stapel.cdn.api.CdnApiClient
import stapel.cdn.api.CdnContractExamples
import stapel.cdn.api.CdnOperations
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
    private val file = FilePart("a.png", "image/png", 3) { Buffer().apply { write(byteArrayOf(1, 2, 3)) } }

    private fun uploaded(prefix: String): Any {
        val example = CdnContractExamples.all.single { it.label == "response 201 AvatarUploaded" }
        @Suppress("UNCHECKED_CAST")
        val serializer = CdnOperations.uploadAvatar.success.getValue(201)!! as kotlinx.serialization.KSerializer<Any>
        return WireJson.decodeFromString(serializer, example.json.replace("\"prefix\":\"avatar\"", "\"prefix\":\"$prefix\""))
    }

    @Test
    fun anAvatarGoesToTheAvatarUploadWithItsProgressAndComesBackAsTheCdnReference() = runTest {
        val wire = FakeApiTransport()
        wire.on("upload_avatar") { FakeAnswer.Value(uploaded("avatar/abc")) }
        val listener: (Long, Long?) -> Unit = { _, _ -> }
        val r = CdnMediaUploader(CdnApiClient(wire)).upload(MediaPurpose.Avatar, file, listener)
        assertEquals(StapelResult.Ok(StoredMedia("avatar/abc", "cdn")), r)
        val (id, call) = wire.calls.single()
        assertEquals("upload_avatar", id)
        assertSame(listener, call.onProgress, "the caller's progress listener reaches the transport")
        val part = assertIs<FormPart.File>(call.parts.single())
        assertEquals("file", part.name)
        assertSame(file, part.file)
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
