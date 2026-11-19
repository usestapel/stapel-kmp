package stapel.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Box
import stapel.ui.StapelBanner
import stapel.ui.StapelButton
import stapel.ui.StapelCodeField
import stapel.ui.StapelLink
import stapel.ui.StapelProgress
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.TextFieldKind
import stapel.ui.stapelPlural
import stapel.ui.stapelString
import kotlin.math.ceil

/**
 * The one-time code step (web `OtpCodeStep`): where the code went (with "Change" back to the
 * identifier), the prototype hint, one code field that submits itself when full, the refusal with
 * the attempts left, and "Resend code" gated by the cooldown. A new [OtpCodeState.error] clears the
 * field and puts the focus back into it.
 */
@Composable
public fun OtpCodeScreen(state: OtpCodeState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    val code = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        if (state.error != null) {
            code.clearText()
            submitted = false
        }
        // The field is attached in the composition this effect follows; an unattached requester is a no-op.
        focus.requestFocus()
    }
    Column(modifier.fillMaxWidth().testTag(AuthTestTags.CODE_STEP), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            StapelText(stapelString(AuthUiStrings.OtpSentTo, "target" to state.maskedTarget), Modifier.weight(1f), color = StapelTheme.colors.textMuted)
            StapelLink(stapelString(AuthUiStrings.EditIdentifier), { onEvent(SignInEvent.EditIdentifier) }, Modifier.testTag(AuthTestTags.EDIT_IDENTIFIER))
        }
        if (state.isMock) {
            StapelBanner(StapelTone.Warning, stapelString(AuthUiStrings.OtpMockDelivery), testTag = AuthTestTags.MOCK_HINT)
        }
        val label = stapelString(AuthUiStrings.OtpEnterCode)
        StapelText(label)
        StapelCodeField(
            code,
            length = state.codeLength,
            label = label,
            modifier = Modifier.focusRequester(focus),
            error = state.error?.let { authErrorText(it) },
            enabled = !state.verifying,
            onComplete = { value ->
                if (!submitted && !state.verifying) {
                    submitted = true
                    onEvent(SignInEvent.SubmitCode(value))
                }
            },
            testTag = AuthTestTags.CODE_FIELD,
        )
        // The skin draws the error under the field; this node announces it once, politely.
        state.error?.let { PoliteAnnouncement(authErrorText(it), Modifier.testTag(AuthTestTags.CODE_ERROR)) }
        if (state.verifying) StapelProgress(label = stapelString(AuthUiStrings.OtpEnterCode))
        val wait = ceil(state.resendIn.inWholeMilliseconds / 1000.0).toLong()
        StapelButton(
            stapelString(AuthUiStrings.OtpResend),
            onClick = { onEvent(SignInEvent.Resend) },
            variant = ButtonVariant.Text,
            gatedReason = if (wait > 0) stapelString(AuthUiStrings.ResendIn, "s" to wait) else null,
            testTag = AuthTestTags.RESEND,
        )
    }
}

/**
 * The TOTP challenge (web `TotpStepForm`): an authenticator code, or — toggled — a backup code;
 * submit; back to sign-in.
 */
