package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import stapel.ui.generated.resources.Res
import stapel.ui.generated.resources.allDrawableResources

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

/** Resolves icon ids to vectors (composable: a set may load its glyphs from resources). */
public fun interface StapelIconSet {
    /** The vector for [id], or null when this set does not draw it. */
    @Composable
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

// 24 x 24 stroke glyphs (stroke 1.75, round caps), drawn for this set. They are vector drawables in
// `composeResources/drawable/stapel_icon_<id with underscores>.xml` (Compose Multiplatform resources,
// packaged with the library for every target).
private val DRAWN: List<String> = listOf(
    "arrow-back",
    "close",
    "check",
    "chevron-right",
    "chevron-down",
    "eye",
    "eye-off",
    "alert-circle",
    "alert-triangle",
    "info",
    "check-circle",
    "more",
    "refresh",
    "mail",
    "phone",
    "lock",
    "key",
    "user",
    "log-in",
    "log-out",
    "settings",
    "home",
    "bell",
    "search",
    "plus",
    "shield-check",
    "device",
    "unknown",
)

/** The default icon set (the ids of [StapelIconIds]); tint comes from the caller. */
public val StapelIcons: StapelIconSet = object : StapelIconSet {
    @Composable
    override fun resolve(id: String): ImageVector? {
        if (id !in DRAWN) return null
        val resource = Res.allDrawableResources["stapel_icon_" + id.replace('-', '_')] ?: return null
        return vectorResource(resource)
    }
}

/** The ids the default set draws (for coverage tests). */
public val StapelIconIdsDrawn: Set<String> get() = DRAWN.toSet()
