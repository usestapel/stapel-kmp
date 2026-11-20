package stapel.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import stapel.ui.LocalStapelReduceMotion
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import stapel.ui.ComponentColors
import stapel.ui.LocalStapelImages
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
        val keyboard = spec.keyboardOptions()
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
                (spec.leadingIcon ?: StapelIconIds.Search.takeIf { spec.kind == TextFieldKind.Search })?.let {
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
                if (spec.loading) Progress(ProgressSpec(ProgressKind.Spinner, label = stapelString(CoreUiStrings.Loading)), Modifier.padding(horizontal = StapelTheme.spacing.s1))
                val clear = spec.clearLabel
                if (spec.kind == TextFieldKind.Search && clear != null && spec.state.text.isNotEmpty()) {
                    IconButton(IconButtonSpec(StapelIconIds.Close, clear, { spec.state.clearText() }, enabled = spec.enabled), Modifier)
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
                    inputTransformation = spec.inputTransformation,
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
                    inputTransformation = spec.inputTransformation,
                    outputTransformation = spec.outputTransformation,
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
        val digitsOnly = remember(spec.length, spec.inputTransformation) {
            val first = spec.inputTransformation
            InputTransformation {
                val digits = toString().filter { it in '0'..'9' }
                if (digits != toString()) replace(0, length, digits)
            }.then(InputTransformation.maxLength(spec.length)).let { filter -> first?.then(filter) ?: filter }
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
                    // A code is a number: its cells read left to right in every layout direction.
                    Box {
                      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
            ProgressKind.Skeleton -> Skeleton(spec, modifier.then(semantics))
        }
    }

    /** The skeleton shapes, filled with [shimmerBrush]. */
    @Composable
    private fun Skeleton(spec: ProgressSpec, modifier: Modifier) {
        val brush = shimmerBrush()
        val lineHeight = with(LocalDensity.current) { StapelTheme.type.body.lineHeight.toDp() }
        when (spec.skeleton) {
            SkeletonShape.Text -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                val lines = spec.lines.coerceAtLeast(1)
                repeat(lines) { i ->
                    Box(
                        Modifier.fillMaxWidth(if (i == lines - 1 && lines > 1) 0.6f else 1f).height(lineHeight * 0.7f)
                            .clip(StapelTheme.shapes.sm).background(brush),
                    )
                }
            }
            SkeletonShape.Block -> Box(modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp).clip(StapelTheme.shapes.md).background(brush))
            SkeletonShape.Circle -> Box(modifier.defaultMinSize(40.dp, 40.dp).clip(CircleShape).background(brush))
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
        val toggle = spec.accessory as? ListItemAccessory.Toggle
        val onClick = spec.onClick
        val action = when {
            toggle != null -> Modifier.stapelToggle(toggle.kind, toggle.checked, false, spec.enabled, interaction, toggle.onCheckedChange)
                .stapelFocusRing(interaction)
            onClick != null ->
                Modifier.selectable(selected = spec.selected, enabled = spec.enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
                    .stapelFocusRing(interaction)
            else -> Modifier
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(StapelTheme.layout.controlHeight, StapelTheme.layout.minTouchTarget, 48.dp))
                .background(if (spec.selected) colors.brandSubtle else Color.Transparent)
                .then(action)
                .then(if (toggle == null && onClick == null && spec.selected) Modifier.semantics { selected = true } else Modifier)
                .optionalTestTag(spec.testTag)
                .padding(horizontal = StapelTheme.spacing.s4, vertical = StapelTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            CompositionLocalProvider(LocalStapelContentColor provides if (spec.enabled) colors.text else colors.textSubtle) {
                spec.leading?.invoke()
                when (val media = spec.media) {
                    is ListItemMedia.Icon -> StapelIconView(
                        media.icon, null,
                        tint = if (media.tone == StapelTone.Neutral) colors.textMuted else colors.status(media.tone).base,
                    )
                    is ListItemMedia.Avatar -> Avatar(media.spec, Modifier)
                    null -> Unit
                }
                Column(Modifier.weight(1f)) {
                    spec.overline?.let { StapelText(it, style = StapelTheme.type.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    StapelText(spec.headline, style = StapelTheme.type.body, maxLines = spec.headlineMaxLines, overflow = TextOverflow.Ellipsis)
                    spec.supporting?.let {
                        StapelText(it, style = StapelTheme.type.bodySmall, color = colors.textMuted, maxLines = spec.supportingMaxLines, overflow = TextOverflow.Ellipsis)
                    }
                }
                spec.value?.let { value ->
                    Column(Modifier.maxWidthFraction(LIST_VALUE_MAX_FRACTION), horizontalAlignment = Alignment.End) {
                        StapelText(
                            value.text,
                            style = StapelTheme.type.body,
                            color = if (value.tone == StapelTone.Neutral) LocalStapelContentColor.current else colors.status(value.tone).base,
                        )
                        value.supporting?.let { StapelText(it, style = StapelTheme.type.caption, color = colors.textMuted) }
                    }
                }
                when (val accessory = spec.accessory) {
                    ListItemAccessory.Chevron -> StapelIconView(StapelIconIds.ChevronRight, null, size = 20.dp, tint = colors.textMuted)
                    is ListItemAccessory.Badge -> Badge(accessory.spec, Modifier) {}
                    is ListItemAccessory.Toggle -> ToggleGlyph(accessory.kind, accessory.checked, false, spec.enabled)
                    null -> Unit
                }
                spec.trailing?.invoke()
            }
        }
    }

    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        val fraction = spec.collapsedFraction?.coerceIn(0f, 1f)
        Column(modifier.fillMaxWidth().background(StapelTheme.colors.surface).optionalTestTag(spec.testTag)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = StapelTheme.spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                spec.navigation?.let { IconButton(it, Modifier) }
                Column(
                    Modifier.weight(1f).padding(horizontal = StapelTheme.spacing.s3)
                        .then(if (fraction != null) Modifier.alpha(fraction) else Modifier),
                ) {
                    val titleStyle = StapelTheme.type.title
                    // Wraps up to the allowed lines; a title that still does not fit (large text, wide fonts)
                    // steps its size down instead of truncating.
                    BasicText(
                        spec.title,
                        modifier = Modifier.semantics { heading() },
                        style = titleStyle.copy(color = StapelTheme.colors.text),
                        maxLines = TOP_BAR_TITLE_MAX_LINES,
                        autoSize = TextAutoSize.StepBased(minFontSize = TOP_BAR_TITLE_MIN_SIZE, maxFontSize = titleStyle.fontSize, stepSize = 1.sp),
                    )
                    if (fraction == null || fraction >= 1f) spec.subtitle?.let { StapelText(it, style = StapelTheme.type.caption, color = StapelTheme.colors.textMuted) }
                }
                spec.actions.forEach { IconButton(it, Modifier) }
            }
            if (fraction != null && fraction < 1f) {
                Column(
                    Modifier.fillMaxWidth().collapse(1f - fraction).padding(horizontal = StapelTheme.spacing.s4).padding(bottom = StapelTheme.spacing.s3)
                        .semantics { hideFromAccessibility() }
                        .optionalTestTag(spec.testTag?.plus(LARGE_TITLE_TAG_SUFFIX)),
                ) {
                    StapelText(spec.title, style = StapelTheme.type.display)
                    spec.subtitle?.let { StapelText(it, style = StapelTheme.type.bodySmall, color = StapelTheme.colors.textMuted) }
                }
            }
        }
    }

    @Composable
    override fun Select(spec: SelectSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val shape = StapelTheme.shapes.md
        val interaction = remember { MutableInteractionSource() }
        val error = spec.error
        Column(modifier) {
            StapelText(spec.label, style = StapelTheme.type.label, color = colors.textMuted, modifier = Modifier.semantics { hideFromAccessibility() })
            Spacer(Modifier.height(StapelTheme.spacing.s1))
            Row(
                Modifier
                    .fillMaxWidth()
                    .stapelTouchTarget()
                    .heightIn(min = StapelTheme.layout.controlHeight)
                    .clip(shape)
                    .background(if (spec.enabled) colors.surface else colors.surfaceSunken, shape)
                    .border(1.dp, if (error != null) colors.errorBorder else colors.border, shape)
                    .clickable(interactionSource = interaction, indication = null, enabled = spec.enabled, role = Role.DropdownList, onClick = spec.onClick)
                    .stapelFocusRing(interaction, shape)
                    .semantics {
                        contentDescription = spec.label
                        if (error != null) error(error)
                    }
                    .optionalTestTag(spec.testTag)
                    .padding(horizontal = StapelTheme.spacing.s3, vertical = StapelTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2),
            ) {
                spec.leadingIcon?.let { StapelIconView(it, null, size = 20.dp, tint = colors.textMuted) }
                StapelText(
                    spec.value ?: spec.placeholder.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = if (spec.value == null || !spec.enabled) colors.textSubtle else colors.text,
                )
                StapelIconView(StapelIconIds.ChevronDown, null, size = 20.dp, tint = colors.textMuted)
            }
            (error ?: spec.supportingText)?.let {
                Spacer(Modifier.height(StapelTheme.spacing.s1))
                StapelText(it, style = StapelTheme.type.caption, color = if (error != null) colors.error else colors.textMuted, modifier = Modifier.semantics { hideFromAccessibility() })
            }
        }
    }

    @Composable
    override fun PagerIndicator(spec: PagerIndicatorSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val onSelect = spec.onSelect
        Row(
            modifier.semantics(mergeDescendants = onSelect == null) { stateDescription = spec.description }.optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (onSelect == null) 6.dp else 0.dp),
        ) {
            pagerDotWindow(spec.count, spec.current, spec.maxDots).forEach { dot ->
                val size = if (dot.small) 5.dp else 8.dp
                val fill = if (dot.page == spec.current) colors.brand else colors.border
                val glyph = @Composable { Box(Modifier.size(size).background(fill, CircleShape)) }
                if (onSelect == null) {
                    glyph()
                } else {
                    Box(
                        Modifier.stapelTouchTarget().sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                            .clickable(role = Role.Button) { onSelect(dot.page) }
                            .semantics { spec.dotLabel?.let { contentDescription = it(dot.page) }; if (dot.page == spec.current) selected = true },
                        contentAlignment = Alignment.Center,
                    ) { glyph() }
                }
            }
        }
    }

    @Composable
    override fun Menu(spec: MenuSpec, modifier: Modifier, anchor: @Composable () -> Unit) {
        val colors = StapelTheme.colors
        val shape = StapelTheme.shapes.md
        Box(modifier) {
            anchor()
            if (spec.expanded) {
                Popup(
                    popupPositionProvider = MenuPositionProvider,
                    onDismissRequest = spec.onDismiss,
                    // Focusable: back (Android), Escape (desktop) and an outside tap call onDismissRequest.
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 160.dp, max = 320.dp)
                            .stapelShadow(StapelTheme.elevation.high, shape)
                            .background(colors.surfaceOverlay, shape)
                            .clip(shape)
                            .semantics {
                                paneTitle = spec.label
                                dismiss { spec.onDismiss(); true }
                            }
                            .optionalTestTag(spec.testTag)
                            .padding(vertical = StapelTheme.spacing.s1),
                    ) {
                        spec.items.forEach { item -> MenuRow(item, spec.onDismiss) }
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuRow(item: MenuItemSpec, onDismiss: () -> Unit) {
        val colors = StapelTheme.colors
        val interaction = remember { MutableInteractionSource() }
        val tint = when {
            !item.enabled -> colors.textSubtle
            item.destructive -> colors.error
            else -> colors.text
        }
        Row(
            Modifier
                .fillMaxWidth()
                .stapelTouchTarget()
                .heightIn(min = 40.dp)
                .clickable(interactionSource = interaction, indication = null, enabled = item.enabled, role = Role.Button) {
                    item.onClick()
                    onDismiss()
                }
                .stapelFocusRing(interaction, RectangleShapeOf)
                .optionalTestTag(item.testTag)
                .padding(horizontal = StapelTheme.spacing.s4, vertical = StapelTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            item.icon?.let { StapelIconView(it, null, size = 20.dp, tint = tint) }
            StapelText(item.label, color = tint)
        }
    }

    @Composable
    override fun Stepper(spec: StepperSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val shape = StapelTheme.shapes.md
        val shown = spec.shownValue
        Row(
            modifier
                .clip(shape)
                .border(1.dp, if (spec.enabled) colors.border else colors.borderSubtle, shape)
                .semantics {
                    contentDescription = spec.label
                    stateDescription = shown
                }
                .optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(IconButtonSpec(StapelIconIds.Minus, spec.decrementLabel, { spec.onValueChange(spec.stepperValue(-1)) }, enabled = spec.canDecrement), Modifier)
            StapelText(
                shown,
                modifier = Modifier.widthIn(min = 32.dp).padding(horizontal = StapelTheme.spacing.s2).semantics { hideFromAccessibility() },
                style = StapelTheme.type.label,
                color = if (spec.enabled) colors.text else colors.textSubtle,
                textAlign = TextAlign.Center,
            )
            IconButton(IconButtonSpec(StapelIconIds.Plus, spec.incrementLabel, { spec.onValueChange(spec.stepperValue(1)) }, enabled = spec.canIncrement), Modifier)
        }
    }

    @Composable
    override fun ListStatus(spec: ListStatusSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val action = spec.action
        val stale = spec.kind == ListStatusKind.Stale
        Row(
            modifier
                .fillMaxWidth()
                .optionalTestTag(spec.testTag)
                .then(if (spec.kind == ListStatusKind.AppendFailed) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
                .heightIn(min = maxOf(StapelTheme.layout.controlHeight, StapelTheme.layout.minTouchTarget))
                .then(if (stale) Modifier.background(colors.surfaceSunken, StapelTheme.shapes.md) else Modifier)
                .padding(horizontal = StapelTheme.spacing.s4, vertical = StapelTheme.spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3, if (action == null) Alignment.CenterHorizontally else Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (spec.kind == ListStatusKind.Appending) {
                Progress(ProgressSpec(ProgressKind.Spinner, label = spec.message), Modifier)
            }
            StapelText(
                spec.message,
                modifier = (if (action != null) Modifier.weight(1f) else Modifier)
                    .then(if (spec.kind == ListStatusKind.Appending) Modifier.semantics { hideFromAccessibility() } else Modifier),
                style = StapelTheme.type.bodySmall,
                color = if (spec.kind == ListStatusKind.AppendFailed) colors.error else colors.textMuted,
                textAlign = if (action == null) TextAlign.Center else null,
            )
            if (action != null) {
                Button(ButtonSpec(spec.actionLabel.orEmpty(), action, variant = ButtonVariant.Text, size = ControlSize.Small), Modifier)
            }
        }
    }

    @Composable
    override fun ChipGroup(spec: ChipGroupSpec, modifier: Modifier) {
        val skin = StapelTheme.skin
        val gap = StapelTheme.spacing.s2
        val group = modifier
            .semantics { contentDescription = spec.label }
            .selectableGroup()
            .optionalTestTag(spec.testTag)
        when (spec.layout) {
            ChipGroupLayout.Wrap -> FlowRow(
                group,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) { spec.chips.forEach { skin.Chip(it, Modifier) } }
            ChipGroupLayout.Scroll -> Row(
                group.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) { spec.chips.forEach { skin.Chip(it, Modifier) } }
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
        val error = spec.error
        Row(
            modifier = modifier
                .stapelTouchTarget()
                .stapelToggle(spec.kind, spec.checked, spec.indeterminate, spec.enabled, interaction, spec.onCheckedChange)
                .stapelFocusRing(interaction)
                .semantics { if (error != null) error(error) }
                .optionalTestTag(spec.testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
        ) {
            if (!spec.controlAtEnd) ToggleGlyph(spec.kind, spec.checked, spec.indeterminate, spec.enabled)
            Column(Modifier.weight(1f, fill = spec.controlAtEnd)) {
                StapelText(spec.label, color = if (spec.enabled) colors.text else colors.textSubtle)
                spec.supporting?.let { StapelText(it, style = StapelTheme.type.bodySmall, color = colors.textMuted) }
                error?.let { StapelText(it, style = StapelTheme.type.caption, color = colors.error, modifier = Modifier.semantics { hideFromAccessibility() }) }
            }
            if (spec.controlAtEnd) ToggleGlyph(spec.kind, spec.checked, spec.indeterminate, spec.enabled)
        }
    }

    /** The visual of a switch / checkbox / radio (no interaction: the caller's node owns it). */
    @Composable
    private fun ToggleGlyph(kind: ToggleKind, checked: Boolean, indeterminate: Boolean, enabled: Boolean) {
        val colors = StapelTheme.colors
        val on = if (enabled) colors.brand else colors.textSubtle
        val filled = checked || indeterminate
        when (kind) {
            ToggleKind.Switch -> Box(
                Modifier.size(44.dp, 24.dp).background(if (checked) on else colors.border, StapelTheme.shapes.full).padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.size(18.dp).background(colors.surface, CircleShape)) }
            ToggleKind.Checkbox -> Box(
                Modifier.size(20.dp).background(if (filled) on else colors.surface, StapelTheme.shapes.sm)
                    .border(1.5.dp, if (filled) on else colors.border, StapelTheme.shapes.sm),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    indeterminate -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(colors.textOnAccent))
                    checked -> StapelIconView(StapelIconIds.Check, null, size = 16.dp, tint = colors.textOnAccent)
                }
            }
            ToggleKind.Radio -> Box(
                Modifier.size(20.dp).border(1.5.dp, if (checked) on else colors.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (checked) Box(Modifier.size(10.dp).background(on, CircleShape)) }
        }
    }

    @Composable
    override fun Tabs(spec: TabsSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val segmented = spec.variant == TabsVariant.Segmented
        Row(
            modifier = modifier
                .then(if (spec.scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .then(if (segmented) Modifier.background(colors.surfaceSunken, StapelTheme.shapes.md).padding(2.dp) else Modifier)
                .selectableGroup()
                .optionalTestTag(spec.testTag),
        ) {
            spec.items.forEachIndexed { i, label ->
                val selected = i == spec.selectedIndex
                val icon = spec.icons?.getOrNull(i)
                Box(
                    Modifier
                        .then(if (spec.scrollable) Modifier else Modifier.weight(1f))
                        .stapelTouchTarget()
                        .heightIn(min = 40.dp)
                        .then(if (segmented && selected) Modifier.background(colors.surface, StapelTheme.shapes.md) else Modifier)
                        .selectable(selected = selected, role = Role.Tab) { spec.onSelect(i) }
                        .then(if (spec.iconOnly && icon != null) Modifier.semantics { contentDescription = label } else Modifier)
                        .padding(horizontal = StapelTheme.spacing.s3, vertical = StapelTheme.spacing.s1),
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = if (selected) colors.text else colors.textMuted
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                        icon?.let { StapelIconView(it, null, size = 20.dp, tint = tint) }
                        if (!spec.iconOnly || icon == null) StapelText(label, style = StapelTheme.type.label, color = tint, textAlign = TextAlign.Center)
                    }
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
        val status = spec.kind == ChipKind.Status
        val c = ComponentColors.chip(colors, spec.selected, if (status) spec.tone else null, spec.enabled)
        val shape = StapelTheme.shapes.full
        val interaction = remember { MutableInteractionSource() }
        val onClick = spec.onClick
        val minHeight = if (spec.size == ControlSize.Small) 28.dp else 32.dp
        Row(
            modifier = modifier
                .heightIn(min = maxOf(minHeight, StapelTheme.layout.minTouchTarget))
                .clip(shape)
                .background(c.container, shape)
                .border(1.dp, c.outline, shape)
                .then(
                    if (!status && onClick != null) {
                        Modifier.selectable(selected = spec.selected, enabled = spec.enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
                            .stapelFocusRing(interaction, shape)
                    } else {
                        Modifier
                    },
                )
                .optionalTestTag(spec.testTag)
                .padding(start = StapelTheme.spacing.s3, end = if (spec.onRemove != null) 0.dp else StapelTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1),
        ) {
            CompositionLocalProvider(LocalStapelContentColor provides c.content) {
                when {
                    spec.selected && !status && spec.leadingIcon == null && spec.kind == ChipKind.Filter ->
                        StapelIconView(StapelIconIds.Check, null, size = 16.dp)
                    else -> spec.leadingIcon?.let { StapelIconView(it, null, size = 16.dp) }
                }
                StapelText(spec.label, style = StapelTheme.type.label, modifier = Modifier.padding(vertical = StapelTheme.spacing.s1))
                spec.onRemove?.let { remove ->
                    IconButton(IconButtonSpec(StapelIconIds.Close, spec.removeLabel ?: stapelString(CoreUiStrings.Dismiss), remove, enabled = spec.enabled), Modifier)
                }
            }
        }
    }

    @Composable
    override fun Avatar(spec: AvatarSpec, modifier: Modifier) {
        val colors = StapelTheme.colors
        val c = ComponentColors.avatar(colors)
        val interaction = remember { MutableInteractionSource() }
        val painter = spec.image?.let { LocalStapelImages.current.painter(it) }
        val onClick = spec.onClick
        val name = spec.accessibleName
        Box(
            modifier = modifier
                .then(
                    if (onClick != null) {
                        Modifier.stapelTouchTarget().clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                            .stapelFocusRing(interaction, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = name }
                .optionalTestTag(spec.testTag),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(spec.size.dp.dp).clip(CircleShape).background(c.container, CircleShape), contentAlignment = Alignment.Center) {
                val initials = spec.initialsText
                when {
                    painter != null -> Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    initials != null -> StapelText(
                        initials,
                        style = avatarTextStyle(spec.size),
                        color = c.content,
                        maxLines = 1,
                        modifier = Modifier.semantics { hideFromAccessibility() },
                    )
                    else -> StapelIconView(spec.icon ?: StapelIconIds.User, null, size = (spec.size.dp * 0.55f).dp, tint = c.content)
                }
            }
            spec.badge?.takeIf { it.visible }?.let { badge ->
                BadgeMark(
                    BadgeSpec(badge.count, null, badge.testTag, badge.tone, badge.size, badge.emphasis, badge.max, badge.icon),
                    Modifier.align(if (spec.badgePlacement == BadgePlacement.TopEnd) Alignment.TopEnd else Alignment.BottomEnd),
                    ring = true,
                )
            }
        }
    }

    @Composable
    private fun avatarTextStyle(size: AvatarSize) = when (size) {
        AvatarSize.Small -> StapelTheme.type.caption
        AvatarSize.Medium -> StapelTheme.type.label
        AvatarSize.Large -> StapelTheme.type.title
        AvatarSize.XLarge -> StapelTheme.type.heading
    }

    @Composable
    override fun Badge(spec: BadgeSpec, modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier) {
            content()
            if (spec.visible) BadgeMark(spec, Modifier.align(Alignment.TopEnd), ring = false)
        }
    }

    /** The badge itself: a dot or a pill with the count; [ring] draws a surface-coloured outline (on avatars). */
    @Composable
    private fun BadgeMark(spec: BadgeSpec, modifier: Modifier, ring: Boolean) {
        val colors = StapelTheme.colors
        val c = ComponentColors.badge(colors, spec.tone, spec.emphasis == BadgeEmphasis.Solid)
        val label = spec.text
        val shape = StapelTheme.shapes.full
        val description = spec.contentDescription
        val dot = when (spec.size) {
            BadgeSize.Small -> 6.dp
            BadgeSize.Medium -> 8.dp
            BadgeSize.Large -> 10.dp
        }
        val pill = when (spec.size) {
            BadgeSize.Small -> 14.dp
            BadgeSize.Medium -> 16.dp
            BadgeSize.Large -> 20.dp
        }
        Row(
            modifier
                .then(if (ring) Modifier.border(2.dp, colors.surface, shape) else Modifier)
                .then(if (label == null && spec.icon == null) Modifier.size(dot) else Modifier.heightIn(min = pill).widthIn(min = pill))
                .background(c.container, shape)
                .semantics { if (description != null) contentDescription = description }
                .optionalTestTag(spec.testTag)
                .padding(horizontal = if (label == null && spec.icon == null) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            spec.icon?.let { StapelIconView(it, null, size = pill - 4.dp, tint = c.content) }
            label?.let { StapelText(it, style = StapelTheme.type.caption, color = c.content, modifier = Modifier.semantics { hideFromAccessibility() }) }
        }
    }

    @Composable
    override fun NavigationBar(spec: NavigationSpec, modifier: Modifier) {
        Row(
            modifier = modifier.fillMaxWidth().background(StapelTheme.colors.surfaceRaised).selectableGroup().optionalTestTag(spec.testTag),
        ) {
            spec.items.forEach { item -> NavItem(item, item.id == spec.selectedId, { spec.onSelect(item.id) }, Modifier.weight(1f)) }
        }
    }

    @Composable
    override fun NavigationRail(spec: NavigationSpec, modifier: Modifier) {
        Column(
            modifier = modifier.width(88.dp).background(StapelTheme.colors.surfaceRaised).selectableGroup().optionalTestTag(spec.testTag)
                .padding(vertical = StapelTheme.spacing.s2),
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
            StapelText(item.label, style = StapelTheme.type.caption, color = if (selected) colors.text else colors.textMuted, textAlign = TextAlign.Center)
        }
    }

    @Composable
    override fun PullToRefresh(spec: PullToRefreshSpec, modifier: Modifier, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val threshold = with(density) { PULL_REFRESH_THRESHOLD.toPx() }
        val current by rememberUpdatedState(spec)
        var pull by remember { mutableFloatStateOf(0f) }
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (pull > 0f && available.y < 0f) {
                        val used = maxOf(available.y, -pull)
                        pull += used
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f && !current.refreshing) {
                        pull += available.y * PULL_RESISTANCE
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val fire = pull >= threshold && !current.refreshing
                    pull = 0f
                    if (fire) current.onRefresh()
                    return Velocity.Zero
                }
            }
        }
        Column(modifier.refreshAction(spec).nestedScroll(connection).optionalTestTag(spec.testTag)) {
            when {
                spec.refreshing -> Progress(ProgressSpec(ProgressKind.Linear, label = spec.label), Modifier)
                pull > 0f -> Progress(ProgressSpec(ProgressKind.Linear, fraction = (pull / threshold).coerceIn(0f, 1f)), Modifier.semantics { hideFromAccessibility() })
                StapelTheme.layout.input == StapelInput.Pointer ->
                    Button(ButtonSpec(spec.label, spec.onRefresh, variant = ButtonVariant.Text, size = ControlSize.Small, leadingIcon = StapelIconIds.Refresh), Modifier)
            }
            content()
        }
    }
}

/** Test tag suffix of the large title block of a collapsing top bar. */
public const val LARGE_TITLE_TAG_SUFFIX: String = ".large"

/** Lays the element out at [fraction] of its height (clipped), for collapsing blocks. */
private fun Modifier.collapse(fraction: Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction.coerceIn(0f, 1f)).roundToInt()
    layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
}

/**
 * A moving highlight across the `surface-sunken` placeholder colour; a still fill when
 * [LocalStapelReduceMotion] is on.
 */
@Composable
public fun shimmerBrush(): Brush {
    val colors = StapelTheme.colors
    val base = colors.surfaceSunken
    if (LocalStapelReduceMotion.current) return SolidColor(base)
    val highlight = colors.surface
    val transition = rememberInfiniteTransition()
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x * SHIMMER_SPAN, 0f),
        end = Offset((x + 0.5f) * SHIMMER_SPAN, 0f),
    )
}

/** One sweep of the shimmer. */
public const val SHIMMER_PERIOD_MS: Int = 1200

private const val SHIMMER_SPAN: Float = 600f

/** How far content must be pulled (after resistance) to start a refresh. */
public val PULL_REFRESH_THRESHOLD: Dp = 64.dp

/** Share of the finger's travel that moves the pull indicator. */
private const val PULL_RESISTANCE: Float = 0.5f

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
 * Lines a top-bar title may wrap to: the bar grows with large text (200 % scale) instead of
 * truncating a title to its first words, and a title that still does not fit steps its size down
 * to [TOP_BAR_TITLE_MIN_SIZE]; both skins use the same policy.
 */
public const val TOP_BAR_TITLE_MAX_LINES: Int = 3

/** The smallest size a top-bar title steps down to when it does not fit in [TOP_BAR_TITLE_MAX_LINES] at its style's size. */
public val TOP_BAR_TITLE_MIN_SIZE: TextUnit = 12.sp

private val RectangleShapeOf: Shape = androidx.compose.ui.graphics.RectangleShape

/**
 * Places a menu below its anchor, its end edge on the anchor's end edge (left edges in right-to-left
 * layouts), above the anchor when there is no room below, and always inside the window.
 */
internal object MenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        menuPosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
}

/** The menu placement rule of [MenuPositionProvider] (shared by tests). */
internal fun menuPosition(anchor: IntRect, window: IntSize, direction: LayoutDirection, popup: IntSize): IntOffset {
    val endAligned = if (direction == LayoutDirection.Ltr) anchor.right - popup.width else anchor.left
    val x = endAligned.coerceIn(0, maxOf(0, window.width - popup.width))
    val below = anchor.bottom
    val y = if (below + popup.height <= window.height) below else maxOf(0, anchor.top - popup.height)
    return IntOffset(x, y)
}