@Composable
public fun TotpChallengeScreen(state: TotpChallengeState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    var backup by rememberSaveable { mutableStateOf(false) }
    val value = rememberTextFieldState()
    val submit = { if (value.text.isNotBlank()) onEvent(SignInEvent.SubmitTotp(value.text.toString().trim(), backup)) }
    Column(modifier.fillMaxWidth().testTag(AuthTestTags.TOTP_STEP), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
        AuthHeading(stapelString(AuthUiStrings.TotpTitle))
        StapelTextField(
            value,
            label = stapelString(if (backup) AuthUiStrings.TotpUseBackup else AuthUiStrings.TotpEnterCode),
            kind = if (backup) TextFieldKind.Text else TextFieldKind.Number,
            error = state.error?.let { authErrorText(it) },
            enabled = !state.submitting,
            imeAction = ImeAction.Done,
            onImeAction = submit,
            autofill = ContentType.SmsOtpCode,
            testTag = AuthTestTags.TOTP_FIELD,
        )
        StapelButton(
            stapelString(AuthUiStrings.Submit),
            onClick = submit,
            enabled = value.text.isNotBlank(),
            loading = state.submitting,
            fillWidth = true,
            testTag = AuthTestTags.SUBMIT,
        )
        StapelButton(
            stapelString(if (backup) AuthUiStrings.TotpUseApp else AuthUiStrings.TotpUseBackup),
            onClick = {
                backup = !backup
                value.clearText()
            },
            variant = ButtonVariant.Text,
            fillWidth = true,
            testTag = AuthTestTags.TOTP_TOGGLE,
        )
        BackToForm(onEvent)
    }
}

/**
 * The forced password change (web `ForcedPasswordChangeCard`): why, the new password twice
 * (show/hide through the skin's password field), submit; back to sign-in.
 */
@Composable
public fun ForcedPasswordChangeScreen(state: ForcedPasswordChangeState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    val password = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    var mismatch by rememberSaveable { mutableStateOf(false) }
    val ready = password.text.isNotEmpty() && confirm.text.isNotEmpty()
    val submit = {
        mismatch = password.text.toString() != confirm.text.toString()
        if (ready && !mismatch) onEvent(SignInEvent.SubmitNewPassword(password.text.toString()))
    }
    Column(modifier.fillMaxWidth().testTag(AuthTestTags.FORCED_CHANGE), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
        AuthHeading(stapelString(AuthUiStrings.ForcedChangeTitle))
        AuthHint(stapelString(AuthUiStrings.ForcedChangeHint))
        PasswordPair(
            password, confirm,
            newLabel = stapelString(AuthUiStrings.ForcedChangeNewLabel),
            confirmLabel = stapelString(AuthUiStrings.ForcedChangeConfirmLabel),
            mismatchText = if (mismatch) stapelString(AuthUiStrings.ForcedChangeMismatch) else null,
            serverError = null,
            enabled = !state.submitting,
            onDone = submit,
        )
        FormError(state.error)
        StapelButton(
            stapelString(AuthUiStrings.ForcedChangeSubmit),
            onClick = submit,
            enabled = ready,
            loading = state.submitting,
            fillWidth = true,
            testTag = AuthTestTags.SUBMIT,
        )
        BackToForm(onEvent)
    }
}

/**
 * A lockout or rate limit: what the server said (through the error ladder, with the wait in the
 * sentence when the catalog has it), when to try again (seconds under a minute, else minutes), and
 * the way back.
 */
@Composable
public fun LockedScreen(state: LockedState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(AuthTestTags.LOCKED), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
        AuthHeading(stapelString(AuthUiStrings.LockedTitle))
        val retryIn = state.retryIn
        StapelBanner(tone = StapelTone.Error, message = authErrorText(state.error), testTag = AuthTestTags.FORM_ERROR)
        StapelText(
            when {
                retryIn == null -> stapelString(AuthUiStrings.LockedLater)
                retryIn < 1.minutes -> stapelPlural(AuthUiStrings.LockedSeconds, ceil(retryIn.inWholeMilliseconds / 1000.0).toLong().coerceAtLeast(1))
                else -> stapelPlural(AuthUiStrings.LockedMinutes, ceil(retryIn.inWholeSeconds / 60.0).toLong().coerceAtLeast(1))
            },
            modifier = Modifier.testTag(AuthTestTags.LOCKED_WAIT),
        )
        BackToForm(onEvent)
    }
}

/** An invisible node that announces [text] through a polite live region (no second visual copy). */
@Composable
internal fun PoliteAnnouncement(text: String, modifier: Modifier = Modifier) {
    Box(modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = text })
}
