package stapel.tokens

import kotlin.jvm.JvmInline
import kotlin.math.pow

/**
 * A colour as `0xAARRGGBB`, with no Compose dependency (`stapel-ui` turns it into a Compose `Color`).
 *
 * @property value the packed channels.
 */
@JvmInline
public value class Argb(public val value: UInt) {
    /** Alpha channel, 0..255. */
    public val alpha: Int get() = (value shr 24).toInt() and 0xFF

    /** Red channel, 0..255. */
    public val red: Int get() = (value shr 16).toInt() and 0xFF

    /** Green channel, 0..255. */
    public val green: Int get() = (value shr 8).toInt() and 0xFF

    /** Blue channel, 0..255. */
    public val blue: Int get() = value.toInt() and 0xFF

    /** `#rrggbb` when opaque, `#rrggbbaa` otherwise (the `tokens.resolved.json` form). */
    public fun toHex(): String {
        fun h(c: Int) = c.toString(16).padStart(2, '0')
        return "#" + h(red) + h(green) + h(blue) + if (alpha == 0xFF) "" else h(alpha)
    }

    /** WCAG relative luminance (0..1) of the colour channels; alpha is ignored. */
    public fun relativeLuminance(): Double {
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(red) + 0.7152 * ch(green) + 0.0722 * ch(blue)
    }

    /**
     * WCAG contrast ratio (1..21) against [other], both taken as opaque (the same arithmetic as the
     * token contrast gate).
     */
    public fun contrastRatio(other: Argb): Double {
        val a = relativeLuminance()
        val b = other.relativeLuminance()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** The [toHex] form. */
    override fun toString(): String = toHex()

    /** Parsing. */
    public companion object {
        /** Parses `#rgb`, `#rrggbb` or `#rrggbbaa`. */
        public fun fromHex(hex: String): Argb {
            require(hex.startsWith("#")) { "not a hex colour: $hex" }
            var h = hex.substring(1)
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            require(h.length == 6 || h.length == 8) { "not a hex colour: $hex" }
            val rgb = h.substring(0, 6).toUInt(16)
            val a = if (h.length == 8) h.substring(6).toUInt(16) else 0xFFu
            return Argb((a shl 24) or rgb)
        }
    }
}

/** Light or dark colour mode. */
public enum class ThemeMode {
    /** Light mode. */
    Light,

    /** Dark mode. */
    Dark,
}
