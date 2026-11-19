package stapel.tokens

/**
 * One mode's colours: the 33 canon roles as properties, a host's extra roles in [extra].
 */
public class ColorTokens(
    /** Page background. */
    public val surface: Argb,
    /** Cards, list rows, bars raised above the page. */
    public val surfaceRaised: Argb,
    /** Filled fields, wells, skeletons. */
    public val surfaceSunken: Argb,
    /** Dialogs, sheets, menus. */
    public val surfaceOverlay: Argb,
    /** Body text. */
    public val text: Argb,
    /** Secondary text. */
    public val textMuted: Argb,
    /** Tertiary text: placeholders, captions, disabled labels. */
    public val textSubtle: Argb,
    /** Text on a solid `brand` fill. */
    public val textOnAccent: Argb,
    /** Outlines (fields, outlined buttons). */
    public val border: Argb,
    /** Dividers. */
    public val borderSubtle: Argb,
    /** Focus indicator. */
    public val focusRing: Argb,
    /** The accent: primary actions. */
    public val brand: Argb,
    /** Accent under a hovering pointer. */
    public val brandHover: Argb,
    /** Accent while pressed. */
    public val brandActive: Argb,
    /** Accent tint: selected rows, indicators. */
    public val brandSubtle: Argb,
    /** Inline links. */
    public val link: Argb,
    /** Hovered or pressed links. */
    public val linkHover: Argb,
    /** `success` status: text and icons. */
    public val success: Argb,
    /** `success` status: tinted background. */
    public val successBg: Argb,
    /** `success` status: outline. */
    public val successBorder: Argb,
    /** `success` status: label on the solid `success` fill. */
    public val successOn: Argb,
    /** `warning` status: text and icons. */
    public val warning: Argb,
    /** `warning` status: tinted background. */
    public val warningBg: Argb,
    /** `warning` status: outline. */
    public val warningBorder: Argb,
    /** `warning` status: label on the solid `warning` fill. */
    public val warningOn: Argb,
    /** `error` status: text and icons. */
    public val error: Argb,
    /** `error` status: tinted background. */
    public val errorBg: Argb,
    /** `error` status: outline. */
    public val errorBorder: Argb,
    /** `error` status: label on the solid `error` fill. */
    public val errorOn: Argb,
    /** `info` status: text and icons. */
    public val info: Argb,
    /** `info` status: tinted background. */
    public val infoBg: Argb,
    /** `info` status: outline. */
    public val infoBorder: Argb,
    /** `info` status: label on the solid `info` fill. */
    public val infoOn: Argb,
    /** Roles a host theme adds beyond the dictionary (custom roles, custom status families), by canon key. */
    public val extra: Map<String, Argb> = emptyMap(),
) {
    /** The colour of [role]. */
    public operator fun get(role: ColorRole): Argb = when (role) {
        ColorRole.Surface -> surface
        ColorRole.SurfaceRaised -> surfaceRaised
        ColorRole.SurfaceSunken -> surfaceSunken
        ColorRole.SurfaceOverlay -> surfaceOverlay
        ColorRole.Text -> text
        ColorRole.TextMuted -> textMuted
        ColorRole.TextSubtle -> textSubtle
        ColorRole.TextOnAccent -> textOnAccent
        ColorRole.Border -> border
        ColorRole.BorderSubtle -> borderSubtle
        ColorRole.FocusRing -> focusRing
        ColorRole.Brand -> brand
        ColorRole.BrandHover -> brandHover
        ColorRole.BrandActive -> brandActive
        ColorRole.BrandSubtle -> brandSubtle
        ColorRole.Link -> link
        ColorRole.LinkHover -> linkHover
        ColorRole.Success -> success
        ColorRole.SuccessBg -> successBg
        ColorRole.SuccessBorder -> successBorder
        ColorRole.SuccessOn -> successOn
        ColorRole.Warning -> warning
        ColorRole.WarningBg -> warningBg
        ColorRole.WarningBorder -> warningBorder
        ColorRole.WarningOn -> warningOn
        ColorRole.Error -> error
        ColorRole.ErrorBg -> errorBg
        ColorRole.ErrorBorder -> errorBorder
        ColorRole.ErrorOn -> errorOn
        ColorRole.Info -> info
        ColorRole.InfoBg -> infoBg
        ColorRole.InfoBorder -> infoBorder
        ColorRole.InfoOn -> infoOn
    }

    /** A canon role or an extra role by its key (`surface-raised`, `promo-bg`); null when unknown. */
    public fun getOrNull(key: String): Argb? = ColorRole.fromKey(key)?.let { get(it) } ?: extra[key]

    /**
     * The four roles of a status family: `<family>`, `<family>-bg`, `<family>-border`, `<family>-on`.
     * Works for the canon families (`success`, `warning`, `error`, `info`) and a host family whose four
     * roles are all present; null otherwise.
     */
    public fun status(family: String): StatusColors? {
        val base = getOrNull(family) ?: return null
        val bg = getOrNull("$family-bg") ?: return null
        val border = getOrNull("$family-border") ?: return null
        val on = getOrNull("$family-on") ?: return null
        return StatusColors(base, bg, border, on)
    }
}

/**
 * A status family's four colours.
 *
 * @property base text and icons in the status colour.
 * @property bg tinted background.
 * @property border outline.
 * @property on label on a solid [base] fill.
 */
public class StatusColors(public val base: Argb, public val bg: Argb, public val border: Argb, public val on: Argb)
