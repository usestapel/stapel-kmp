package stapel.tokens

/**
 * The §68 neutral colour-role dictionary: 33 roles, each a light/dark pair in [ColorTokens].
 *
 * @property key the canon name (`surface-raised`), as in `stapel.theme.json` and on the web.
 */
public enum class ColorRole(public val key: String) {
    /** Page background. */
    Surface("surface"),
    /** Cards, list rows, bars raised above the page. */
    SurfaceRaised("surface-raised"),
    /** Filled fields, wells, skeletons. */
    SurfaceSunken("surface-sunken"),
    /** Dialogs, sheets, menus. */
    SurfaceOverlay("surface-overlay"),
    /** Body text. */
    Text("text"),
    /** Secondary text. */
    TextMuted("text-muted"),
    /** Tertiary text: placeholders, captions, disabled labels. */
    TextSubtle("text-subtle"),
    /** Text on a solid `brand` fill. */
    TextOnAccent("text-on-accent"),
    /** Outlines (fields, outlined buttons). */
    Border("border"),
    /** Dividers. */
    BorderSubtle("border-subtle"),
    /** Focus indicator. */
    FocusRing("focus-ring"),
    /** The accent: primary actions. */
    Brand("brand"),
    /** Accent under a hovering pointer. */
    BrandHover("brand-hover"),
    /** Accent while pressed. */
    BrandActive("brand-active"),
    /** Accent tint: selected rows, indicators. */
    BrandSubtle("brand-subtle"),
    /** Inline links. */
    Link("link"),
    /** Hovered or pressed links. */
    LinkHover("link-hover"),
    /** `success` status: text and icons. */
    Success("success"),
    /** `success` status: tinted background. */
    SuccessBg("success-bg"),
    /** `success` status: outline. */
    SuccessBorder("success-border"),
    /** `success` status: label on the solid `success` fill. */
    SuccessOn("success-on"),
    /** `warning` status: text and icons. */
    Warning("warning"),
    /** `warning` status: tinted background. */
    WarningBg("warning-bg"),
    /** `warning` status: outline. */
    WarningBorder("warning-border"),
    /** `warning` status: label on the solid `warning` fill. */
    WarningOn("warning-on"),
    /** `error` status: text and icons. */
    Error("error"),
    /** `error` status: tinted background. */
    ErrorBg("error-bg"),
    /** `error` status: outline. */
    ErrorBorder("error-border"),
    /** `error` status: label on the solid `error` fill. */
    ErrorOn("error-on"),
    /** `info` status: text and icons. */
    Info("info"),
    /** `info` status: tinted background. */
    InfoBg("info-bg"),
    /** `info` status: outline. */
    InfoBorder("info-border"),
    /** `info` status: label on the solid `info` fill. */
    InfoOn("info-on"),
    ;

    /** Lookup by canon name. */
    public companion object {
        private val byKey = entries.associateBy { it.key }

        /** The role named [key], or null for a host's extra role. */
        public fun fromKey(key: String): ColorRole? = byKey[key]
    }
}
