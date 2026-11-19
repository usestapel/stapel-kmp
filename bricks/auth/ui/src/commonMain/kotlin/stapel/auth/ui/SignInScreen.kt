package stapel.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import stapel.result.StapelError
import stapel.ui.ErrorState
import stapel.ui.PaneMeasure
import stapel.ui.StapelBanner
import stapel.ui.StapelButton
import stapel.ui.StapelDialog
import stapel.ui.StapelDivider
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.TabsSpec
import stapel.ui.skin.TextFieldKind
import stapel.ui.stapelString

/**
 * The sign-in screen (web `AuthPanel`): title and notice, the main methods as tabs (or a lone form),
 * the selected method's form or the current step, the alternative methods, and the footer (guest
 * entry, register switch, legal slot). Stateless: [state] in, [onEvent] out; only typed text lives here.
 */
@Composable
public fun SignInScreen(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
    modifier: Modifier = Modifier,
    slots: AuthSlots = AuthSlots(),
) {
    val screens = LocalAuthScreens.current
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    StapelPage(modifier.testTag(AuthTestTags.SIGN_IN), measure = PaneMeasure.Narrow) {
        AuthCard {
            slots.brand?.invoke()
            AuthHeading(
                stapelString(if (state.variant == SignInVariant.Register) AuthUiStrings.RegisterTitle else AuthUiStrings.LoginTitle),
                testTag = AuthTestTags.TITLE,
            )
            state.notice?.let { notice ->
                StapelBanner(
                    tone = if (notice == AuthNotice.SignedOut) StapelTone.Info else StapelTone.Warning,
                    message = stapelString(notice.textKey()),
                    onDismiss = { onEvent(SignInEvent.DismissNotice) },
                    testTag = AuthTestTags.NOTICE,
                )
            }
            val method = state.activeMethod
            when {
                state.loading -> StapelProgress(label = stapelString(stapel.i18n.CoreUiStrings.Loading))
                state.loadError != null -> ErrorState(state.loadError, onRetry = { onEvent(SignInEvent.Retry) })
                method == null -> StapelBanner(StapelTone.Error, stapelString(AuthUiStrings.NoMethods))
                else -> Step(state, method, onEvent, screens)
            }
            if (state.step is SignInStep.Form) slots.belowForm?.invoke()
            if (!state.loading && state.loadError == null && state.step is SignInStep.Form) {
                Alternatives(state, method, onEvent, onMore = { moreOpen = true })
                Footer(state, onEvent, slots)
            }
        }
    }
    if (moreOpen) {
        StapelDialog(
            onDismiss = { moreOpen = false },
            dismissLabel = stapelString(AuthUiStrings.Close),
            title = stapelString(AuthUiStrings.MoreMethods),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                val authSkin = StapelTheme.skin.extension(AuthSkinKey)
                for (m in state.zones.overflow) {
                    authSkin.MethodButton(m, stapelString(m.labelKey()), {
                        moreOpen = false
                        onEvent(SignInEvent.SelectMethod(m))
                    }, Modifier.fillMaxWidth().testTag("${AuthTestTags.METHOD_BUTTON}.${m.name.lowercase()}"))
                }
            }
        }
    }
}

@Composable
private fun Step(state: SignInState, method: SignInMethod, onEvent: (SignInEvent) -> Unit, screens: AuthScreens) {
    when (val step = state.step) {
        is SignInStep.Form -> {
            val main = state.zones.main
            if (main.size > 1 && method in main) {
                StapelTheme.skin.Tabs(
                    TabsSpec(
                        items = main.map { stapelString(it.labelKey()) },
                        selectedIndex = main.indexOf(method),
                        onSelect = { onEvent(SignInEvent.SelectMethod(main[it])) },
                        testTag = AuthTestTags.METHOD_TABS,
                    ),
                    Modifier.fillMaxWidth(),
                )
            }
            key(method, state.variant) {
                when {
                    method == SignInMethod.Password && state.variant == SignInVariant.Register -> RegisterForm(step, onEvent)
                    method == SignInMethod.Password -> PasswordForm(step, onEvent)
                    else -> IdentifierForm(method, step, onEvent)
                }
            }
        }
        is SignInStep.Code -> screens.OtpCode(step.state, onEvent)
        is SignInStep.Totp -> screens.TotpChallenge(step.state, onEvent)
        is SignInStep.ForcedPasswordChange -> screens.ForcedPasswordChange(step.state, onEvent)
        is SignInStep.Locked -> screens.Locked(step.state, onEvent)
        SignInStep.MfaEnrollRequired -> {
            StapelBanner(StapelTone.Info, stapelString(AuthUiStrings.MfaEnrollOnWeb))
            BackToForm(onEvent)
        }
        SignInStep.Done -> StapelProgress(label = stapelString(AuthUiStrings.VerificationSuccess))
    }
}

