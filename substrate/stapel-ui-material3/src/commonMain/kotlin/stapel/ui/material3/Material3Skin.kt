package stapel.ui.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.ImeAction
import stapel.ui.skin.LARGE_TITLE_TAG_SUFFIX
import stapel.ui.skin.SelectSpec
import stapel.ui.skin.ChipGroupSpec
import stapel.ui.skin.MenuSpec
import stapel.ui.skin.StepperSpec
import stapel.ui.skin.ListStatusKind
import stapel.ui.skin.ListStatusSpec
import stapel.ui.skin.canDecrement
import stapel.ui.skin.canIncrement
import stapel.ui.skin.shownValue
import stapel.ui.skin.stepperValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedIconButton
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import stapel.ui.ComponentColors
import stapel.ui.StapelInput
import stapel.ui.skin.AvatarSpec
import stapel.ui.skin.BadgeEmphasis
import stapel.ui.skin.BadgeSize
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.ChipKind
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.LIST_VALUE_MAX_FRACTION
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.ListItemMedia
import stapel.ui.skin.NavigationItemSpec
import stapel.ui.skin.NavigationSpec
import stapel.ui.skin.PullToRefreshSpec
import stapel.ui.skin.TabsSpec
import stapel.ui.skin.TabsVariant
import stapel.ui.skin.ToggleKind
import stapel.ui.skin.ToggleSpec
import stapel.ui.skin.maxWidthFraction
import stapel.ui.skin.refreshAction
import stapel.ui.skin.stapelToggle
import stapel.ui.skin.keyboardOptions
import stapel.ui.skin.text
import stapel.ui.skin.toggleState
import stapel.ui.skin.visible
import androidx.compose.material3.Badge as M3Badge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import stapel.i18n.CoreUiStrings
import stapel.ui.DialogSurface
import stapel.ui.StapelIcon
import stapel.ui.StapelIconIds
import stapel.ui.stapelIconVector
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.skin.BannerSpec
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.CodeFieldSpec
import stapel.ui.skin.ControlSize
import stapel.ui.skin.DialogSpec
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.IconButtonSpec
import stapel.ui.skin.ListItemSpec
import stapel.ui.skin.ProgressKind
import stapel.ui.skin.ProgressSpec
import stapel.ui.skin.SnackbarSpec
import stapel.ui.skin.StapelSkin
import stapel.ui.skin.SurfaceSpec
import stapel.ui.skin.SurfaceTone
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.TextFieldSpec
import stapel.ui.skin.TopBarSpec
import stapel.ui.skin.optionalTestTag
import stapel.ui.skin.stapelShadow
import stapel.ui.skin.stapelTouchTarget
import stapel.ui.skin.toneIcon
import stapel.ui.skin.TOP_BAR_TITLE_MAX_LINES
import stapel.ui.skin.TOP_BAR_TITLE_MIN_SIZE
import stapel.ui.stapelString
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.ListItem as M3ListItem
import androidx.compose.material3.Snackbar as M3Snackbar
import androidx.compose.material3.Surface as M3Surface

@Composable
private fun vector(icon: StapelIcon): ImageVector = stapelIconVector(icon)

/**
 * The default skin: Material 3 components, coloured from the §68 roles explicitly (a card is
 * `surface-raised` because the skin says so), controls at the token height with `radii.md` corners,
 * elevation as the token shadow (no tonal tint). Every slot is a Material component except where
 * Material 3 has none (CodeField, Avatar, ChipGroup: the token-styled foundation bodies; Stepper is
 * built from Material icon buttons).
 */
