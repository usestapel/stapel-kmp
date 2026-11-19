package stapel.showcase.gallery

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.tokens.StapelPresetTokens
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.StapelSkin
import stapel.ui.material3.Material3Skin

/** The skins the gallery switches between. */
enum class GallerySkin(val skin: StapelSkin) { Material3(Material3Skin), Foundation(FoundationSkin) }

/** Light, dark, or both side by side. */
enum class ModeChoice { Light, Dark, Both }

/**
 * The frame the story is drawn in: its width sets the size class; screens and flows get the
 * viewport [height] too (a screen fills its window), components take their own height.
 */
enum class SizeChoice(val width: Dp, val height: Dp) { Phone(390.dp, 844.dp), Tablet(768.dp, 1024.dp), Desktop(1280.dp, 800.dp) }

/** The text scales offered (percent). */
val TEXT_SCALES: List<Int> = listOf(100, 130, 150, 200)

/**
 * The global controls applied to every story canvas. Mirrored in links as query parameters
 * ([toQuery] / [fromQuery]), so a page with its controls is a link.
 */
data class GalleryControls(
    val skin: GallerySkin = GallerySkin.Material3,
    val preset: String = "default",
    val mode: ModeChoice = ModeChoice.Light,
    val locale: String = "en",
    val size: SizeChoice = SizeChoice.Phone,
    val textScale: Int = 100,
    val reduceMotion: Boolean = false,
    val rtl: Boolean = false,
) {
    init {
        require(textScale in 50..300) { "text scale $textScale % is out of range" }
    }

    /** The non-default controls as query parameters. */
    fun toQuery(): Map<String, String> {
        val d = GalleryControls()
        return buildMap {
            if (skin != d.skin) put("skin", skin.name)
            if (preset != d.preset) put("preset", preset)
            if (mode != d.mode) put("mode", mode.name)
            if (locale != d.locale) put("locale", locale)
            if (this@GalleryControls.size != d.size) put("size", this@GalleryControls.size.name)
            if (textScale != d.textScale) put("scale", textScale.toString())
            if (reduceMotion) put("motion", "reduced")
            if (rtl) put("dir", "rtl")
        }
    }

    companion object {
        /** The query keys [toQuery] writes. */
        val KEYS: Set<String> = setOf("skin", "preset", "mode", "locale", "size", "scale", "motion", "dir")

        /** Controls from query parameters; an unknown or illegal value keeps the default. */
        fun fromQuery(query: Map<String, String>): GalleryControls {
            val d = GalleryControls()
            return GalleryControls(
                skin = GallerySkin.entries.firstOrNull { it.name == query["skin"] } ?: d.skin,
                preset = query["preset"]?.takeIf { it in StapelPresetTokens.all } ?: d.preset,
                mode = ModeChoice.entries.firstOrNull { it.name == query["mode"] } ?: d.mode,
                locale = query["locale"]?.takeIf { Regex("[a-z]{2}(-[A-Z]{2})?").matches(it) } ?: d.locale,
                size = SizeChoice.entries.firstOrNull { it.name == query["size"] } ?: d.size,
                textScale = query["scale"]?.toIntOrNull()?.takeIf { it in 50..300 } ?: d.textScale,
                reduceMotion = query["motion"] == "reduced",
                rtl = query["dir"] == "rtl",
            )
        }
    }
}