@Composable
private fun IdentifierForm(method: SignInMethod, step: SignInStep.Form, onEvent: (SignInEvent) -> Unit) {
    val value = rememberTextFieldState(step.identifier)
    val submit = { if (value.text.isNotBlank()) onEvent(SignInEvent.RequestCode(method, value.text.toString().trim())) }
    val email = method == SignInMethod.Email
    val label = stapelString(if (email) AuthUiStrings.EmailLabel else AuthUiStrings.PhoneLabel)
    StapelTextField(
        value,
        label = label,
        kind = if (email) TextFieldKind.Email else TextFieldKind.Phone,
        placeholder = stapelString(if (email) AuthUiStrings.EmailPlaceholder else AuthUiStrings.PhonePlaceholder),
        error = (step.fieldErrors[if (email) "email" else "phone"] ?: step.error)?.let { authErrorText(it) },
        enabled = !step.busy,
        imeAction = ImeAction.Send,
        onImeAction = submit,
        autofill = if (email) ContentType.EmailAddress else ContentType.PhoneNumber,
        testTag = AuthTestTags.IDENTIFIER,
    )
    StapelButton(
        stapelString(AuthUiStrings.SendCode),
        onClick = submit,
        enabled = value.text.isNotBlank(),
        loading = step.busy,
        fillWidth = true,
        testTag = AuthTestTags.SEND_CODE,
    )
}

@Composable
private fun PasswordForm(step: SignInStep.Form, onEvent: (SignInEvent) -> Unit) {
    val login = rememberTextFieldState(step.identifier)
    val password = rememberTextFieldState()
    val ready = login.text.isNotBlank() && password.text.isNotEmpty()
    val submit = { if (ready) onEvent(SignInEvent.SignInWithPassword(login.text.toString().trim(), password.text.toString())) }
    StapelTextField(
        login,
        label = stapelString(AuthUiStrings.EmailLabel),
        kind = TextFieldKind.Email,
        placeholder = stapelString(AuthUiStrings.EmailPlaceholder),
        enabled = !step.busy,
        imeAction = ImeAction.Next,
        autofill = ContentType.Username,
        testTag = AuthTestTags.LOGIN,
    )
    StapelTextField(
        password,
        label = stapelString(AuthUiStrings.PasswordLabel),
        kind = TextFieldKind.Password,
        placeholder = stapelString(AuthUiStrings.PasswordPlaceholder),
        enabled = !step.busy,
        imeAction = ImeAction.Done,
        onImeAction = submit,
        autofill = ContentType.Password,
        testTag = AuthTestTags.PASSWORD,
    )
    FormError(step.error)
    StapelButton(
        stapelString(AuthUiStrings.Submit),
        onClick = submit,
        enabled = ready,
        loading = step.busy,
        fillWidth = true,
        testTag = AuthTestTags.SUBMIT,
    )
}

@Composable
private fun RegisterForm(step: SignInStep.Form, onEvent: (SignInEvent) -> Unit) {
    val email = rememberTextFieldState(step.identifier)
    val password = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    var mismatch by rememberSaveable { mutableStateOf(false) }
    val ready = email.text.isNotBlank() && password.text.isNotEmpty() && confirm.text.isNotEmpty()
    val submit = {
        mismatch = password.text.toString() != confirm.text.toString()
        if (ready && !mismatch) onEvent(SignInEvent.RegisterWithPassword(email.text.toString().trim(), password.text.toString()))
    }
    StapelTextField(
        email,
        label = stapelString(AuthUiStrings.EmailLabel),
        kind = TextFieldKind.Email,
        placeholder = stapelString(AuthUiStrings.EmailPlaceholder),
        error = step.fieldErrors["email"]?.let { authErrorText(it) },
        enabled = !step.busy,
        imeAction = ImeAction.Next,
        autofill = ContentType.EmailAddress,
        testTag = AuthTestTags.LOGIN,
    )
    PasswordPair(
        password, confirm,
        newLabel = stapelString(AuthUiStrings.PasswordLabel),
        confirmLabel = stapelString(AuthUiStrings.RegisterConfirmLabel),
        mismatchText = if (mismatch) stapelString(AuthUiStrings.RegisterMismatch) else null,
        serverError = step.fieldErrors["password"],
        enabled = !step.busy,
        onDone = submit,
    )
    FormError(step.error)
    StapelButton(
        stapelString(AuthUiStrings.RegisterSubmit),
        onClick = submit,
        enabled = ready,
        loading = step.busy,
        fillWidth = true,
        testTag = AuthTestTags.SUBMIT,
    )
}

