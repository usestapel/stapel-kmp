package stapel.navigation

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Address of one screen instance. Implemented by `@Serializable` classes (usually `data class` or
 * `data object`) in UI libraries; the serializer is registered through the library's manifest.
 */
public interface Route

/**
 * Stable id of a destination, the web manifest id (`"auth.login"`).
 *
 * @property value the id.
 */
@Serializable
@JvmInline
public value class DestinationId(public val value: String) {
    /** The raw value. */
    override fun toString(): String = value
}

/**
 * Stable id of an embed (`"auth.account_menu"`).
 *
 * @property value the id.
 */
@JvmInline
public value class EmbedId(public val value: String) {
    /** The raw value. */
    override fun toString(): String = value
}

/**
 * Id of a chrome or page slot (`"topbar.end"`, `"page.actions"`).
 *
 * @property value the id.
 */
@JvmInline
public value class SlotId(public val value: String) {
    /** True for slots a page renders itself (`page.*`); false for chrome slots the shell renders. */
    public val isPageSlot: Boolean get() = value.startsWith("page.")

    /** The raw value. */
    override fun toString(): String = value
}

/**
 * Id of a presenter scope shared by several destinations (`"auth.sign_in"`).
 *
 * @property value the id.
 */
@JvmInline
public value class FlowId(public val value: String) {
    /** The raw value. */
    override fun toString(): String = value
}

/**
 * Key in the neutral icon vocabulary ([NavIcons]); never a UI-library icon name.
 *
 * @property key the key.
 */
@JvmInline
public value class NavIcon(public val key: String) {
    /** The raw key. */
    override fun toString(): String = key
}

/**
 * A platform capability a destination needs (`"push"`, `"camera"`).
 *
 * @property value the key.
 */
@JvmInline
public value class CapabilityKey(public val value: String) {
    /** The raw value. */
    override fun toString(): String = value
}

/** Who a destination is for: anyone, or a signed-in member with a mandate. */
public enum class Surface {
    /** Reachable without a membership. */
    Public,

    /** Only for members. */
    Member,
}

/** Who an embed is shown to. */
public enum class EmbedSurface {
    /** Only when not a member. */
    Public,

    /** Only for members. */
    Member,

    /** Always. */
    Any,
}

/** How a destination is presented. */
public enum class Presentation {
    /** A full screen in the stack. */
    Screen,

    /** A dialog above the current screen. */
    Dialog,

    /** A bottom sheet above the current screen. */
    Sheet,

    /** A full-screen cover above the tab host. */
    Cover,
}

/** Role of a destination in a list-detail layout. */
public enum class PaneRole {
    /** Not part of a list-detail pair. */
    Single,

    /** The list side. */
    List,

    /** The detail side. */
    Detail,
}

/** Window width class; thresholds are the token breakpoints, so web and mobile switch together. */
public enum class WidthClass {
    /** Compact. */
    Phone,

    /** Medium. */
    Tablet,

    /** Expanded. */
    Desktop,
}

/** Where a destination appears in the menu tree. */
public sealed interface Placement {
    /** A top-level destination (a tab, a rail item or a drawer item). */
    public data object Top : Placement

    /**
     * A child of [parentId]; dropped with a reason when no installed library declares the parent.
     *
     * @property parentId id of the parent destination, by string, so no dependency between libraries exists.
     */
    public data class Submenu(public val parentId: DestinationId) : Placement
}

/** The neutral icon vocabulary of the navigation contract (the same 20 keys the web registry maps). */
public object NavIcons {
    /** Sign-in. */
    public val Login: NavIcon = NavIcon("login")

    /** Security. */
    public val ShieldCheck: NavIcon = NavIcon("shield-check")

    /** A person. */
    public val User: NavIcon = NavIcon("user")

    /** Notifications. */
    public val Bell: NavIcon = NavIcon("bell")

    /** A grid of apps or categories. */
    public val Apps: NavIcon = NavIcon("apps")

    /** An audit trail. */
    public val Audit: NavIcon = NavIcon("audit")

    /** Time. */
    public val Clock: NavIcon = NavIcon("clock")

    /** An open folder. */
    public val FolderOpen: NavIcon = NavIcon("folder-open")

    /** A folder. */
    public val Folder: NavIcon = NavIcon("folder")

    /** Favourites. */
    public val Heart: NavIcon = NavIcon("heart")

    /** Home. */
    public val Home: NavIcon = NavIcon("home")

    /** Messages. */
    public val Message: NavIcon = NavIcon("message")

    /** An ordered list. */
    public val ListOrdered: NavIcon = NavIcon("list-ordered")

    /** Create. */
    public val Plus: NavIcon = NavIcon("plus")

    /** A profile card. */
    public val Profile: NavIcon = NavIcon("profile")

    /** A QR code. */
    public val Qr: NavIcon = NavIcon("qr")

    /** Search. */
    public val Search: NavIcon = NavIcon("search")

    /** Settings. */
    public val Settings: NavIcon = NavIcon("settings")

    /** A shopping cart. */
    public val Cart: NavIcon = NavIcon("cart")

    /** A tag. */
    public val Tag: NavIcon = NavIcon("tag")

    /** Every key of the vocabulary. */
    public val all: Set<NavIcon> = setOf(
        Login, ShieldCheck, User, Bell, Apps, Audit, Clock, FolderOpen, Folder, Heart,
        Home, Message, ListOrdered, Plus, Profile, Qr, Search, Settings, Cart, Tag,
    )
}
