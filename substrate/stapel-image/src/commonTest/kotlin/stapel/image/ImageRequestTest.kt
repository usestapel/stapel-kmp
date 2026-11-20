package stapel.image

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageRequestTest {
    private val ladder = StapelImage(
        "cdn", "https://media.example/o.webp", aspect = 1.0, square = true,
        previewB64 = "data:image/png;base64,iVBORw0KGgo=",
        variants = listOf(
            VariantMeta("64", null, "https://media.example/64.webp"),
            VariantMeta("160", Branch.W, "https://media.example/160.webp"),
            VariantMeta("480", Branch.W, "https://media.example/480.webp"),
        ),
    )

    @Test
    fun theWireShapeDecodesWithSnakeCaseFieldsAndUnknownKeys() {
        val json = Json { ignoreUnknownKeys = true }
        val image = json.decodeFromString(
            StapelImage.serializer(),
            """{"source":"cdn","url":"u","mime":null,"width":4,"height":2,"aspect":2.0,"square":false,
               "preview_b64":null,"variants":[{"tier":"original","branch":null,"url":"u","width":4,"height":2}],
               "kind":"video","preview_kind":"poster","duration_ms":0,"poster_url":"p","meta_status":"ok","meta_reason":null,"future":1}""",
        )
        assertEquals(PreviewKind.Poster, image.previewKind)
        assertEquals(0L, image.durationMs)
        assertEquals("p", image.posterUrl)
        assertEquals(null, image.variants.single().numericTier)
    }

    @Test
    fun aSlotPicksItsVariantAndCarriesThePreview() {
        val r = ladder.requestFor(40f, 40f, 3f, Fit.Cover)!!
        assertEquals("https://media.example/160.webp", r.url, "40 dp × 3 = 120 px → the 160 tier")
        assertEquals(120, r.widthPx)
        val preview = assertIs<ImagePlaceholder.Preview>(r.placeholder)
        assertEquals("image/png", preview.mime)
        assertEquals(PreviewKind.Blur, preview.kind)
    }

    @Test
    fun aZeroSidedSlotLoadsNothing() {
        assertNull(ladder.requestFor(0f, 40f, 2f, Fit.Cover))
        assertNull(ladder.requestFor(40f, 0f, 2f, Fit.Cover))
    }

    @Test
    fun aMediumThatIsNotAPictureLoadsItsPosterNeverItself() {
        val video = StapelImage("cdn", "https://media.example/v.mp4", kind = "video", posterUrl = "https://media.example/v.jpg")
        assertEquals("https://media.example/v.jpg", video.requestFor(100f, 100f, 1f, Fit.Cover)?.url)
        assertNull(video.copy(posterUrl = null).requestFor(100f, 100f, 1f, Fit.Cover), "no poster: nothing to load")
        val audio = StapelImage("cdn", "https://media.example/a.mp3", kind = "audio", previewKind = PreviewKind.Waveform)
        assertEquals(4.0, audio.reservedAspect)
        assertTrue(StapelImage("cdn", "x", kind = "gif").isPicture)
    }

    @Test
    fun onlyBase64ImageDataUrisArePreviews() {
        assertIs<ImagePlaceholder.Preview>(decodePreview("data:image/jpeg;base64,/9j/4AAQ"))
        assertNull(decodePreview("https://evil.example/x.png"))
        assertNull(decodePreview("data:text/html;base64,PGgxPg=="))
        assertNull(decodePreview("data:image/png,rawbytes"))
        assertNull(decodePreview("data:image/png;base64,***"))
        assertNull(decodePreview("data:image/svg+xml;charset=utf-8;base64"))
        assertEquals(ImagePlaceholder.None, ladder.copy(previewB64 = "javascript:alert(1)").placeholder())
    }

    @Test
    fun theOnScreenVariantIsOnlyEverUpgraded() {
        val big = ladder.requestFor(150f, 150f, 3f, Fit.Cover)!!
        val small = ladder.requestFor(20f, 20f, 3f, Fit.Cover)!!
        assertEquals("https://media.example/480.webp", big.url)
        assertSame(big, upgradeOnly(big, small), "a shrinking slot keeps the sharper file")
        assertSame(big, upgradeOnly(small, big))
        assertSame(small, upgradeOnly(null, small))
    }
}