/** A new password and its repetition (registration, forced change). */
@Composable
internal fun PasswordPair(
    password: TextFieldState,
    confirm: TextFieldState,
    newLabel: String,
    confirmLabel: String,
    mismatchText: String?,
    serverError: StapelError?,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    StapelTextField(
        password,
        label = newLabel,
        kind = TextFieldKind.Password,
        error = serverError?.let { authErrorText(it) },
        enabled = enabled,
        imeAction = ImeAction.Next,
        autofill = ContentType.NewPassword,
        testTag = AuthTestTags.PASSWORD,
    )
    StapelTextField(
        confirm,
        label = confirmLabel,
        kind = TextFieldKind.Password,
        error = mismatchText,
        enabled = enabled,
        imeAction = ImeAction.Done,
        onImeAction = onDone,
        autofill = ContentType.NewPassword,
        testTag = AuthTestTags.PASSWORD_CONFIRM,
    )
}

/** The form-level error under a form (announced politely by the skin's banner). */
@Composable
internal fun FormError(error: StapelError?) {
    if (error != null) ErrorState(error, testTag = AuthTestTags.FORM_ERROR)
}

/** "Back to sign in". */
@Composable
internal fun BackToForm(onEvent: (SignInEvent) -> Unit) {
    StapelButton(
        stapelString(AuthUiStrings.BackToSignIn),
        onClick = { onEvent(SignInEvent.BackToForm) },
        variant = ButtonVariant.Text,
        fillWidth = true,
        testTag = AuthTestTags.BACK_TO_FORM,
    )
}

@Composable
private fun Alternatives(state: SignInState, active: SignInMethod?, onEvent: (SignInEvent) -> Unit, onMore: () -> Unit) {
    val bottom = state.zones.bottom
    val overflow = state.zones.overflow
    // A method picked from the bottom row or the overflow list is shown as the form; the way back to
    // the main methods is their tabs, so the tab strip stays reachable from the bottom row too.
    val offMain = active != null && active !in state.zones.main && state.zones.main.isNotEmpty()
    if (bottom.isEmpty() && overflow.isEmpty() && !offMain) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
        OrDivider()
        val authSkin = StapelTheme.skin.extension(AuthSkinKey)
        val rows = (if (offMain) state.zones.main.take(1) else emptyList()) + bottom.filter { it != active }
        for (m in rows) {
            authSkin.MethodButton(m, stapelString(m.labelKey()), { onEvent(SignInEvent.SelectMethod(m)) },
                Modifier.fillMaxWidth().testTag("${AuthTestTags.METHOD_BUTTON}.${m.name.lowercase()}"))
        }
        if (overflow.any { it != active }) {
            StapelButton(
                stapelString(AuthUiStrings.MoreMethods),
                onClick = onMore,
                variant = ButtonVariant.Text,
                fillWidth = true,
                testTag = AuthTestTags.MORE_METHODS,
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
        StapelDivider()
        StapelText(stapelString(AuthUiStrings.Or), style = StapelTheme.type.caption, color = StapelTheme.colors.textMuted)
    }
}

@Composable
private fun Footer(state: SignInState, onEvent: (SignInEvent) -> Unit, slots: AuthSlots) {
    val showSwitch = state.variant == SignInVariant.Register || state.canRegister
    val guest = state.guest?.takeIf { state.variant == SignInVariant.Login }
    if (!showSwitch && guest == null && slots.footer == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
        StapelDivider()
        if (guest != null) {
            StapelButton(
                stapelString(if (guest.creating) AuthUiStrings.ContinueAsGuestPending else AuthUiStrings.ContinueAsGuest),
                onClick = { onEvent(SignInEvent.ContinueAsGuest) },
                variant = ButtonVariant.Text,
                loading = guest.creating,
                fillWidth = true,
                testTag = AuthTestTags.GUEST,
            )
            AuthHint(stapelString(AuthUiStrings.ContinueAsGuestHint))
            guest.error?.let { ErrorState(it) }
        }
        if (showSwitch) {
            val toRegister = state.variant == SignInVariant.Login
            StapelButton(
                stapelString(if (toRegister) AuthUiStrings.SwitchToRegister else AuthUiStrings.SwitchToLogin),
                onClick = { onEvent(SignInEvent.SwitchVariant(if (toRegister) SignInVariant.Register else SignInVariant.Login)) },
                variant = ButtonVariant.Text,
                fillWidth = true,
                testTag = AuthTestTags.VARIANT_SWITCH,
            )
        }
        slots.footer?.invoke()
    }
}

/** The default method button: a full-width secondary button. */
@Composable
internal fun DefaultMethodButton(method: SignInMethod, label: String, onClick: () -> Unit, modifier: Modifier) {
    val icon = when (method) {
        SignInMethod.Email -> stapel.ui.StapelIconIds.Mail
        SignInMethod.Phone -> stapel.ui.StapelIconIds.Phone
        SignInMethod.Password -> stapel.ui.StapelIconIds.Lock
    }
    StapelButton(label, onClick, modifier, variant = ButtonVariant.Secondary, leadingIcon = icon, fillWidth = true)
}
