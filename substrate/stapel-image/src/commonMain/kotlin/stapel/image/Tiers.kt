package stapel.image

/** How an image fills its slot. */
public enum class Fit {
    /** Fill the slot, cropping the overflow. */
    Cover,

    /** Show the whole image inside the slot. */
    Contain,
}

/**
 * The smallest tier that does not upscale beyond the ×1.1 tolerance: the smallest `T` in [tiers]
 * with `neededPx ≤ T × 1.1`; if none qualifies, the largest tier. [neededPx] already includes the
 * density (`dp × density`).
 *
 * @throws IllegalArgumentException when [tiers] is empty.
 */
public fun pickTier(neededPx: Double, tiers: Collection<Int>): Int {
    require(tiers.isNotEmpty()) { "pickTier: tiers must be non-empty" }
    val sorted = tiers.sorted()
    return sorted.firstOrNull { neededPx <= it * 1.1 } ?: sorted.last()
}

/**
 * Which side of the slot limits the pixel budget: `cover` scales by the larger ratio, so the side
 * where the image is narrower relative to the slot limits; `contain` is the opposite. Equal aspects
 * tie-break to [Branch.W].
 */
public fun limitingAxis(imgAspect: Double, slotAspect: Double, fit: Fit): Branch {
    if (imgAspect == slotAspect) return Branch.W
    val imageWider = imgAspect > slotAspect
    return when (fit) {
        Fit.Cover -> if (imageWider) Branch.H else Branch.W
        Fit.Contain -> if (imageWider) Branch.W else Branch.H
    }
}

/**
 * The variant for a slot of [slotWidthDp] × [slotHeightDp] at [density]: the limiting axis from the
 * image aspect, the slot aspect and [fit]; the needed pixels along it; then the smallest
 * non-upscaling tier among the variants that serve that axis (the matching branch, a min-side
 * variant, or any branch of a [StapelImage.square] image). Past the top of the ladder the
 * `"original"` variant when there is one. Null when [image] has no usable variant (no ladder: load
 * [StapelImage.url]).
 */
public fun chooseVariant(
    slotWidthDp: Float,
    slotHeightDp: Float,
    density: Float,
    imgAspect: Double,
    fit: Fit,
    image: StapelImage,
): VariantMeta? {
    val slotAspect = slotWidthDp.toDouble() / slotHeightDp.toDouble()
    val axis = limitingAxis(imgAspect, slotAspect, fit)
    val neededPx = (if (axis == Branch.W) slotWidthDp else slotHeightDp).toDouble() * density
    val candidates = image.variants.filter { it.numericTier != null && (image.square || it.branch == null || it.branch == axis) }
    val original = image.variants.firstOrNull { it.tier == ORIGINAL_TIER }
    if (candidates.isEmpty()) return original
    val tiers = candidates.mapNotNull { it.numericTier }.toSet()
    if (neededPx > tiers.max() * 1.1 && original != null) return original
    val tier = pickTier(neededPx, tiers)
    return candidates.firstOrNull { it.numericTier == tier && it.branch == axis }
        ?: candidates.firstOrNull { it.numericTier == tier && it.branch == null }
        ?: candidates.first { it.numericTier == tier }
}

/** The rank of a variant for the upgrade-only rule: its numeric tier, `"original"` above every tier. */
public val VariantMeta.rank: Int get() = numericTier ?: Int.MAX_VALUE
