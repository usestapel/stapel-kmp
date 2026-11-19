package stapel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import stapel.i18n.CoreUiStrings
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.PagerIndicatorSpec
import stapel.ui.skin.SelectSpec
import stapel.ui.skin.SkeletonShape
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeEmphasis
import stapel.ui.skin.BadgePlacement
import stapel.ui.skin.BadgeSize
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.BannerSpec
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemMedia
import stapel.ui.skin.ListItemValue
import stapel.ui.skin.NavigationItemSpec
import stapel.ui.skin.NavigationSpec
import stapel.ui.skin.PullToRefreshSpec
import stapel.ui.skin.ToggleKind
import stapel.ui.skin.ToggleSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.CodeFieldSpec
import stapel.ui.skin.ControlSize
import stapel.ui.skin.DialogSpec
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.IconButtonSpec
import stapel.ui.skin.ListItemSpec
import stapel.ui.skin.ProgressKind
import stapel.ui.skin.ProgressSpec
import stapel.ui.skin.SurfaceSpec
import stapel.ui.skin.SurfaceTone
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.TabsSpec
import stapel.ui.skin.TabsVariant
import stapel.ui.skin.TopBarSpec

/** A button through the skin ([ButtonSpec] lists the duties). */
@Composable
public fun StapelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ControlSize = ControlSize.Regular,
    enabled: Boolean = true,
    loading: Boolean = false,
    gatedReason: String? = null,
    leadingIcon: StapelIcon? = null,
    fillWidth: Boolean = false,
    testTag: String? = null,
) {
    StapelTheme.skin.Button(ButtonSpec(label, onClick, variant, size, enabled, loading, gatedReason, leadingIcon, fillWidth, testTag), modifier)
}

/** An icon-only button through the skin; [contentDescription] is its accessible name. */
@Composable
public fun StapelIconButton(
    icon: StapelIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: StapelTone = StapelTone.Neutral,
    testTag: String? = null,
) {
    StapelTheme.skin.IconButton(IconButtonSpec(icon, contentDescription, onClick, enabled, tone, testTag), modifier)
}

/** A text field through the skin ([TextFieldSpec] lists the duties). */
@Composable
public fun StapelTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    kind: TextFieldKind = TextFieldKind.Text,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: StapelIcon? = null,
    suffix: String? = null,
    trailingAction: IconButtonSpec? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    autofill: ContentType? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    testTag: String? = null,
    loading: Boolean = false,
    clearLabel: String? = if (kind == TextFieldKind.Search) stapelString(CoreUiStrings.Clear) else null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
) {
    StapelTheme.skin.TextField(
        TextFieldSpec(
            state, label, kind, placeholder, supportingText, error, enabled, readOnly, leadingIcon, suffix, trailingAction,
            imeAction, onImeAction, autofill, minLines, maxLines, testTag, loading, clearLabel, inputTransformation, outputTransformation,
        ),
        modifier,
    )
}

/** A fixed-length numeric code field through the skin. */
@Composable
public fun StapelCodeField(
    state: TextFieldState,
    length: Int,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    onComplete: ((String) -> Unit)? = null,
    testTag: String? = null,
    inputTransformation: InputTransformation? = null,
) {
    StapelTheme.skin.CodeField(CodeFieldSpec(state, length, label, error, enabled, onComplete, testTag, inputTransformation), modifier)
}

/**
 * A dialog through the skin; the surface (sheet on a phone, modal otherwise) comes from the layout.
 *
 * @param dismissLabel the accessible label of the dismiss affordance (i18n copy).
 */
@Composable
public fun StapelDialog(
    onDismiss: () -> Unit,
    dismissLabel: String = stapelString(CoreUiStrings.Close),
    title: String? = null,
    accessibleName: String? = null,
    dismissible: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    StapelTheme.skin.Dialog(
        DialogSpec(onDismiss, StapelTheme.layout.dialogSurface, title, accessibleName, dismissLabel, dismissible, footer, testTag),
        content,
    )
}

