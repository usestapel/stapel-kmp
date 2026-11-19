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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import stapel.ui.skin.BannerSpec
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
) {
    StapelTheme.skin.TextField(
        TextFieldSpec(
            state, label, kind, placeholder, supportingText, error, enabled, readOnly, leadingIcon, suffix, trailingAction,
            imeAction, onImeAction, autofill, minLines, maxLines, testTag,
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
) {
    StapelTheme.skin.CodeField(CodeFieldSpec(state, length, label, error, enabled, onComplete, testTag), modifier)
}

/**
 * A dialog through the skin; the surface (sheet on a phone, modal otherwise) comes from the layout.
 *
 * @param dismissLabel the accessible label of the dismiss affordance (i18n copy).
 */
@Composable
public fun StapelDialog(
    onDismiss: () -> Unit,
    dismissLabel: String,
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

/** A list row through the skin. */
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
) {
    StapelTheme.skin.ListItem(ListItemSpec(headline, supporting, overline, leading, trailing, onClick, selected, enabled, testTag), modifier)
}

/** A top bar through the skin. */
@Composable
public fun StapelTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: IconButtonSpec? = null,
    actions: List<IconButtonSpec> = emptyList(),
    testTag: String? = null,
) {
    StapelTheme.skin.TopBar(TopBarSpec(title, navigation, actions, testTag), modifier)
}

/** Tabs or a segmented control through the skin. */
@Composable
public fun StapelTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: TabsVariant = TabsVariant.Tabs,
    testTag: String? = null,
) {
    StapelTheme.skin.Tabs(TabsSpec(items, selectedIndex, onSelect, variant, testTag), modifier)
}

/** A progress indicator through the skin. */
@Composable
public fun StapelProgress(
    kind: ProgressKind = ProgressKind.Spinner,
    modifier: Modifier = Modifier,
    label: String? = null,
    fraction: Float? = null,
    testTag: String? = null,
) {
    StapelTheme.skin.Progress(ProgressSpec(kind, label, fraction, testTag), modifier)
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

/**
 * The root of every screen: safe-drawing insets, the page measure, and THE page gutter
 * (`StapelTheme.layout.pageGutter`) — applied here once, so a brick screen never writes a horizontal
 * padding of its own.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = if (edgeToEdge) 0.dp else StapelTheme.layout.pageGutter
    Column(modifier.fillMaxSize().background(StapelTheme.colors.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        topBar?.invoke()
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .then(measure.maxWidthDp?.let { Modifier.widthIn(max = it.dp) } ?: Modifier)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .testTag(STAPEL_PAGE_CONTENT_TAG)
                    .padding(horizontal = gutter),
                verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4),
                content = content,
            )
        }
        bottomBar?.invoke()
    }
}
