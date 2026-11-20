package stapel.image

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The web's vectors (stapel-react `packages/image/test/tiers.test.ts`), re-typed: both clients pick the same file. */
class TiersTest {
    private val tiers = listOf(560, 720, 1080)

    @Test
    fun pickTierIsTheSmallestTierWithinTenPercent() {
        assertEquals(720, pickTier(792.0, tiers))
        assertEquals(1080, pickTier(793.0, tiers))
        assertEquals(560, pickTier(616.0, tiers))
        assertEquals(1080, pickTier(4000.0, tiers), "above the ladder: the largest tier")
        assertEquals(560, pickTier(10.0, tiers), "below the ladder: the smallest tier")
        assertEquals(720, pickTier(792.0, listOf(1080, 560, 720)), "order does not matter")
        val ladder = listOf(160, 240, 480, 560, 720, 1080)
        assertEquals(160, pickTier(160.0, ladder))
        assertEquals(160, pickTier(176.0, ladder))
        assertEquals(240, pickTier(177.0, ladder))
        assertEquals(480, pickTier(500.0, ladder))
        assertEquals(560, pickTier(529.0, ladder))
        assertFailsWith<IllegalArgumentException> { pickTier(100.0, emptyList()) }
    }

    @Test
    fun limitingAxisFollowsTheTable() {
        assertEquals(Branch.W, limitingAxis(1.5, 1.5, Fit.Cover))
        assertEquals(Branch.W, limitingAxis(1.5, 1.5, Fit.Contain))
        assertEquals(Branch.H, limitingAxis(3.0, 4.0 / 3, Fit.Cover))
        assertEquals(Branch.W, limitingAxis(3.0, 4.0 / 3, Fit.Contain))
        assertEquals(Branch.W, limitingAxis(0.75, 16.0 / 9, Fit.Cover))
        assertEquals(Branch.H, limitingAxis(0.75, 16.0 / 9, Fit.Contain))
        assertEquals(Branch.W, limitingAxis(0.75, 1280.0 / 720, Fit.Cover))
        assertEquals(Branch.W, limitingAxis(0.75, 1.0, Fit.Cover))
        assertEquals(Branch.H, limitingAxis(1.6, 1.0, Fit.Cover))
    }

    private fun portrait(): StapelImage {
        val variants = mutableListOf(
            VariantMeta("32", null, "cdn://img/32.webp", 32, 43),
            VariantMeta("64", null, "cdn://img/64.webp", 64, 85),
            VariantMeta("120", null, "cdn://img/120.webp", 120, 160),
        )
        for (t in listOf(160, 240, 480, 560, 720, 1080)) {
            variants += VariantMeta("$t", Branch.W, "cdn://img/${t}w.webp", t, (t / 0.75).roundToInt())
            variants += VariantMeta("$t", Branch.H, "cdn://img/${t}h.webp", (t * 0.75).roundToInt(), t)
        }
        variants += VariantMeta(ORIGINAL_TIER, null, "cdn://img/original.webp", 3024, 4032)
        return StapelImage("cdn", "cdn://img/original.webp", "image/webp", 3024, 4032, 0.75, previewB64 = "data:image/webp;base64,UklGRi4A", variants = variants)
    }

    private fun pick(w: Float, h: Float, dpr: Float, fit: Fit, image: StapelImage = portrait(), aspect: Double = 0.75) =
        chooseVariant(w, h, dpr, aspect, fit, image)

    @Test
    fun chooseVariantMatchesTheWebVectors() {
        assertEquals(ORIGINAL_TIER, pick(1280f, 720f, 1f, Fit.Cover)?.tier)
        assertEquals("720" to Branch.W, pick(640f, 360f, 1f, Fit.Cover)?.let { it.tier to it.branch })
        assertEquals("1080" to Branch.W, pick(400f, 225f, 2f, Fit.Cover)?.let { it.tier to it.branch }, "density is part of the budget")
        assertEquals("480" to Branch.H, pick(640f, 360f, 1f, Fit.Contain)?.let { it.tier to it.branch })
        assertEquals(3024, pick(2000f, 1500f, 2f, Fit.Cover)?.width)
        assertEquals("64" to null, pick(64f, 64f, 1f, Fit.Cover)?.let { it.tier to it.branch })
    }

    @Test
    fun aSquareImageTakesAnyBranchAndAnEmptyLadderHasNoVariant() {
        val square = StapelImage(
            "cdn", "cdn://sq/original.webp", width = 800, height = 800, aspect = 1.0, square = true,
            variants = listOf(
                VariantMeta("560", Branch.W, "cdn://sq/560w.webp", 560, 560),
                VariantMeta("720", Branch.W, "cdn://sq/720w.webp", 720, 720),
                VariantMeta(ORIGINAL_TIER, null, "cdn://sq/original.webp", 800, 800),
            ),
        )
        assertEquals("560" to Branch.W, pick(300f, 600f, 1f, Fit.Cover, square, 1.0)?.let { it.tier to it.branch })
        val onlyOriginal = StapelImage("cdn", "cdn://o.webp", variants = listOf(VariantMeta(ORIGINAL_TIER, null, "cdn://o.webp", 100, 100)))
        assertEquals(ORIGINAL_TIER, pick(50f, 50f, 1f, Fit.Cover, onlyOriginal, 1.0)?.tier)
        assertNull(pick(10f, 10f, 1f, Fit.Cover, StapelImage.link("https://example.org/x.png"), 1.0))
    }
}