/** An inline message through the skin. */
@Composable
public fun StapelBanner(
    tone: StapelTone,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    detail: String? = null,
    action: ButtonSpec? = null,
    onDismiss: (() -> Unit)? = null,
    testTag: String? = null,
) {
    StapelTheme.skin.Banner(BannerSpec(tone, message, title, detail, action, onDismiss, null, testTag), modifier)
}

/** A container through the skin. */
@Composable
public fun StapelSurface(
    modifier: Modifier = Modifier,
    tone: SurfaceTone = SurfaceTone.Raised,
    elevation: ElevationStep = ElevationStep.None,
    bordered: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    StapelTheme.skin.Surface(SurfaceSpec(tone, elevation, bordered, onClick, testTag), modifier, content)
}

/** A list row through the skin ([ListItemSpec] lists the duties). */
@Composable
public fun StapelListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
    media: ListItemMedia? = null,
    value: ListItemValue? = null,
    accessory: ListItemAccessory? = null,
    headlineMaxLines: Int = Int.MAX_VALUE,
    supportingMaxLines: Int = Int.MAX_VALUE,
) {
    StapelTheme.skin.ListItem(
        ListItemSpec(
            headline, supporting, overline, leading, trailing, onClick, selected, enabled, testTag, media, value, accessory,
            headlineMaxLines, supportingMaxLines,
        ),
        modifier,
    )
}

/** A top bar through the skin ([TopBarSpec] lists the duties; `StapelPage` drives [collapsedFraction]). */
@Composable
public fun StapelTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: IconButtonSpec? = null,
    actions: List<IconButtonSpec> = emptyList(),
    testTag: String? = null,
    subtitle: String? = null,
    collapsedFraction: Float? = null,
) {
    StapelTheme.skin.TopBar(TopBarSpec(title, navigation, actions, testTag, subtitle, collapsedFraction), modifier)
}

/** Tabs or a segmented control through the skin ([TabsSpec] lists the duties). */
@Composable
public fun StapelTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: TabsVariant = TabsVariant.Tabs,
    testTag: String? = null,
    icons: List<StapelIcon>? = null,
    iconOnly: Boolean = false,
    scrollable: Boolean = false,
) {
    StapelTheme.skin.Tabs(TabsSpec(items, selectedIndex, onSelect, variant, testTag, icons, iconOnly, scrollable), modifier)
}

/** A switch, checkbox or radio with its label through the skin ([ToggleSpec] lists the duties). */
@Composable
public fun StapelToggle(
    kind: ToggleKind,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    indeterminate: Boolean = false,
    error: String? = null,
    controlAtEnd: Boolean = kind == ToggleKind.Switch,
    testTag: String? = null,
) {
    StapelTheme.skin.Toggle(ToggleSpec(kind, checked, onCheckedChange, label, enabled, testTag, supporting, indeterminate, error, controlAtEnd), modifier)
}

/** A chip through the skin ([ChipSpec] lists the duties). */
@Composable
public fun StapelChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: ChipKind = ChipKind.Choice,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    tone: StapelTone = StapelTone.Neutral,
    leadingIcon: StapelIcon? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = if (onRemove != null) stapelString(CoreUiStrings.Remove) else null,
    enabled: Boolean = true,
    size: ControlSize = ControlSize.Regular,
    testTag: String? = null,
) {
    StapelTheme.skin.Chip(ChipSpec(label, kind, selected, onClick, tone, testTag, leadingIcon, onRemove, removeLabel, enabled, size), modifier)
}

/** An avatar through the skin ([AvatarSpec] lists the content ladder and duties). */
@Composable
public fun StapelAvatar(
    contentDescription: String,
    modifier: Modifier = Modifier,
    initials: String? = avatarInitials(contentDescription),
    size: AvatarSize = AvatarSize.Medium,
    icon: StapelIcon? = null,
    image: String? = null,
    badge: BadgeSpec? = null,
    badgePlacement: BadgePlacement = BadgePlacement.BottomEnd,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    StapelTheme.skin.Avatar(AvatarSpec(initials, contentDescription, size, icon, image, badge, badgePlacement, onClick, testTag), modifier)
}

