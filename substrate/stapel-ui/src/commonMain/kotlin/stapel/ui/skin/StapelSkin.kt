package stapel.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * The component slot table: one composable per slot. Bricks never call it directly — they use the
 * `Stapel*` wrappers of `stapel.ui`, which read `StapelTheme.skin`. A project replaces one slot by
 * interface delegation:
 *
 * ```kotlin
 * object AppSkin : StapelSkin by Material3Skin {
 *     @Composable override fun Button(spec: ButtonSpec, modifier: Modifier) { MyBrandButton(spec, modifier) }
 * }
 * ```
 *
 * The first twelve slots (Button … Snackbar) are abstract. The later slots (Toggle … PullToRefresh)
 * have a [FoundationSkin] body, so adding one never breaks an existing skin. Methods take no default
 * arguments on purpose; the wrappers carry the defaults.
 */
@Stable
public interface StapelSkin {
    /** A button ([ButtonSpec] lists its duties). */
    @Composable
    public fun Button(spec: ButtonSpec, modifier: Modifier)

    /** An icon-only button. */
    @Composable
    public fun IconButton(spec: IconButtonSpec, modifier: Modifier)

    /** A text field ([TextFieldSpec] lists its duties). */
    @Composable
    public fun TextField(spec: TextFieldSpec, modifier: Modifier)

    /** A fixed-length numeric code field. */
    @Composable
    public fun CodeField(spec: CodeFieldSpec, modifier: Modifier)

    /** A dialog (sheet or modal, as [DialogSpec.surface] says) around [content]. */
    @Composable
    public fun Dialog(spec: DialogSpec, content: @Composable () -> Unit)

    /** An inline message. */
    @Composable
    public fun Banner(spec: BannerSpec, modifier: Modifier)

    /** A progress indicator. */
    @Composable
    public fun Progress(spec: ProgressSpec, modifier: Modifier)

    /** A container around [content]. */
    @Composable
    public fun Surface(spec: SurfaceSpec, modifier: Modifier, content: @Composable () -> Unit)

    /** A list row. */
    @Composable
    public fun ListItem(spec: ListItemSpec, modifier: Modifier)

    /** A screen's top bar. */
    @Composable
    public fun TopBar(spec: TopBarSpec, modifier: Modifier)

    /** A divider line. */
    @Composable
    public fun Divider(modifier: Modifier)

    /** One transient message (the queue is `StapelSnackbarHost`). */
    @Composable
    public fun Snackbar(spec: SnackbarSpec, modifier: Modifier)

    /** A switch, checkbox or radio with its label. */
    @Composable
    public fun Toggle(spec: ToggleSpec, modifier: Modifier) {
        FoundationSkin.Toggle(spec, modifier)
    }

    /** Tabs or a segmented control. */
    @Composable
    public fun Tabs(spec: TabsSpec, modifier: Modifier) {
        FoundationSkin.Tabs(spec, modifier)
    }

    /** A chip. */
    @Composable
    public fun Chip(spec: ChipSpec, modifier: Modifier) {
        FoundationSkin.Chip(spec, modifier)
    }

    /** An avatar. */
    @Composable
    public fun Avatar(spec: AvatarSpec, modifier: Modifier) {
        FoundationSkin.Avatar(spec, modifier)
    }

    /** A badge on [content]. */
    @Composable
    public fun Badge(spec: BadgeSpec, modifier: Modifier, content: @Composable () -> Unit) {
        FoundationSkin.Badge(spec, modifier, content)
    }

    /** The phone primary navigation. */
    @Composable
    public fun NavigationBar(spec: NavigationSpec, modifier: Modifier) {
        FoundationSkin.NavigationBar(spec, modifier)
    }

    /** The tablet/desktop primary navigation. */
    @Composable
    public fun NavigationRail(spec: NavigationSpec, modifier: Modifier) {
        FoundationSkin.NavigationRail(spec, modifier)
    }

    /** Pull-to-refresh around [content]. */
    @Composable
    public fun PullToRefresh(spec: PullToRefreshSpec, modifier: Modifier, content: @Composable () -> Unit) {
        FoundationSkin.PullToRefresh(spec, modifier, content)
    }

    /** A brick-local widget: the skin's own value for [key], or the brick's default. */
    public fun <T : Any> extension(key: SkinExtensionKey<T>): T = key.default
}

/** The names of the twenty slots, in table order (tests and the gallery iterate them). */
public val StapelSkinSlots: List<String> = listOf(
    "Button", "IconButton", "TextField", "CodeField", "Dialog", "Banner", "Progress", "Surface", "ListItem", "TopBar",
    "Divider", "Snackbar", "Toggle", "Tabs", "Chip", "Avatar", "Badge", "NavigationBar", "NavigationRail", "PullToRefresh",
)
