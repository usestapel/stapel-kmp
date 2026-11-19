package stapel.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import stapel.i18n.CoreUiStrings
import stapel.tokens.Shadow
import stapel.ui.DialogSurface
import stapel.ui.LocalStapelContentColor
import stapel.ui.StapelIcon
import stapel.ui.StapelIconIds
import stapel.ui.StapelIconView
import stapel.ui.StapelInput
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.stapelFocusRing
import stapel.ui.stapelString
import stapel.ui.toColor
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow

/** Test tag suffix of the visible reason a gated control shows. */
public const val GATED_REASON_TAG_SUFFIX: String = ".reason"

/** Applies [tag] as a test tag when non-null. */
public fun Modifier.optionalTestTag(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/** Draws a token elevation [shadow] (null = none) with Compose's drop shadow, outside [shape]. */
@Composable
public fun Modifier.stapelShadow(shadow: Shadow?, shape: Shape): Modifier =
    if (shadow == null) {
        this
    } else {
        dropShadow(
            shape,
            ComposeShadow(radius = shadow.blur.dp, color = shadow.color.toColor(), spread = shadow.spread.dp, offset = DpOffset(shadow.x.dp, shadow.y.dp)),
        )
    }

/** Enforces `StapelTheme.layout.minTouchTarget` (zero under a pointer) as a minimum size. */
@Composable
public fun Modifier.stapelTouchTarget(): Modifier {
    val min = StapelTheme.layout.minTouchTarget
    return if (min.value <= 0f) this else sizeIn(minWidth = min, minHeight = min)
}

/**
 * The token-styled, Material-free implementation of every slot: the fallback for the later slots of
 * any skin, and the base for a custom design system (`object MySkin : StapelSkin by FoundationSkin`).
 */
public object FoundationSkin : StapelSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val layout = StapelTheme.layout
        val shape = StapelTheme.shapes.md
        val gated = spec.gatedReason != null
        val active = spec.enabled && !gated
        val (fill, content, outline) = when (spec.variant) {
            ButtonVariant.Primary -> Triple(colors.brand, colors.textOnAccent, null)
            ButtonVariant.Secondary -> Triple(colors.surface, colors.text, colors.border)
            ButtonVariant.Text -> Triple(Color.Transparent, colors.link, null)
            ButtonVariant.Danger -> Triple(colors.error, colors.errorOn, null)
        }
        val busyLabel = stapelString(CoreUiStrings.Loading)
        val interaction = remember { MutableInteractionSource() }
        val height = if (spec.size == ControlSize.Small) StapelTheme.tokens.controls.height.dp else layout.controlHeight
        Column(modifier = if (spec.fillWidth) modifier.fillMaxWidth() else modifier) {
            Box(
                modifier = Modifier
                    .then(if (spec.fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .stapelTouchTarget()
                    .heightIn(min = height)
                    .clip(shape)
                    .background(if (active) fill else colors.surfaceSunken, shape)
                    .then(if (outline != null && active) Modifier.border(1.dp, outline, shape) else Modifier)
                    .clickable(interactionSource = interaction, indication = null, enabled = spec.enabled, role = Role.Button) {
                        if (!spec.loading && !gated) spec.onClick()
                    }
                    .stapelFocusRing(interaction, shape)
                    .semantics {
                        when {
                            spec.loading -> stateDescription = busyLabel
                            gated -> stateDescription = spec.gatedReason
                        }
                    }
                    .optionalTestTag(spec.testTag)
                    .padding(horizontal = StapelTheme.spacing.s4),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalStapelContentColor provides if (active) content else colors.textSubtle) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                        if (spec.loading) Spinner(Modifier.size(16.dp), LocalStapelContentColor.current)
                        else spec.leadingIcon?.let { StapelIconView(it, null, size = 18.dp) }
                        StapelText(spec.label, style = StapelTheme.type.label)
                    }
                }
            }
            if (spec.gatedReason != null) {
                StapelText(
                    spec.gatedReason,
                    modifier = Modifier.padding(top = StapelTheme.spacing.s1).optionalTestTag(spec.testTag?.plus(GATED_REASON_TAG_SUFFIX)),
                    style = StapelTheme.type.caption,
                    color = colors.textMuted,
                )
            }
        }
    }

    @Composable
    override fun IconButton(spec: IconButtonSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val interaction = remember { MutableInteractionSource() }
        val tint = if (spec.tone == StapelTone.Neutral) colors.text else colors.status(spec.tone).base
        Box(
            modifier = modifier
                .stapelTouchTarget()
                .sizeIn(minWidth = 32.dp, minHeight = 32.dp)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, enabled = spec.enabled, role = Role.Button, onClick = spec.onClick)
                .stapelFocusRing(interaction, CircleShape)
                .semantics { contentDescription = spec.contentDescription }
                .optionalTestTag(spec.testTag),
            contentAlignment = Alignment.Center,
        ) {
            StapelIconView(spec.icon, null, tint = if (spec.enabled) tint else colors.textSubtle)
        }
    }

    @Composable
    override fun TextField(spec: TextFieldSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val type = StapelTheme.type
        val shape = StapelTheme.shapes.md
        val interaction = remember { MutableInteractionSource() }
        var revealed by remember { mutableStateOf(false) }
        val keyboard = KeyboardOptions(
            keyboardType = when (spec.kind) {
                TextFieldKind.Email -> KeyboardType.Email
                TextFieldKind.Phone -> KeyboardType.Phone
                TextFieldKind.Password -> KeyboardType.Password
                TextFieldKind.Number -> KeyboardType.Number
                TextFieldKind.Text, TextFieldKind.Multiline -> KeyboardType.Text
            },
            imeAction = spec.imeAction,
        )
        val outline = when {
            spec.error != null -> colors.errorBorder
            else -> colors.border
        }
        val fieldModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StapelTheme.layout.controlHeight)
            .semantics {
                contentDescription = spec.label
                spec.error?.let { error(it) }
                spec.autofill?.let { contentType = it }
            }
            .optionalTestTag(spec.testTag)
        val textStyle = type.body.copy(color = if (spec.enabled) colors.text else colors.textSubtle)
        val decorator: @Composable (@Composable () -> Unit) -> Unit = { inner ->
            Row(
                modifier = Modifier
                    .background(if (spec.enabled) colors.surface else colors.surfaceSunken, shape)
                    .border(1.dp, outline, shape)
                    .stapelFocusRing(interaction, shape)
                    .padding(horizontal = StapelTheme.spacing.s3, vertical = StapelTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                spec.leadingIcon?.let {
                    StapelIconView(it, null, size = 20.dp, tint = colors.textMuted)
                    Spacer(Modifier.width(StapelTheme.spacing.s2))
                }
                Box(Modifier.weight(1f)) {
                    if (spec.state.text.isEmpty() && spec.placeholder != null) {
                        StapelText(spec.placeholder, style = type.body, color = colors.textSubtle, modifier = Modifier.clearAndSetSemantics { })
                    }
                    inner()
                }
                spec.suffix?.let { StapelText(it, style = type.body, color = colors.textMuted) }
                if (spec.kind == TextFieldKind.Password) {
                    val label = stapelString(if (revealed) CoreUiStrings.HidePassword else CoreUiStrings.ShowPassword)
                    IconButton(
                        IconButtonSpec(if (revealed) StapelIconIds.EyeOff else StapelIconIds.Eye, label, { revealed = !revealed }),
                        Modifier,
                    )
                }
                spec.trailingAction?.let { IconButton(it, Modifier) }
            }
        }
        Column(modifier = modifier) {
            StapelText(spec.label, style = type.label, color = colors.textMuted, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.height(StapelTheme.spacing.s1))
            if (spec.kind == TextFieldKind.Password) {
                BasicSecureTextField(
                    state = spec.state,
                    modifier = fieldModifier,
                    enabled = spec.enabled,
                    textStyle = textStyle,
                    keyboardOptions = keyboard,
                    onKeyboardAction = spec.onImeAction?.let { action -> { action() } },
                    interactionSource = interaction,
                    cursorBrush = SolidColor(colors.brand),
                    textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                    decorator = { inner -> decorator(inner) },
                )
            } else {
                BasicTextField(
                    state = spec.state,
                    modifier = fieldModifier,
                    enabled = spec.enabled,
                    readOnly = spec.readOnly,
                    textStyle = textStyle,
                    keyboardOptions = keyboard,
                    onKeyboardAction = spec.onImeAction?.let { action -> { action() } },
                    lineLimits = if (spec.kind == TextFieldKind.Multiline) {
                        TextFieldLineLimits.MultiLine(spec.minLines, maxOf(spec.minLines, spec.maxLines))
                    } else {
                        TextFieldLineLimits.SingleLine
                    },
                    interactionSource = interaction,
                    cursorBrush = SolidColor(colors.brand),
                    decorator = { inner -> decorator(inner) },
                )
            }
            val below = spec.error ?: spec.supportingText
            if (below != null) {
                Spacer(Modifier.height(StapelTheme.spacing.s1))
                StapelText(
                    below,
                    style = type.caption,
                    color = if (spec.error != null) colors.error else colors.textMuted,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }

    @Composable
    override fun CodeField(spec: CodeFieldSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val shape = StapelTheme.shapes.md
        val interaction = remember { MutableInteractionSource() }
        val digitsOnly = remember(spec.length) {
            InputTransformation {
                val digits = toString().filter { it.isDigit() }
                if (digits != toString()) replace(0, length, digits)
            }.then(InputTransformation.maxLength(spec.length))
        }
        val onComplete = spec.onComplete
        LaunchedEffect(spec.state, spec.length, onComplete) {
            if (onComplete != null) {
                snapshotFlow { spec.state.text.toString() }.collect { if (it.length == spec.length) onComplete(it) }
            }
        }
        Column(modifier) {
            BasicTextField(
                state = spec.state,
                modifier = Modifier
                    .semantics {
                        contentDescription = spec.label
                        contentType = androidx.compose.ui.autofill.ContentType.SmsOtpCode
                        spec.error?.let { error(it) }
                    }
                    .optionalTestTag(spec.testTag),
                enabled = spec.enabled,
                inputTransformation = digitsOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                lineLimits = TextFieldLineLimits.SingleLine,
                interactionSource = interaction,
                textStyle = StapelTheme.type.body.copy(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                decorator = { inner ->
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                            val text = spec.state.text
                            repeat(spec.length) { i ->
                                val current = i == text.length
                                Box(
                                    Modifier
                                        .size(width = 44.dp, height = 52.dp)
                                        .background(colors.surface, shape)
                                        .border(
                                            if (current) 2.dp else 1.dp,
                                            when {
                                                spec.error != null -> colors.errorBorder
                                                current -> colors.focusRing
                                                else -> colors.border
                                            },
                                            shape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    StapelText(text.getOrNull(i)?.toString().orEmpty(), style = StapelTheme.type.heading, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Box(Modifier.size(1.dp).alpha(0f)) { inner() }
                    }
                },
            )
            spec.error?.let {
                Spacer(Modifier.height(StapelTheme.spacing.s1))
                StapelText(it, style = StapelTheme.type.caption, color = colors.error, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }

    @Composable
    override fun Dialog(spec: DialogSpec, content: @Composable () -> Unit) {
        val colors = StapelTheme.colors
        val sheet = spec.surface == DialogSurface.Sheet
        val shape = if (sheet) StapelTheme.shapes.xl else StapelTheme.shapes.lg
        Dialog(
            onDismissRequest = { if (spec.dismissible) spec.onDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = spec.dismissible,
                dismissOnClickOutside = spec.dismissible,
                usePlatformDefaultWidth = !sheet,
            ),
        ) {
            Box(Modifier.then(if (sheet) Modifier.fillMaxSize() else Modifier), contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center) {
                Column(
                    modifier = Modifier
                        .then(if (sheet) Modifier.fillMaxWidth() else Modifier.widthIn(min = 280.dp, max = 560.dp))
                        .stapelShadow(StapelTheme.elevation.high, shape)
                        .background(colors.surfaceOverlay, shape)
                        .semantics {
                            paneTitle = spec.title ?: spec.accessibleName ?: ""
                            if (spec.dismissible) dismiss { spec.onDismiss(); true }
                        }
                        .optionalTestTag(spec.testTag)
                        .padding(StapelTheme.spacing.s5),
                    verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4),
                ) {
                    if (spec.title != null || spec.dismissible) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                spec.title?.let { StapelText(it, style = StapelTheme.type.title, modifier = Modifier.semantics { heading() }) }
                            }
                            if (spec.dismissible) {
                                IconButton(IconButtonSpec(StapelIconIds.Close, spec.dismissLabel, spec.onDismiss), Modifier)
                            }
                        }
                    }
                    CompositionLocalProvider(LocalStapelContentColor provides colors.text) { content() }
                    spec.footer?.let { footer ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2, Alignment.End)) { footer() }
                    }
                }
            }
        }
    }

    @Composable
    override fun Banner(spec: BannerSpec, modifier: Modifier) {
        val tone = StapelTheme.colors.status(spec.tone)
        val shape = StapelTheme.shapes.md
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(tone.bg, shape)
                .border(1.dp, tone.border, shape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .optionalTestTag(spec.testTag)
                .padding(StapelTheme.spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            StapelIconView(toneIcon(spec.tone), null, tint = tone.base, size = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
                CompositionLocalProvider(LocalStapelContentColor provides StapelTheme.colors.text) {
                    spec.title?.let { StapelText(it, style = StapelTheme.type.label) }
                    StapelText(spec.message, style = StapelTheme.type.bodySmall)
                    spec.detail?.let { detail ->
                        StapelText(detail, style = StapelTheme.type.caption, color = StapelTheme.colors.textMuted)
                    }
                    spec.action?.let { Button(it, Modifier.padding(top = StapelTheme.spacing.s1)) }
                }
            }
            if (spec.onDismiss != null) {
                IconButton(IconButtonSpec(StapelIconIds.Close, spec.dismissLabel ?: stapelString(CoreUiStrings.Dismiss), spec.onDismiss), Modifier)
            }
        }
    }

    @Composable
    override fun Progress(spec: ProgressSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val semantics = Modifier.semantics {
            progressBarRangeInfo = spec.fraction?.let { ProgressBarRangeInfo(it.coerceIn(0f, 1f), 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
            spec.label?.let { contentDescription = it }
        }.optionalTestTag(spec.testTag)
        when (spec.kind) {
            ProgressKind.Spinner -> Spinner(modifier.size(24.dp).then(semantics), colors.brand)
            ProgressKind.Linear -> Box(
                modifier.fillMaxWidth().height(4.dp).background(colors.surfaceSunken, StapelTheme.shapes.full).then(semantics),
            ) {
                Box(Modifier.fillMaxWidth(spec.fraction ?: 0.3f).height(4.dp).background(colors.brand, StapelTheme.shapes.full))
            }
            ProgressKind.Skeleton -> Box(modifier.fillMaxWidth().height(16.dp).background(colors.surfaceSunken, StapelTheme.shapes.sm).then(semantics))
        }
    }

    @Composable
    override fun Surface(spec: SurfaceSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val colors = StapelTheme.colors
        val shape = StapelTheme.shapes.lg
        val fill = when (spec.tone) {
            SurfaceTone.Plain -> colors.surface
            SurfaceTone.Raised -> colors.surfaceRaised
            SurfaceTone.Sunken -> colors.surfaceSunken
            SurfaceTone.Overlay -> colors.surfaceOverlay
        }
        val e = StapelTheme.elevation
        val shadow = when (spec.elevation) {
            ElevationStep.None -> e.none
            ElevationStep.Low -> e.low
            ElevationStep.Medium -> e.medium
            ElevationStep.High -> e.high
        }
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .stapelShadow(shadow, shape)
                .clip(shape)
                .background(fill, shape)
                .then(if (spec.bordered) Modifier.border(1.dp, colors.borderSubtle, shape) else Modifier)
                .then(
                    spec.onClick?.let {
                        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = it).stapelFocusRing(interaction, shape)
                    } ?: Modifier,
                )
                .optionalTestTag(spec.testTag),
        ) {
            CompositionLocalProvider(LocalStapelContentColor provides colors.text) { content() }
        }
    }

    @Composable
    override fun ListItem(spec: ListItemSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(StapelTheme.layout.controlHeight, StapelTheme.layout.minTouchTarget, 48.dp))
                .background(if (spec.selected) colors.brandSubtle else Color.Transparent)
                .then(
                    spec.onClick?.let {
                        Modifier.selectable(selected = spec.selected, enabled = spec.enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = it)
                            .stapelFocusRing(interaction)
                    } ?: Modifier,
                )
                .optionalTestTag(spec.testTag)
                .padding(horizontal = StapelTheme.spacing.s4, vertical = StapelTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            CompositionLocalProvider(LocalStapelContentColor provides if (spec.enabled) colors.text else colors.textSubtle) {
                spec.leading?.invoke()
                Column(Modifier.weight(1f)) {
                    spec.overline?.let { StapelText(it, style = StapelTheme.type.caption, color = colors.textMuted) }
                    StapelText(spec.headline, style = StapelTheme.type.body)
                    spec.supporting?.let { StapelText(it, style = StapelTheme.type.bodySmall, color = colors.textMuted) }
                }
                spec.trailing?.invoke()
            }
        }
    }

    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(StapelTheme.colors.surface)
                .optionalTestTag(spec.testTag)
                .padding(horizontal = StapelTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            spec.navigation?.let { IconButton(it, Modifier) }
            StapelText(
                spec.title,
                style = StapelTheme.type.title,
                maxLines = TOP_BAR_TITLE_MAX_LINES,
                modifier = Modifier.weight(1f).padding(horizontal = StapelTheme.spacing.s3).semantics { heading() },
            )
            spec.actions.forEach { IconButton(it, Modifier) }
        }
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(StapelTheme.colors.borderSubtle))
    }

    @Composable
    override fun Snackbar(spec: SnackbarSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val tone = colors.status(spec.tone)
        val shape = StapelTheme.shapes.md
        Row(
            modifier = modifier
                .fillMaxWidth()
                .stapelShadow(StapelTheme.elevation.medium, shape)
                .background(colors.surfaceOverlay, shape)
                .border(1.dp, if (spec.tone == StapelTone.Neutral) colors.borderSubtle else tone.border, shape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .optionalTestTag(spec.testTag)
                .padding(horizontal = StapelTheme.spacing.s4, vertical = StapelTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            StapelText(spec.message, style = StapelTheme.type.bodySmall, modifier = Modifier.weight(1f))
            if (spec.actionLabel != null && spec.onAction != null) {
                Button(ButtonSpec(spec.actionLabel, spec.onAction, variant = ButtonVariant.Text, size = ControlSize.Small), Modifier)
            }
            spec.onDismiss?.let { IconButton(IconButtonSpec(StapelIconIds.Close, stapelString(CoreUiStrings.Dismiss), it), Modifier) }
        }
    }

    @Composable
    override fun Toggle(spec: ToggleSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val interaction = remember { MutableInteractionSource() }
        val on = if (spec.enabled) colors.brand else colors.textSubtle
        Row(
            modifier = modifier
                .stapelTouchTarget()
                .toggleable(
                    value = spec.checked,
                    enabled = spec.enabled,
                    role = when (spec.kind) {
                        ToggleKind.Switch -> Role.Switch
                        ToggleKind.Checkbox -> Role.Checkbox
                        ToggleKind.Radio -> Role.RadioButton
                    },
                    interactionSource = interaction,
                    indication = null,
                    onValueChange = spec.onCheckedChange,
                )
                .stapelFocusRing(interaction)
                .optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            when (spec.kind) {
                ToggleKind.Switch -> Box(
                    Modifier.size(44.dp, 24.dp).background(if (spec.checked) on else colors.border, StapelTheme.shapes.full).padding(3.dp),
                    contentAlignment = if (spec.checked) Alignment.CenterEnd else Alignment.CenterStart,
                ) { Box(Modifier.size(18.dp).background(colors.surface, CircleShape)) }
                ToggleKind.Checkbox -> Box(
                    Modifier.size(20.dp).background(if (spec.checked) on else colors.surface, StapelTheme.shapes.sm)
                        .border(1.5.dp, if (spec.checked) on else colors.border, StapelTheme.shapes.sm),
                    contentAlignment = Alignment.Center,
                ) { if (spec.checked) StapelIconView(StapelIconIds.Check, null, size = 16.dp, tint = colors.textOnAccent) }
                ToggleKind.Radio -> Box(
                    Modifier.size(20.dp).border(1.5.dp, if (spec.checked) on else colors.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (spec.checked) Box(Modifier.size(10.dp).background(on, CircleShape)) }
            }
            StapelText(spec.label, color = if (spec.enabled) colors.text else colors.textSubtle)
        }
    }

    @Composable
    override fun Tabs(spec: TabsSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val segmented = spec.variant == TabsVariant.Segmented
        Row(
            modifier = modifier
                .then(if (segmented) Modifier.background(colors.surfaceSunken, StapelTheme.shapes.md).padding(2.dp) else Modifier)
                .optionalTestTag(spec.testTag),
        ) {
            spec.items.forEachIndexed { i, label ->
                val selected = i == spec.selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .stapelTouchTarget()
                        .heightIn(min = 40.dp)
                        .then(if (segmented && selected) Modifier.background(colors.surface, StapelTheme.shapes.md) else Modifier)
                        .selectable(selected = selected, role = Role.Tab) { spec.onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    StapelText(label, style = StapelTheme.type.label, color = if (selected) colors.text else colors.textMuted)
                    if (!segmented && selected) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(colors.brand))
                    }
                }
            }
        }
    }

    @Composable
    override fun Chip(spec: ChipSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val tone = colors.status(spec.tone)
        val fill = when {
            spec.kind == ChipKind.Status -> tone.bg
            spec.selected -> colors.brandSubtle
            else -> colors.surface
        }
        val outline = when {
            spec.kind == ChipKind.Status -> tone.border
            spec.selected -> colors.brand
            else -> colors.border
        }
        Box(
            modifier = modifier
                .heightIn(min = 32.dp)
                .background(fill, StapelTheme.shapes.full)
                .border(1.dp, outline, StapelTheme.shapes.full)
                .then(spec.onClick?.let { Modifier.selectable(selected = spec.selected, role = Role.Button, onClick = it) } ?: Modifier)
                .optionalTestTag(spec.testTag)
                .padding(horizontal = StapelTheme.spacing.s3, vertical = StapelTheme.spacing.s1),
            contentAlignment = Alignment.Center,
        ) {
            StapelText(spec.label, style = StapelTheme.type.label, color = colors.text)
        }
    }

    @Composable
    override fun Avatar(spec: AvatarSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        Box(
            modifier = modifier
                .size(spec.sizeDp.dp)
                .background(colors.brandSubtle, CircleShape)
                .optionalTestTag(spec.testTag)
                .clearAndSetSemantics { contentDescription = spec.contentDescription },
            contentAlignment = Alignment.Center,
        ) { StapelText(spec.initials.take(2).uppercase(), style = StapelTheme.type.label, color = colors.text) }
    }

    @Composable
    override fun Badge(spec: BadgeSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val colors = StapelTheme.colors
        Box(modifier) {
            content()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .then(if (spec.count == null) Modifier.size(8.dp) else Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp))
                    .background(colors.error, StapelTheme.shapes.full)
                    .semantics { spec.contentDescription?.let { contentDescription = it } }
                    .optionalTestTag(spec.testTag)
                    .padding(horizontal = if (spec.count == null) 0.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                spec.count?.let { StapelText(if (it > 99) "99+" else it.toString(), style = StapelTheme.type.caption, color = colors.errorOn) }
            }
        }
    }

    @Composable
    override fun NavigationBar(spec: NavigationSpec, modifier: Modifier) {
        Row(
            modifier = modifier.fillMaxWidth().background(StapelTheme.colors.surfaceRaised).optionalTestTag(spec.testTag),
        ) {
            spec.items.forEach { item -> NavItem(item, item.id == spec.selectedId, { spec.onSelect(item.id) }, Modifier.weight(1f)) }
        }
    }

    @Composable
    override fun NavigationRail(spec: NavigationSpec, modifier: Modifier) {
        Column(
            modifier = modifier.width(88.dp).background(StapelTheme.colors.surfaceRaised).optionalTestTag(spec.testTag).padding(vertical = StapelTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1),
        ) {
            spec.items.forEach { item -> NavItem(item, item.id == spec.selectedId, { spec.onSelect(item.id) }, Modifier.fillMaxWidth()) }
        }
    }

    @Composable
    private fun NavItem(item: NavigationItemSpec, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
        val colors = StapelTheme.colors
        Column(
            modifier = modifier
                .stapelTouchTarget()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(vertical = StapelTheme.spacing.s1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.background(if (selected) colors.brandSubtle else Color.Transparent, StapelTheme.shapes.full).padding(horizontal = 16.dp, vertical = 4.dp)) {
                val icon: @Composable () -> Unit = { StapelIconView(item.icon, null, tint = if (selected) colors.text else colors.textMuted) }
                if (item.badge != null) Badge(item.badge, Modifier, icon) else icon()
            }
            StapelText(item.label, style = StapelTheme.type.caption, color = if (selected) colors.text else colors.textMuted)
        }
    }

    @Composable
    override fun PullToRefresh(spec: PullToRefreshSpec, modifier: Modifier, content: @Composable () -> Unit) {
        // Foundation body: no drag gesture; a pointer user gets an explicit refresh row, touch users the
        // skin's gesture (Material3Skin) — basic implementation.
        Column(modifier.optionalTestTag(spec.testTag)) {
            if (spec.refreshing) {
                Progress(ProgressSpec(ProgressKind.Linear, label = spec.label), Modifier)
            } else if (StapelTheme.layout.input == StapelInput.Pointer) {
                Button(ButtonSpec(spec.label, spec.onRefresh, variant = ButtonVariant.Text, size = ControlSize.Small, leadingIcon = StapelIconIds.Refresh), Modifier)
            }
            content()
        }
    }
}

/** The status glyph of [tone]. */
public fun toneIcon(tone: StapelTone): StapelIcon = when (tone) {
    StapelTone.Neutral, StapelTone.Info -> StapelIconIds.Info
    StapelTone.Success -> StapelIconIds.CheckCircle
    StapelTone.Warning -> StapelIconIds.AlertTriangle
    StapelTone.Error -> StapelIconIds.AlertCircle
}

/** A static indeterminate ring: no infinite animation, so tests and reduced-motion users are never blocked. */
@Composable
internal fun Spinner(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.minDimension / 8f
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 100f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/**
 * Lines a top-bar title may wrap to before it ellipsizes: the bar grows with large text (200 %
 * scale) instead of truncating a title to its first words; both skins use the same policy.
 */
public const val TOP_BAR_TITLE_MAX_LINES: Int = 3