/**
 * A badge through the skin ([BadgeSpec] lists the duties): on [content] when given, standalone
 * (a count in a row, a dot in a header) otherwise.
 */
@Composable
public fun StapelBadge(
    modifier: Modifier = Modifier,
    count: Int? = null,
    contentDescription: String? = null,
    tone: StapelTone = StapelTone.Error,
    size: BadgeSize = BadgeSize.Medium,
    emphasis: BadgeEmphasis = BadgeEmphasis.Solid,
    max: Int = 99,
    icon: StapelIcon? = null,
    testTag: String? = null,
    content: @Composable () -> Unit = {},
) {
    StapelTheme.skin.Badge(BadgeSpec(count, contentDescription, testTag, tone, size, emphasis, max, icon), modifier, content)
}

/** Pull-to-refresh around [content] through the skin ([PullToRefreshSpec] lists the duties). */
@Composable
public fun StapelPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    label: String = stapelString(CoreUiStrings.Refresh),
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    StapelTheme.skin.PullToRefresh(PullToRefreshSpec(refreshing, onRefresh, label, testTag), modifier, content)
}

/** The phone primary navigation through the skin (the shell draws it; apps without the shell call this). */
@Composable
public fun StapelNavigationBar(
    items: List<NavigationItemSpec>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    StapelTheme.skin.NavigationBar(NavigationSpec(items, selectedId, onSelect, testTag), modifier)
}

/** The tablet / desktop primary navigation through the skin. */
@Composable
public fun StapelNavigationRail(
    items: List<NavigationItemSpec>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    StapelTheme.skin.NavigationRail(NavigationSpec(items, selectedId, onSelect, testTag), modifier)
}

/** A progress indicator through the skin ([ProgressSpec] lists the duties). */
@Composable
public fun StapelProgress(
    kind: ProgressKind = ProgressKind.Spinner,
    modifier: Modifier = Modifier,
    label: String? = null,
    fraction: Float? = null,
    testTag: String? = null,
    skeleton: SkeletonShape = SkeletonShape.Text,
    lines: Int = 1,
) {
    StapelTheme.skin.Progress(ProgressSpec(kind, label, fraction, testTag, skeleton, lines), modifier)
}

/** A select field through the skin; [StapelSelect] pairs it with a [PickerSheet]. */
@Composable
public fun StapelSelectField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    leadingIcon: StapelIcon? = null,
    testTag: String? = null,
) {
    StapelTheme.skin.Select(SelectSpec(label, value, onClick, placeholder, supportingText, error, enabled, leadingIcon, testTag), modifier)
}

/** Page dots through the skin ([PagerIndicatorSpec] lists the duties). */
@Composable
public fun StapelPagerIndicator(
    count: Int,
    current: Int,
    description: String,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    dotLabel: ((Int) -> String)? = null,
    maxDots: Int = 7,
    testTag: String? = null,
) {
    StapelTheme.skin.PagerIndicator(PagerIndicatorSpec(count, current, description, onSelect, dotLabel, maxDots, testTag), modifier)
}

/** A divider through the skin. */
@Composable
public fun StapelDivider(modifier: Modifier = Modifier) {
    StapelTheme.skin.Divider(modifier)
}

/** How wide a page's content may grow. */
public enum class PaneMeasure(
    /** Maximum content width in dp; null = no limit. */
    public val maxWidthDp: Int?,
) {
    /** Forms, sign-in: 480. */
    Narrow(480),

    /** Reading: 720. */
    Reading(720),

    /** Dashboards: 1200. */
    Wide(1200),

    /** No limit. */
    Full(null),
}

/** Test tag of the content column of [StapelPage] (the node the gutter is applied to). */
public const val STAPEL_PAGE_CONTENT_TAG: String = "stapel.page.content"

/** Test tag of [StapelPage]'s top bar when the page draws it from `title`. */
public const val STAPEL_PAGE_TOP_BAR_TAG: String = "stapel.page.topbar"

