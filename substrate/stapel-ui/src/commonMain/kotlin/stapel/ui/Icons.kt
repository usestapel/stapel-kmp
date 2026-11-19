package stapel.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A neutral icon id (kebab-case semantic name, the nav contract's vocabulary).
 *
 * @property id the id (`bell`, `arrow-back`).
 * @property autoMirror mirror in right-to-left layouts (directional glyphs).
 */
@Immutable
public class StapelIcon(public val id: String, public val autoMirror: Boolean = false) {
    /** Equal ids are equal icons. */
    override fun equals(other: Any?): Boolean = other is StapelIcon && other.id == id && other.autoMirror == autoMirror

    /** Hash of [id] and [autoMirror]. */
    override fun hashCode(): Int = id.hashCode() * 31 + autoMirror.hashCode()

    /** The id. */
    override fun toString(): String = id
}

/** Resolves icon ids to vectors. */
public fun interface StapelIconSet {
    /** The vector for [id], or null when this set does not draw it. */
    public fun resolve(id: String): ImageVector?
}

/** This set first, [fallback] for ids it does not draw: `MyIcons.orElse(StapelIcons)`. */
public fun StapelIconSet.orElse(fallback: StapelIconSet): StapelIconSet = StapelIconSet { resolve(it) ?: fallback.resolve(it) }

/** The ids the default set draws. */
public object StapelIconIds {
    /** Back navigation. */
    public val ArrowBack: StapelIcon = StapelIcon("arrow-back", autoMirror = true)

    /** Close / dismiss. */
    public val Close: StapelIcon = StapelIcon("close")

    /** Check mark. */
    public val Check: StapelIcon = StapelIcon("check")

    /** Disclosure chevron. */
    public val ChevronRight: StapelIcon = StapelIcon("chevron-right", autoMirror = true)

    /** Expand chevron. */
    public val ChevronDown: StapelIcon = StapelIcon("chevron-down")

    /** Show (password). */
    public val Eye: StapelIcon = StapelIcon("eye")

    /** Hide (password). */
    public val EyeOff: StapelIcon = StapelIcon("eye-off")

    /** Error status. */
    public val AlertCircle: StapelIcon = StapelIcon("alert-circle")

    /** Warning status. */
    public val AlertTriangle: StapelIcon = StapelIcon("alert-triangle")

    /** Information status. */
    public val Info: StapelIcon = StapelIcon("info")

    /** Success status. */
    public val CheckCircle: StapelIcon = StapelIcon("check-circle")

    /** More actions. */
    public val More: StapelIcon = StapelIcon("more")

    /** Reload. */
    public val Refresh: StapelIcon = StapelIcon("refresh")

    /** E-mail. */
    public val Mail: StapelIcon = StapelIcon("mail")

    /** Phone. */
    public val Phone: StapelIcon = StapelIcon("phone")

    /** Lock / password. */
    public val Lock: StapelIcon = StapelIcon("lock")

    /** Key / passkey. */
    public val Key: StapelIcon = StapelIcon("key")

    /** User / account. */
    public val User: StapelIcon = StapelIcon("user")

    /** Sign in. */
    public val LogIn: StapelIcon = StapelIcon("log-in", autoMirror = true)

    /** Sign out. */
    public val LogOut: StapelIcon = StapelIcon("log-out", autoMirror = true)

    /** Settings. */
    public val Settings: StapelIcon = StapelIcon("settings")

    /** Home. */
    public val Home: StapelIcon = StapelIcon("home")

    /** Notifications. */
    public val Bell: StapelIcon = StapelIcon("bell")

    /** Search. */
    public val Search: StapelIcon = StapelIcon("search")

    /** Add. */
    public val Plus: StapelIcon = StapelIcon("plus")

    /** Security. */
    public val ShieldCheck: StapelIcon = StapelIcon("shield-check")

    /** A device / session. */
    public val Device: StapelIcon = StapelIcon("device")

    /** The generic square drawn for an id no set knows. */
    public val Unknown: StapelIcon = StapelIcon("unknown")
}

// 24 x 24 stroke glyphs (stroke 1.75, round caps), drawn for this set.
private val PATHS: Map<String, String> = mapOf(
    "arrow-back" to "M19 12H5 M11 6l-6 6 6 6",
    "close" to "M6 6l12 12 M18 6L6 18",
    "check" to "M5 12.5l4.5 4.5L19 7.5",
    "chevron-right" to "M9 5l7 7-7 7",
    "chevron-down" to "M5 9l7 7 7-7",
    "eye" to "M2 12c2.5-4.5 6-7 10-7s7.5 2.5 10 7c-2.5 4.5-6 7-10 7s-7.5-2.5-10-7z M12 9a3 3 0 1 0 0.01 0",
    "eye-off" to "M3 3l18 18 M10.5 5.2A9.6 9.6 0 0 1 12 5c4 0 7.5 2.5 10 7a17 17 0 0 1-3 3.9 M6.6 6.6C4.7 7.8 3.2 9.6 2 12c2.5 4.5 6 7 10 7 1.8 0 3.5-.5 5-1.4",
    "alert-circle" to "M12 3a9 9 0 1 0 0.01 0 M12 7.5v5.5 M12 16.5v.5",
    "alert-triangle" to "M12 3.5L2.5 20h19z M12 9.5v5 M12 17.5v.5",
    "info" to "M12 3a9 9 0 1 0 0.01 0 M12 11v6 M12 7.5v.5",
    "check-circle" to "M12 3a9 9 0 1 0 0.01 0 M8 12.5l3 3 5-6",
    "more" to "M5 12h.01 M12 12h.01 M19 12h.01",
    "refresh" to "M20 12a8 8 0 1 1-2.3-5.6 M20 4v5h-5",
    "mail" to "M3 6h18v12H3z M3 7l9 6 9-6",
    "phone" to "M8 3h8v18H8z M11 18h2",
    "lock" to "M5 11h14v10H5z M8 11V8a4 4 0 0 1 8 0v3",
    "key" to "M8 15a4 4 0 1 0 0.01 0 M11 12l9-9 M17 6l3 3 M15 8l2 2",
    "user" to "M12 4a4 4 0 1 0 0.01 0 M4 21c1-4 4.5-6 8-6s7 2 8 6",
    "log-in" to "M14 4h5v16h-5 M3 12h11 M10 8l4 4-4 4",
    "log-out" to "M10 4H5v16h5 M9 12h12 M17 8l4 4-4 4",
    "settings" to "M12 9a3 3 0 1 0 0.01 0 M12 2v3 M12 19v3 M2 12h3 M19 12h3 M4.9 4.9l2.1 2.1 M17 17l2.1 2.1 M4.9 19.1L7 17 M17 7l2.1-2.1",
    "home" to "M3 11l9-8 9 8 M5 9.5V21h14V9.5 M10 21v-6h4v6",
    "bell" to "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z M10 21h4",
    "search" to "M11 4a7 7 0 1 0 0.01 0 M16 16l5 5",
    "plus" to "M12 5v14 M5 12h14",
    "shield-check" to "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z M8.5 12l2.5 2.5 4.5-5",
    "device" to "M3 5h18v11H3z M8 20h8 M12 16v4",
    "unknown" to "M5 5h14v14H5z",
)

private fun glyph(id: String, autoMirror: Boolean): ImageVector =
    ImageVector.Builder(name = id, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror)
        .addPath(
            pathData = addPathNodes(PATHS.getValue(id)),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

private val MIRRORED = setOf("arrow-back", "chevron-right", "log-in", "log-out")

/** The default icon set (the ids of [StapelIconIds]); tint comes from the caller. */
public val StapelIcons: StapelIconSet = object : StapelIconSet {
    private val cache = HashMap<String, ImageVector>()

    override fun resolve(id: String): ImageVector? =
        if (id !in PATHS) null else cache.getOrPut(id) { glyph(id, id in MIRRORED) }
}

/** The ids the default set draws (for coverage tests). */
public val StapelIconIdsDrawn: Set<String> get() = PATHS.keys