public object Material3Skin : StapelSkin {
    /** Material `Button` / `OutlinedButton` / `TextButton` by variant, at the token control height. */
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val gated = spec.gatedReason != null
        val shape = StapelTheme.shapes.md
        val height = if (spec.size == ControlSize.Small) StapelTheme.tokens.controls.height.dp else StapelTheme.layout.controlHeight
        val busy = stapelString(CoreUiStrings.Loading)
        val reason = spec.gatedReason
        val action = { if (!spec.loading && !gated) spec.onClick() }
        val buttonModifier = Modifier
            .then(if (spec.fillWidth) Modifier.fillMaxWidth() else Modifier)
            .stapelTouchTarget()
            .heightIn(min = height)
            .semantics {
                when {
                    spec.loading -> stateDescription = busy
                    reason != null -> stateDescription = reason
                }
            }
            .optionalTestTag(spec.testTag)
        val content: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                if (spec.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
                else spec.leadingIcon?.let { Icon(vector(it), null, Modifier.size(18.dp)) }
                Text(spec.label, style = MaterialTheme.typography.labelLarge)
            }
        }
        val enabledLook = spec.enabled && !gated
        Column(if (spec.fillWidth) modifier.fillMaxWidth() else modifier) {
            when (spec.variant) {
                ButtonVariant.Primary, ButtonVariant.Danger -> {
                    val (fill, on) = if (spec.variant == ButtonVariant.Primary) c.brand to c.textOnAccent else c.error to c.errorOn
                    M3Button(
                        onClick = action, modifier = buttonModifier, enabled = spec.enabled, shape = shape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (enabledLook) fill else c.surfaceSunken,
                            contentColor = if (enabledLook) on else c.textSubtle,
                            disabledContainerColor = c.surfaceSunken, disabledContentColor = c.textSubtle,
                        ),
                    ) { content() }
                }
                ButtonVariant.Secondary -> OutlinedButton(
                    onClick = action, modifier = buttonModifier, enabled = spec.enabled, shape = shape,
                    border = BorderStroke(1.dp, if (enabledLook) c.border else c.borderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (enabledLook) c.text else c.textSubtle, disabledContentColor = c.textSubtle),
                ) { content() }
                ButtonVariant.Text -> TextButton(
                    onClick = action, modifier = buttonModifier, enabled = spec.enabled, shape = shape,
                    colors = ButtonDefaults.textButtonColors(contentColor = if (enabledLook) c.link else c.textSubtle, disabledContentColor = c.textSubtle),
                ) { content() }
            }
            spec.gatedReason?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    modifier = Modifier.padding(top = StapelTheme.spacing.s1).optionalTestTag(spec.testTag?.plus(".reason")),
                )
            }
        }
    }

    /** Material `IconButton` with the role-coloured glyph. */
    @Composable
    override fun IconButton(spec: IconButtonSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val tint = if (spec.tone == StapelTone.Neutral) c.text else c.status(spec.tone).base
        M3IconButton(
            onClick = spec.onClick,
            modifier = modifier.stapelTouchTarget().semantics { contentDescription = spec.contentDescription }.optionalTestTag(spec.testTag),
            enabled = spec.enabled,
        ) { Icon(vector(spec.icon), null, tint = if (spec.enabled) tint else c.textSubtle) }
    }

    /** Material `OutlinedTextField` / `OutlinedSecureTextField` bound to the state. */
    @Composable
    override fun TextField(spec: TextFieldSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.focusRing, unfocusedBorderColor = c.border, errorBorderColor = c.errorBorder,
            focusedLabelColor = c.brand, unfocusedLabelColor = c.textMuted, errorLabelColor = c.error,
            focusedTextColor = c.text, unfocusedTextColor = c.text, disabledTextColor = c.textSubtle,
            cursorColor = c.brand, errorSupportingTextColor = c.error, unfocusedSupportingTextColor = c.textMuted,
            focusedSupportingTextColor = c.textMuted, focusedPlaceholderColor = c.textSubtle, unfocusedPlaceholderColor = c.textSubtle,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.surfaceSunken,
            errorContainerColor = c.surface,
        )
        val fieldModifier = modifier
            .fillMaxWidth()
            .heightIn(min = StapelTheme.layout.controlHeight)
            .semantics {
                contentDescription = spec.label
                spec.autofill?.let { contentType = it }
            }
            .optionalTestTag(spec.testTag)
            .semantics { spec.error?.let { error(it) } }
        val keyboard = spec.keyboardOptions()
        val below: (@Composable () -> Unit)? = (spec.error ?: spec.supportingText)?.let { { Text(it) } }
        val leading: (@Composable () -> Unit)? = (spec.leadingIcon ?: StapelIconIds.Search.takeIf { spec.kind == TextFieldKind.Search })?.let { { Icon(vector(it), null) } }
        val clear = spec.clearLabel?.takeIf { spec.kind == TextFieldKind.Search && spec.state.text.isNotEmpty() }
        val loadingLabel = stapelString(CoreUiStrings.Loading)
        val trailing: (@Composable () -> Unit)? = if (spec.loading || clear != null || spec.trailingAction != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (spec.loading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = loadingLabel },
                            color = c.brand, strokeWidth = 2.dp, trackColor = c.surfaceSunken,
                        )
                    }
                    if (clear != null) IconButton(IconButtonSpec(StapelIconIds.Close, clear, { spec.state.clearText() }, enabled = spec.enabled), Modifier)
                    spec.trailingAction?.let { IconButton(it, Modifier) }
                }
            }
        } else {
            null
        }
        val placeholder: (@Composable () -> Unit)? = spec.placeholder?.let { { Text(it) } }
        val suffix: (@Composable () -> Unit)? = spec.suffix?.let { { Text(it) } }
        val onIme = spec.onImeAction
        if (spec.kind == TextFieldKind.Password) {
            var revealed by remember { mutableStateOf(false) }
            val toggleLabel = stapelString(if (revealed) CoreUiStrings.HidePassword else CoreUiStrings.ShowPassword)
            OutlinedSecureTextField(
                state = spec.state,
                modifier = fieldModifier,
                enabled = spec.enabled,
                inputTransformation = spec.inputTransformation,
                label = { Text(spec.label) },
                placeholder = placeholder,
                leadingIcon = leading,
                trailingIcon = {
                    IconButton(IconButtonSpec(if (revealed) StapelIconIds.EyeOff else StapelIconIds.Eye, toggleLabel, { revealed = !revealed }), Modifier)
                },
                supportingText = below,
                isError = spec.error != null,
                textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                keyboardOptions = keyboard,
                onKeyboardAction = onIme?.let { { _ -> it() } },
                shape = StapelTheme.shapes.md,
                colors = fieldColors,
            )
        } else {
            OutlinedTextField(
                state = spec.state,
                modifier = fieldModifier,
                enabled = spec.enabled,
                readOnly = spec.readOnly,
                inputTransformation = spec.inputTransformation,
                outputTransformation = spec.outputTransformation,
                label = { Text(spec.label) },
                placeholder = placeholder,
                leadingIcon = leading,
                trailingIcon = trailing,
                suffix = suffix,
                supportingText = below,
                isError = spec.error != null,
                keyboardOptions = keyboard,
                onKeyboardAction = onIme?.let { { _ -> it() } },
                lineLimits = if (spec.kind == TextFieldKind.Multiline) {
                    TextFieldLineLimits.MultiLine(spec.minLines, maxOf(spec.minLines, spec.maxLines))
                } else {
                    TextFieldLineLimits.SingleLine
                },
                shape = StapelTheme.shapes.md,
                colors = fieldColors,
            )
        }
    }

    /** Material 3 has no one-time-code field: the token-styled foundation field is the Material skin's too. */
    @Composable
    override fun CodeField(spec: CodeFieldSpec, modifier: Modifier) {
        FoundationSkin.CodeField(spec, modifier)
    }

    /** A Material surface in a dialog window: bottom sheet or centered modal. */
    @Composable
    override fun Dialog(spec: DialogSpec, content: @Composable () -> Unit) {
        val c = StapelTheme.colors
        val sheet = spec.surface == DialogSurface.Sheet
        val shape = if (sheet) StapelTheme.shapes.xl else MaterialTheme.shapes.extraLarge
        Dialog(
            onDismissRequest = { if (spec.dismissible) spec.onDismiss() },
            properties = DialogProperties(dismissOnBackPress = spec.dismissible, dismissOnClickOutside = spec.dismissible, usePlatformDefaultWidth = !sheet),
        ) {
            Box(if (sheet) Modifier.fillMaxSize() else Modifier, contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center) {
                M3Surface(
                    modifier = Modifier
                        .then(if (sheet) Modifier.fillMaxWidth() else Modifier.widthIn(min = 280.dp, max = 560.dp))
                        .stapelShadow(StapelTheme.elevation.high, shape)
                        .semantics {
                            paneTitle = spec.title ?: spec.accessibleName ?: ""
                            if (spec.dismissible) dismiss { spec.onDismiss(); true }
                        }
                        .optionalTestTag(spec.testTag),
                    shape = shape,
                    color = c.surfaceOverlay,
                    contentColor = c.text,
                ) {
                    Column(Modifier.padding(StapelTheme.spacing.s5), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4)) {
                        if (spec.title != null || spec.dismissible) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    spec.title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
                                }
                                if (spec.dismissible) IconButton(IconButtonSpec(StapelIconIds.Close, spec.dismissLabel, spec.onDismiss), Modifier)
                            }
                        }
                        content()
                        spec.footer?.let { f ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2, Alignment.End)) { f() }
                        }
                    }
                }
            }
        }
    }

    /** A tinted Material surface with the tone's glyph, message, detail, action and dismiss. */
    @Composable
    override fun Banner(spec: BannerSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val tone = c.status(spec.tone)
        M3Surface(
            modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.optionalTestTag(spec.testTag),
            shape = StapelTheme.shapes.md,
            color = tone.bg,
            contentColor = c.text,
            border = BorderStroke(1.dp, tone.border),
        ) {
            Row(Modifier.padding(StapelTheme.spacing.s3), horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
                Icon(vector(toneIcon(spec.tone)), null, tint = tone.base, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
                    spec.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Text(spec.message, style = MaterialTheme.typography.bodyMedium)
                    spec.detail?.let { detail ->
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    spec.action?.let { Button(it, Modifier) }
                }
                spec.onDismiss?.let { dismiss ->
                    IconButton(IconButtonSpec(StapelIconIds.Close, spec.dismissLabel ?: stapelString(CoreUiStrings.Dismiss), dismiss), Modifier)
                }
            }
        }
    }

    /** Material circular / linear indicators; skeleton from the foundation. */
    @Composable
    override fun Progress(spec: ProgressSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val m = modifier.semantics { spec.label?.let { contentDescription = it } }.optionalTestTag(spec.testTag)
        when (spec.kind) {
            ProgressKind.Spinner -> CircularProgressIndicator(m.size(24.dp), color = c.brand, trackColor = c.surfaceSunken)
            ProgressKind.Linear -> {
                val f = spec.fraction
                if (f != null) LinearProgressIndicator({ f }, m.fillMaxWidth(), color = c.brand, trackColor = c.surfaceSunken)
                else LinearProgressIndicator(m.fillMaxWidth(), color = c.brand, trackColor = c.surfaceSunken)
            }
            ProgressKind.Skeleton -> FoundationSkin.Progress(spec, modifier)
        }
    }

    /** Material `Surface` in the tone's role colour with the token shadow. */
    @Composable
    override fun Surface(spec: SurfaceSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val c = StapelTheme.colors
        val shape = StapelTheme.shapes.lg
        val fill = when (spec.tone) {
            SurfaceTone.Plain -> c.surface
            SurfaceTone.Raised -> c.surfaceRaised
            SurfaceTone.Sunken -> c.surfaceSunken
            SurfaceTone.Overlay -> c.surfaceOverlay
        }
        val e = StapelTheme.elevation
        val shadow = when (spec.elevation) {
            ElevationStep.None -> e.none
            ElevationStep.Low -> e.low
            ElevationStep.Medium -> e.medium
            ElevationStep.High -> e.high
        }
        val border = if (spec.bordered) BorderStroke(1.dp, c.borderSubtle) else null
        val m = modifier.stapelShadow(shadow, shape).optionalTestTag(spec.testTag)
        val onClick = spec.onClick
        if (onClick != null) {
            M3Surface(onClick = onClick, modifier = m, shape = shape, color = fill, contentColor = c.text, border = border) { content() }
        } else {
            M3Surface(modifier = m, shape = shape, color = fill, contentColor = c.text, border = border) { content() }
        }
    }

    /** A Material row: `ListItem` with the value block and accessory in its trailing content; a toggle accessory makes the row the control. */
    @Composable
    override fun ListItem(spec: ListItemSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val onClick = spec.onClick
        val toggle = spec.accessory as? ListItemAccessory.Toggle
        val interaction = remember { MutableInteractionSource() }
        val action = when {
            toggle != null -> Modifier.stapelToggle(toggle.kind, toggle.checked, false, spec.enabled, interaction, toggle.onCheckedChange)
            onClick != null -> Modifier.selectable(selected = spec.selected, enabled = spec.enabled, onClick = onClick, role = Role.Button)
            spec.selected -> Modifier.semantics { selected = true }
            else -> Modifier
        }
        val leading: (@Composable () -> Unit)? = when (val media = spec.media) {
            is ListItemMedia.Icon -> {
                { Icon(vector(media.icon), null, tint = if (media.tone == StapelTone.Neutral) c.textMuted else c.status(media.tone).base) }
            }
            is ListItemMedia.Avatar -> {
                { Avatar(media.spec, Modifier) }
            }
            null -> spec.leading
        }
        val hasTrailing = spec.value != null || spec.accessory != null || spec.trailing != null
        M3ListItem(
            headlineContent = { Text(spec.headline, maxLines = spec.headlineMaxLines, overflow = TextOverflow.Ellipsis) },
            modifier = modifier
                .heightIn(min = maxOf(StapelTheme.layout.minTouchTarget, 48.dp))
                .then(action)
                .optionalTestTag(spec.testTag),
            overlineContent = spec.overline?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            supportingContent = spec.supporting?.let { { Text(it, maxLines = spec.supportingMaxLines, overflow = TextOverflow.Ellipsis) } },
            leadingContent = if (spec.media != null && spec.leading != null) {
                { Row(horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) { spec.leading?.invoke(); leading?.invoke() } }
            } else {
                leading
            },
            trailingContent = if (!hasTrailing) {
                null
            } else {
                {
                    Row(
                        Modifier.maxWidthFraction(LIST_VALUE_MAX_FRACTION),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2),
                    ) {
                        spec.value?.let { value ->
                            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End) {
                                Text(
                                    value.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (value.tone == StapelTone.Neutral) c.text else c.status(value.tone).base,
                                        )
                                value.supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
                            }
                        }
                        when (val accessory = spec.accessory) {
                            ListItemAccessory.Chevron -> Icon(vector(StapelIconIds.ChevronRight), null, tint = c.textMuted)
                            is ListItemAccessory.Badge -> Badge(accessory.spec, Modifier) {}
                            is ListItemAccessory.Toggle -> ToggleControl(accessory.kind, accessory.checked, false, spec.enabled, interaction)
                            null -> Unit
                        }
                        spec.trailing?.invoke()
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = if (spec.selected) c.brandSubtle else Color.Transparent,
                headlineColor = if (spec.enabled) c.text else c.textSubtle,
                supportingColor = c.textMuted,
                overlineColor = c.textMuted,
                leadingIconColor = c.textMuted,
                trailingIconColor = c.textMuted,
            ),
        )
    }

    /** Material `TopAppBar` look (64 dp, title-large; headline-medium large title) coloured from the roles. */
    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val fraction = spec.collapsedFraction?.coerceIn(0f, 1f)
        // Material's TopAppBar has a fixed height and a single-line title, which truncates at large
        // text scales; this keeps its look and lets the bar grow instead.
        M3Surface(modifier = modifier.fillMaxWidth().optionalTestTag(spec.testTag), color = c.surface, contentColor = c.text) {
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    spec.navigation?.let { IconButton(it, Modifier) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp).then(if (fraction != null) Modifier.alpha(fraction) else Modifier)) {
                        val titleStyle = MaterialTheme.typography.titleLarge
                        // Wraps up to the allowed lines; a title that still does not fit (large text, wide
                        // fonts) steps its size down instead of truncating.
                        Text(
                            spec.title,
                            style = titleStyle,
                            maxLines = TOP_BAR_TITLE_MAX_LINES,
                            autoSize = TextAutoSize.StepBased(minFontSize = TOP_BAR_TITLE_MIN_SIZE, maxFontSize = titleStyle.fontSize, stepSize = 1.sp),
                            modifier = Modifier.semantics { heading() },
                        )
                        if (fraction == null || fraction >= 1f) spec.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                    }
                    spec.actions.forEach { IconButton(it, Modifier) }
                }
                if (fraction != null && fraction < 1f) {
                    Column(
                        Modifier.fillMaxWidth().collapseTo(1f - fraction).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .semantics { hideFromAccessibility() }
                            .optionalTestTag(spec.testTag?.plus(LARGE_TITLE_TAG_SUFFIX)),
                    ) {
                        Text(spec.title, style = MaterialTheme.typography.headlineMedium)
                        spec.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                    }
                }
            }
        }
    }

    /** An outlined Material field look that opens the picker (Material's exposed-dropdown menu is a dropdown: never on a phone). */
    @Composable
    override fun Select(spec: SelectSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val error = spec.error
        Column(modifier) {
            M3Surface(
                onClick = spec.onClick,
                enabled = spec.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .stapelTouchTarget()
                    .heightIn(min = StapelTheme.layout.controlHeight)
                    .semantics {
                        role = Role.DropdownList
                        contentDescription = spec.label
                        if (error != null) error(error)
                    }
                    .optionalTestTag(spec.testTag),
                shape = StapelTheme.shapes.md,
                color = if (spec.enabled) c.surface else c.surfaceSunken,
                contentColor = c.text,
                border = BorderStroke(1.dp, if (error != null) c.errorBorder else c.border),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
                ) {
                    spec.leadingIcon?.let { Icon(vector(it), null, tint = c.textMuted) }
                    Column(Modifier.weight(1f)) {
                        Text(spec.label, style = MaterialTheme.typography.bodySmall, color = if (error != null) c.error else c.textMuted, modifier = Modifier.semantics { hideFromAccessibility() })
                        Text(
                            spec.value ?: spec.placeholder.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (spec.value == null || !spec.enabled) c.textSubtle else c.text,
                        )
                    }
                    Icon(vector(StapelIconIds.ChevronDown), null, tint = c.textMuted)
                }
            }
            (error ?: spec.supportingText)?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = if (error != null) c.error else c.textMuted,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp).semantics { hideFromAccessibility() },
                )
            }
        }
    }

    /** Material `HorizontalDivider` in `border-subtle`. */
    @Composable
    override fun Divider(modifier: Modifier) {
        HorizontalDivider(modifier, thickness = 1.dp, color = StapelTheme.colors.borderSubtle)
    }

    /** Material `Snackbar` on `surface-overlay`. */
    @Composable
    override fun Snackbar(spec: SnackbarSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val onAction = spec.onAction
        val actionLabel = spec.actionLabel
        val onDismiss = spec.onDismiss
        val dismissLabel = stapelString(CoreUiStrings.Dismiss)
        M3Snackbar(
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.optionalTestTag(spec.testTag),
            action = if (actionLabel != null && onAction != null) {
                { TextButton(onAction, colors = ButtonDefaults.textButtonColors(contentColor = c.link)) { Text(actionLabel) } }
            } else {
                null
            },
            dismissAction = onDismiss?.let { { IconButton(IconButtonSpec(StapelIconIds.Close, dismissLabel, it), Modifier) } },
            shape = StapelTheme.shapes.md,
            containerColor = c.surfaceOverlay,
            contentColor = c.text,
        ) { Text(spec.message) }
    }

    /** Material `Switch` / `TriStateCheckbox` / `RadioButton` in a row that owns the interaction (label tap toggles too). */
    @Composable
    override fun Toggle(spec: ToggleSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val interaction = remember { MutableInteractionSource() }
        val error = spec.error
        Row(
            modifier = modifier
                .stapelTouchTarget()
                .stapelToggle(spec.kind, spec.checked, spec.indeterminate, spec.enabled, interaction, spec.onCheckedChange)
                .semantics { if (error != null) error(error) }
                .optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            if (!spec.controlAtEnd) ToggleControl(spec.kind, spec.checked, spec.indeterminate, spec.enabled, interaction)
            Column(Modifier.weight(1f, fill = spec.controlAtEnd)) {
                Text(spec.label, style = MaterialTheme.typography.bodyLarge, color = if (spec.enabled) c.text else c.textSubtle)
                spec.supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.semantics { hideFromAccessibility() }) }
            }
            if (spec.controlAtEnd) ToggleControl(spec.kind, spec.checked, spec.indeterminate, spec.enabled, interaction)
        }
    }

    /** The Material control without its own click handling (the row owns it). */
    @Composable
    private fun ToggleControl(kind: ToggleKind, checked: Boolean, indeterminate: Boolean, enabled: Boolean, interaction: MutableInteractionSource) {
        val c = StapelTheme.colors
        when (kind) {
            ToggleKind.Switch -> Switch(
                checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = interaction,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.textOnAccent, checkedTrackColor = c.brand, checkedBorderColor = c.brand,
                    uncheckedThumbColor = c.textMuted, uncheckedTrackColor = c.surfaceSunken, uncheckedBorderColor = c.border,
                ),
            )
            ToggleKind.Checkbox -> TriStateCheckbox(
                state = toggleState(checked, indeterminate), onClick = null, enabled = enabled, interactionSource = interaction,
                colors = CheckboxDefaults.colors(checkedColor = c.brand, uncheckedColor = c.border, checkmarkColor = c.textOnAccent),
            )
            ToggleKind.Radio -> RadioButton(
                selected = checked, onClick = null, enabled = enabled, interactionSource = interaction,
                colors = RadioButtonDefaults.colors(selectedColor = c.brand, unselectedColor = c.border),
            )
        }
    }

    /** Material primary tab rows (fixed or scrollable) or a single-choice segmented button row. */
    @Composable
    override fun Tabs(spec: TabsSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val valid = spec.selectedIndex in spec.items.indices
        if (spec.variant == TabsVariant.Segmented) {
            SingleChoiceSegmentedButtonRow(modifier.selectableGroup().optionalTestTag(spec.testTag)) {
                spec.items.forEachIndexed { i, label ->
                    val icon = spec.icons?.getOrNull(i)
                    SegmentedButton(
                        selected = i == spec.selectedIndex,
                        onClick = { spec.onSelect(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, spec.items.size),
                        modifier = Modifier
                            .stapelTouchTarget()
                            .semantics { role = Role.Tab }
                            .then(if (spec.iconOnly && icon != null) Modifier.semantics { contentDescription = label } else Modifier),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = c.brandSubtle, activeContentColor = c.text, activeBorderColor = c.border,
                            inactiveContainerColor = c.surface, inactiveContentColor = c.textMuted, inactiveBorderColor = c.border,
                        ),
                        icon = {},
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
                                icon?.let { Icon(vector(it), null, Modifier.size(18.dp)) }
                                if (!spec.iconOnly || icon == null) Text(label, textAlign = TextAlign.Center)
                            }
                        },
                    )
                }
            }
            return
        }
        val tabs: @Composable () -> Unit = {
            spec.items.forEachIndexed { i, label ->
                val icon = spec.icons?.getOrNull(i)
                Tab(
                    selected = i == spec.selectedIndex,
                    onClick = { spec.onSelect(i) },
                    modifier = Modifier
                        .stapelTouchTarget()
                        .then(if (spec.iconOnly && icon != null) Modifier.semantics { contentDescription = label } else Modifier),
                    text = if (spec.iconOnly && icon != null) null else ({ Text(label, textAlign = TextAlign.Center) }),
                    icon = icon?.let { { Icon(vector(it), null) } },
                    selectedContentColor = c.text,
                    unselectedContentColor = c.textMuted,
                )
            }
        }
        val selected = if (valid) spec.selectedIndex else 0
        val indicator: @Composable TabIndicatorScope.() -> Unit = {
            if (valid) {
                TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(spec.selectedIndex, matchContentSize = true), width = Dp.Unspecified, color = c.brand)
            }
        }
        val rowModifier = modifier.selectableGroup().optionalTestTag(spec.testTag)
        if (spec.scrollable) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selected, modifier = rowModifier, containerColor = c.surface, contentColor = c.text,
                edgePadding = 0.dp, indicator = indicator, divider = { HorizontalDivider(color = c.borderSubtle) }, tabs = tabs,
            )
        } else {
            PrimaryTabRow(
                selectedTabIndex = selected, modifier = rowModifier, containerColor = c.surface, contentColor = c.text,
                indicator = indicator, divider = { HorizontalDivider(color = c.borderSubtle) }, tabs = tabs,
            )
        }
    }

    /** Material `FilterChip` / `InputChip` for interactive chips; a static Material surface for status chips. */
    @Composable
    override fun Chip(spec: ChipSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val status = spec.kind == ChipKind.Status
        val colors = ComponentColors.chip(c, spec.selected, if (status) spec.tone else null, spec.enabled)
        val onClick = spec.onClick
        val remove: (@Composable () -> Unit)? = spec.onRemove?.let { r ->
            { IconButton(IconButtonSpec(StapelIconIds.Close, spec.removeLabel ?: stapelString(CoreUiStrings.Dismiss), r, enabled = spec.enabled), Modifier) }
        }
        val leading: (@Composable () -> Unit)? = when {
            spec.leadingIcon != null -> { { Icon(vector(spec.leadingIcon ?: StapelIconIds.Unknown), null, Modifier.size(FilterChipDefaults.IconSize)) } }
            spec.kind == ChipKind.Filter && spec.selected -> { { Icon(vector(StapelIconIds.Check), null, Modifier.size(FilterChipDefaults.IconSize)) } }
            else -> null
        }
        val label: @Composable () -> Unit = { Text(spec.label, modifier = Modifier.padding(vertical = StapelTheme.spacing.s1)) }
        val m = modifier.heightIn(min = if (spec.size == ControlSize.Small) 28.dp else 32.dp).optionalTestTag(spec.testTag)
        when {
            status || onClick == null -> M3Surface(
                modifier = m,
                shape = StapelTheme.shapes.full,
                color = colors.container,
                contentColor = colors.content,
                border = BorderStroke(1.dp, colors.outline),
            ) {
                Row(
                    Modifier.heightIn(min = if (spec.size == ControlSize.Small) 28.dp else 32.dp)
                        .padding(start = StapelTheme.spacing.s3, end = if (remove == null) StapelTheme.spacing.s3 else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1),
                ) {
                    leading?.invoke()
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
                    remove?.invoke()
                }
            }
            spec.kind == ChipKind.Input -> InputChip(
                selected = spec.selected, onClick = onClick, label = label, modifier = m, enabled = spec.enabled,
                leadingIcon = leading, trailingIcon = remove, shape = StapelTheme.shapes.full,
                colors = InputChipDefaults.inputChipColors(
                    containerColor = c.surface, labelColor = c.text, selectedContainerColor = c.brandSubtle, selectedLabelColor = c.text,
                ),
                border = InputChipDefaults.inputChipBorder(spec.enabled, spec.selected, borderColor = c.border, selectedBorderColor = c.brand, selectedBorderWidth = 1.dp),
            )
            else -> FilterChip(
                selected = spec.selected, onClick = onClick, label = label, modifier = m, enabled = spec.enabled,
                leadingIcon = leading, trailingIcon = remove, shape = StapelTheme.shapes.full,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.surface, labelColor = c.text, selectedContainerColor = c.brandSubtle, selectedLabelColor = c.text,
                    iconColor = c.textMuted, selectedLeadingIconColor = c.text,
                ),
                border = FilterChipDefaults.filterChipBorder(spec.enabled, spec.selected, borderColor = c.border, selectedBorderColor = c.brand, selectedBorderWidth = 1.dp),
            )
        }
    }

    /** Material `DropdownMenu` on `surface-overlay`, `DropdownMenuItem`s; destructive items in the error role. */
    @Composable
    override fun Menu(spec: MenuSpec, modifier: Modifier, anchor: @Composable () -> Unit) {
        val c = StapelTheme.colors
        Box(modifier) {
            anchor()
            DropdownMenu(
                expanded = spec.expanded,
                onDismissRequest = spec.onDismiss,
                modifier = Modifier.semantics {
                    paneTitle = spec.label
                    dismiss { spec.onDismiss(); true }
                }.optionalTestTag(spec.testTag),
                shape = StapelTheme.shapes.md,
                containerColor = c.surfaceOverlay,
                tonalElevation = 0.dp,
            ) {
                spec.items.forEach { item ->
                    val tint = if (item.destructive) c.error else c.text
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            item.onClick()
                            spec.onDismiss()
                        },
                        modifier = Modifier.stapelTouchTarget().semantics { role = Role.Button }.optionalTestTag(item.testTag),
                        leadingIcon = item.icon?.let { icon -> { Icon(vector(icon), null) } },
                        enabled = item.enabled,
                        colors = MenuDefaults.itemColors(
                            textColor = tint, leadingIconColor = tint,
                            disabledTextColor = c.textSubtle, disabledLeadingIconColor = c.textSubtle,
                        ),
                    )
                }
            }
        }
    }

    /** Material `OutlinedIconButton`s around the value (Material 3 has no stepper component). */
    @Composable
    override fun Stepper(spec: StepperSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val shown = spec.shownValue
        Row(
            modifier.semantics {
                contentDescription = spec.label
                stateDescription = shown
            }.optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2),
        ) {
            StepperButton(StapelIconIds.Minus, spec.decrementLabel, spec.canDecrement) { spec.onValueChange(spec.stepperValue(-1)) }
            Text(
                shown,
                modifier = Modifier.widthIn(min = 32.dp).semantics { hideFromAccessibility() },
                style = MaterialTheme.typography.titleMedium,
                color = if (spec.enabled) c.text else c.textSubtle,
                textAlign = TextAlign.Center,
            )
            StepperButton(StapelIconIds.Plus, spec.incrementLabel, spec.canIncrement) { spec.onValueChange(spec.stepperValue(1)) }
        }
    }

    @Composable
    private fun StepperButton(icon: StapelIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
        val c = StapelTheme.colors
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.stapelTouchTarget().semantics { contentDescription = label },
            border = BorderStroke(1.dp, if (enabled) c.border else c.borderSubtle),
        ) { Icon(vector(icon), null, tint = if (enabled) c.text else c.textSubtle) }
    }

    /**
     * Material 3 has no list-status component: a full-width row with a Material `CircularProgressIndicator`
     * (appending), the message, and a Material `TextButton` (retry / refresh); stale rows sit on a tonal
     * `Surface`.
     */
    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val action = spec.action
        val stale = spec.kind == ListStatusKind.Stale
        M3Surface(
            modifier
                .fillMaxWidth()
                .then(if (spec.kind == ListStatusKind.AppendFailed) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
                .optionalTestTag(spec.testTag),
            color = if (stale) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
            shape = if (stale) MaterialTheme.shapes.medium else androidx.compose.ui.graphics.RectangleShape,
        ) {
            Row(
                Modifier
                    .heightIn(min = maxOf(StapelTheme.layout.controlHeight, StapelTheme.layout.minTouchTarget))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, if (action == null) Alignment.CenterHorizontally else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (spec.kind == ListStatusKind.Appending) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp).semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            contentDescription = spec.message
                        },
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    spec.message,
                    modifier = (if (action != null) Modifier.weight(1f) else Modifier)
                        .then(if (spec.kind == ListStatusKind.Appending) Modifier.semantics { hideFromAccessibility() } else Modifier),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (spec.kind == ListStatusKind.AppendFailed) c.error else c.textMuted,
                    textAlign = if (action == null) TextAlign.Center else null,
                )
                if (action != null) {
                    TextButton(onClick = action, modifier = Modifier.stapelTouchTarget()) { Text(spec.actionLabel.orEmpty()) }
                }
            }
        }
    }

    /** Material 3 has no chip-group component: the foundation arrangement, drawing each chip with the Material chip. */
    @Composable
    override fun ChipGroup(spec: ChipGroupSpec, modifier: Modifier) {
        FoundationSkin.ChipGroup(spec, modifier)
    }

    /** Material 3 has no avatar component: the token-styled foundation avatar is the Material skin's too. */
    @Composable
    override fun Avatar(spec: AvatarSpec, modifier: Modifier) {
        FoundationSkin.Avatar(spec, modifier)
    }

    /** Material `Badge` in the tone's derived colours on [content]. */
    @Composable
    override fun Badge(spec: BadgeSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val c = ComponentColors.badge(StapelTheme.colors, spec.tone, spec.emphasis == BadgeEmphasis.Solid)
        val text = spec.text
        val description = spec.contentDescription
        Box(modifier) {
            content()
            if (spec.visible) {
                val dot = when (spec.size) {
                    BadgeSize.Small -> 6.dp
                    BadgeSize.Medium -> 8.dp
                    BadgeSize.Large -> 10.dp
                }
                M3Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(if (text == null && spec.icon == null) Modifier.size(dot) else Modifier)
                        .semantics { if (description != null) contentDescription = description }
                        .optionalTestTag(spec.testTag),
                    containerColor = c.container,
                    contentColor = c.content,
                ) {
                    spec.icon?.let { Icon(vector(it), null, Modifier.size(12.dp)) }
                    text?.let { Text(it, modifier = Modifier.semantics { hideFromAccessibility() }) }
                }
            }
        }
    }

    /** Material `NavigationBarItem`s in a row that grows with large text (the Material container has a fixed height). */
    @Composable
    override fun NavigationBar(spec: NavigationSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        M3Surface(modifier = modifier.fillMaxWidth().optionalTestTag(spec.testTag), color = c.surfaceRaised, contentColor = c.text) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                spec.items.forEach { item ->
                    NavigationBarItem(
                        selected = item.id == spec.selectedId,
                        onClick = { spec.onSelect(item.id) },
                        icon = { NavIcon(item) },
                        label = { Text(item.label, textAlign = TextAlign.Center) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.text, selectedTextColor = c.text, indicatorColor = c.brandSubtle,
                            unselectedIconColor = c.textMuted, unselectedTextColor = c.textMuted,
                        ),
                    )
                }
            }
        }
    }

    /** Material `NavigationRail` with role-coloured items. */
    @Composable
    override fun NavigationRail(spec: NavigationSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        NavigationRail(
            modifier = modifier.optionalTestTag(spec.testTag),
            containerColor = c.surfaceRaised,
            contentColor = c.text,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
                spec.items.forEach { item ->
                    NavigationRailItem(
                        selected = item.id == spec.selectedId,
                        onClick = { spec.onSelect(item.id) },
                        icon = { NavIcon(item) },
                        label = { Text(item.label, textAlign = TextAlign.Center) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = c.text, selectedTextColor = c.text, indicatorColor = c.brandSubtle,
                            unselectedIconColor = c.textMuted, unselectedTextColor = c.textMuted,
                        ),
                    )
                }
            }
        }
    }

    /** Lays the element out at [fraction] of its height (clipped), for the collapsing large title. */
    private fun Modifier.collapseTo(fraction: Float): Modifier = clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * fraction.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
    }

    @Composable
    private fun NavIcon(item: NavigationItemSpec) {
        val icon: @Composable () -> Unit = { Icon(vector(item.icon), null) }
        val badge = item.badge
        if (badge != null) Badge(badge, Modifier, icon) else icon()
    }

    /** Material `PullToRefreshBox` with a named progress indicator, the refresh as an accessibility action, and a pointer button. */
    @Composable
    override fun PullToRefresh(spec: PullToRefreshSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val c = StapelTheme.colors
        val state = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = spec.refreshing,
            onRefresh = spec.onRefresh,
            modifier = modifier.refreshAction(spec).optionalTestTag(spec.testTag),
            state = state,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = spec.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).semantics {
                        if (spec.refreshing) {
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            contentDescription = spec.label
                        }
                    },
                    containerColor = c.surfaceRaised,
                    color = c.brand,
                )
            },
        ) {
            Column {
                if (!spec.refreshing && StapelTheme.layout.input == StapelInput.Pointer) {
                    TextButton(spec.onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = c.link)) {
                        Icon(vector(StapelIconIds.Refresh), null, Modifier.size(18.dp))
                        Text(spec.label, modifier = Modifier.padding(start = StapelTheme.spacing.s2))
                    }
                }
                content()
            }
        }
    }
}