/** Scroll distance over which a large title collapses into the bar. */
public val LARGE_TITLE_COLLAPSE_DISTANCE: Dp = 96.dp

/**
 * The root of every screen, wiring once what every screen would otherwise wire itself:
 *
 * - the window [insets] (safe drawing: system bars, cutout **and the keyboard**, so a focused field
 *   is never under the IME) and THE page gutter (`StapelTheme.layout.pageGutter`) — a brick screen never
 *   writes a horizontal padding of its own;
 * - the top bar: a custom [topBar], or one drawn from [title] / [subtitle] / [navigation] / [actions];
 *   with [largeTitle] it collapses from a large title into the bar as the content scrolls. A bar drawn
 *   from [title] is the content's chrome title ([StapelChromeTitle]): a [StapelScreenTitle] inside
 *   replaces [title] in the bar instead of drawing a second heading;
 * - pull-to-refresh around the content when [refresh] is given;
 * - the snackbar host over the content when [snackbars] is given (above the bottom bar and the keyboard).
 *
 * @param edgeToEdge content opts out of the gutter (media, lists that bleed).
 */
@Composable
public fun StapelPage(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    measure: PaneMeasure = PaneMeasure.Reading,
    scrollable: Boolean = true,
    edgeToEdge: Boolean = false,
    title: String? = null,
    subtitle: String? = null,
    navigation: IconButtonSpec? = null,
    actions: List<IconButtonSpec> = emptyList(),
    largeTitle: Boolean = false,
    refresh: PullToRefreshSpec? = null,
    snackbars: StapelSnackbarHostState? = null,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = if (edgeToEdge) 0.dp else StapelTheme.layout.pageGutter
    val density = LocalDensity.current
    val distance = with(density) { LARGE_TITLE_COLLAPSE_DISTANCE.toPx() }
    var collapsed by remember { mutableFloatStateOf(0f) }
    val collapsing = largeTitle && title != null && topBar == null
    val connection = remember(collapsing, distance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!collapsing || available.y >= 0f) return Offset.Zero
                val before = collapsed
                collapsed = (collapsed - available.y).coerceAtMost(distance)
                return Offset(0f, -(collapsed - before))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!collapsing || available.y <= 0f) return Offset.Zero
                val before = collapsed
                collapsed = (collapsed - available.y).coerceAtLeast(0f)
                return Offset(0f, before - collapsed)
            }
        }
    }
    // A page that draws its bar from `title` names the screens inside it: their StapelScreenTitle goes to the bar.
    val ownTitle = if (topBar == null && title != null) remember { StapelChromeTitle() } else null
    Box(modifier.fillMaxSize().background(StapelTheme.colors.surface).windowInsetsPadding(insets)) {
        Column(Modifier.fillMaxSize()) {
            when {
                topBar != null -> topBar()
                title != null -> StapelTheme.skin.TopBar(
                    TopBarSpec(
                        ownTitle?.current ?: title, navigation, actions, STAPEL_PAGE_TOP_BAR_TAG, subtitle,
                        if (collapsing) (collapsed / distance).coerceIn(0f, 1f) else null,
                    ),
                    Modifier,
                )
            }
            val body: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .then(measure.maxWidthDp?.let { Modifier.widthIn(max = it.dp) } ?: Modifier)
                            .fillMaxWidth()
                            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .testTag(STAPEL_PAGE_CONTENT_TAG)
                            .padding(horizontal = gutter),
                        verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4),
                    ) {
                        if (ownTitle != null) {
                            CompositionLocalProvider(LocalStapelChromeTitle provides ownTitle) { content() }
                        } else {
                            content()
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(connection)) {
                if (refresh != null) StapelTheme.skin.PullToRefresh(refresh, Modifier.fillMaxSize(), body) else body()
                if (snackbars != null) {
                    StapelSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(horizontal = gutter))
                }
            }
            bottomBar?.invoke()
        }
    }
}
