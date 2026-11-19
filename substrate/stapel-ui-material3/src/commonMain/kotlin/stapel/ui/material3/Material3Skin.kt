package stapel.ui.material3

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import stapel.i18n.CoreUiStrings
import stapel.ui.DialogSurface
import stapel.ui.StapelIcon
import stapel.ui.StapelIconIds
import stapel.ui.StapelIcons
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
import stapel.ui.stapelString
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.ListItem as M3ListItem
import androidx.compose.material3.Snackbar as M3Snackbar
import androidx.compose.material3.Surface as M3Surface

@Composable
private fun vector(icon: StapelIcon): ImageVector {
    val set = StapelTheme.icons
    return set.resolve(icon.id) ?: StapelIcons.resolve(icon.id) ?: StapelIcons.resolve(StapelIconIds.Unknown.id)!!
}

/**
 * The default skin: Material 3 components, coloured from the §68 roles explicitly (a card is
 * `surface-raised` because the skin says so), controls at the token height with `radii.md` corners,
 * elevation as the token shadow (no tonal tint). The twelve slots the auth screens use are Material;
 * Toggle, Tabs, Chip, Avatar, Badge, NavigationBar, NavigationRail and PullToRefresh use the
 * interface's `FoundationSkin` bodies (basic) until their first consumer lands.
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
        val below: (@Composable () -> Unit)? = (spec.error ?: spec.supportingText)?.let { { Text(it) } }
        val leading: (@Composable () -> Unit)? = spec.leadingIcon?.let { { Icon(vector(it), null) } }
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
                label = { Text(spec.label) },
                placeholder = placeholder,
                leadingIcon = leading,
                trailingIcon = spec.trailingAction?.let { a -> { IconButton(a, Modifier) } },
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
        var expanded by remember { mutableStateOf(false) }
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
                        val label = stapelString(CoreUiStrings.Details)
                        Text(
                            if (expanded) detail else label,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
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

    /** Material `ListItem` coloured from the roles. */
    @Composable
    override fun ListItem(spec: ListItemSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        val onClick = spec.onClick
        M3ListItem(
            headlineContent = { Text(spec.headline) },
            modifier = modifier
                .then(if (onClick != null) Modifier.selectable(selected = spec.selected, enabled = spec.enabled, onClick = onClick) else Modifier)
                .optionalTestTag(spec.testTag),
            overlineContent = spec.overline?.let { { Text(it) } },
            supportingContent = spec.supporting?.let { { Text(it) } },
            leadingContent = spec.leading,
            trailingContent = spec.trailing,
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

    /** Material `TopAppBar` coloured from the roles. */
    @Composable
    override fun TopBar(spec: TopBarSpec, modifier: Modifier) {
        val c = StapelTheme.colors
        TopAppBar(
            title = { Text(spec.title, maxLines = 1, modifier = Modifier.semantics { heading() }) },
            modifier = modifier.optionalTestTag(spec.testTag),
            navigationIcon = { spec.navigation?.let { IconButton(it, Modifier) } },
            actions = { spec.actions.forEach { IconButton(it, Modifier) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface, titleContentColor = c.text),
        )
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
}
