package stapel.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import stapel.result.state.Resource
import stapel.result.state.dataOrNull
import stapel.ui.ConfirmDialog
import stapel.ui.EmptyState
import stapel.ui.ErrorState
import stapel.ui.LoadList
import stapel.ui.PaneMeasure
import stapel.ui.StapelButton
import stapel.ui.StapelDialog
import stapel.ui.StapelIconButton
import stapel.ui.StapelIconIds
import stapel.ui.StapelIconView
import stapel.ui.StapelListItem
import stapel.ui.StapelPage
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.PullToRefreshSpec
import stapel.ui.stapelString

/** The guest wall: a guest reached something that needs an account. Sign in, create one, or not now. */
@Composable
public fun GuestUpgradeScreen(state: GuestUpgradeState, onEvent: (GuestUpgradeEvent) -> Unit, modifier: Modifier = Modifier) {
    StapelPage(modifier.testTag(AuthTestTags.GUEST_WALL), measure = PaneMeasure.Narrow) {
        AuthCard {
            AuthHeading(stapelString(AuthUiStrings.GuestTitle))
            StapelText(stapelString(AuthUiStrings.GuestBody), color = StapelTheme.colors.textMuted)
            StapelButton(stapelString(AuthUiStrings.GuestSignIn), { onEvent(GuestUpgradeEvent.SignIn) }, fillWidth = true)
            StapelButton(
                stapelString(AuthUiStrings.GuestRegister),
                { onEvent(GuestUpgradeEvent.Register) },
                variant = ButtonVariant.Secondary,
                fillWidth = true,
            )
            if (state.canDismiss) {
                StapelButton(
                    stapelString(AuthUiStrings.GuestNotNow),
                    { onEvent(GuestUpgradeEvent.NotNow) },
                    variant = ButtonVariant.Text,
                    fillWidth = true,
                )
            }
        }
    }
}

/**
 * Sessions and devices (web `SessionsList`): every session with its device, last use, "this device"
 * and "unrecognized" markers; revoke one (confirmed), "this was me" on a suspicious one, sign out
 * everyone else (confirmed), pull to refresh (and a refresh action for pointer input).
 */
@Composable
public fun SessionsScreen(state: SessionsState, onEvent: (SessionsEvent) -> Unit, modifier: Modifier = Modifier) {
    // No top bar of its own: the destination's chrome (shell or host) names it and owns "back"; a
    // single-line bar title would truncate at large text sizes, the body heading wraps.
    val refreshLabel = stapelString(AuthUiStrings.SessionsRefresh)
    StapelPage(
        modifier.testTag(AuthTestTags.SESSIONS),
        measure = PaneMeasure.Reading,
        scrollable = false,
    ) {
        val refreshing = (state.sessions as? Resource.Ready)?.freshness?.refreshing == true
        StapelTheme.skin.PullToRefresh(PullToRefreshSpec(refreshing, { onEvent(SessionsEvent.Refresh) }, refreshLabel), Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3),
            ) {
                AuthHeading(stapelString(AuthUiStrings.SessionsTitle))
                AuthHint(stapelString(AuthUiStrings.SessionsSubtitle))
                // Pull-to-refresh is the touch gesture; this is the same action for pointer and assistive input.
                StapelButton(refreshLabel, { onEvent(SessionsEvent.Refresh) }, variant = ButtonVariant.Text, leadingIcon = StapelIconIds.Refresh)
                state.actionError?.let { ErrorState(it, onDismiss = { onEvent(SessionsEvent.DismissError) }, testTag = AuthTestTags.FORM_ERROR) }
                val authSkin = StapelTheme.skin.extension(AuthSkinKey)
                LoadList(
                    state.sessions,
                    empty = { EmptyState(stapelString(AuthUiStrings.SessionsEmpty), description = stapelString(AuthUiStrings.SessionsEmptyHint)) },
                    onRetry = { onEvent(SessionsEvent.Refresh) },
                ) { row ->
                    val actions = SessionRowActions(
                        lastUsed = stapelString(AuthUiStrings.SessionsLastUsed, "when" to relativeTime(row.lastUsedAt, state.now)),
                        busy = row.id in state.busy,
                        onRevoke = if (row.isCurrent) null else ({ onEvent(SessionsEvent.AskRevoke(row.id)) }),
                        onConfirmMine = if (row.isSuspicious) ({ onEvent(SessionsEvent.ConfirmMine(row.id)) }) else null,
                    )
                    authSkin.SessionRow(row, actions, Modifier.fillMaxWidth().testTag("${AuthTestTags.SESSION_ROW}.${row.id}"))
                }
                val others = state.sessions.dataOrNull().orEmpty().count { !it.isCurrent }
                if (others > 0) {
                    StapelButton(
                        stapelString(AuthUiStrings.SessionsSignOutAll),
                        { onEvent(SessionsEvent.AskRevokeOthers) },
                        variant = ButtonVariant.Danger,
                        loading = state.revokingOthers,
                        fillWidth = true,
                        testTag = AuthTestTags.SIGN_OUT_OTHERS,
                    )
                }
            }
        }
    }
    val cancel = stapelString(AuthUiStrings.Cancel)
    if (state.confirmRevoke != null) {
        ConfirmDialog(
            title = stapelString(AuthUiStrings.SessionsSignOutConfirmTitle),
            confirmLabel = stapelString(AuthUiStrings.SessionsSignOut),
            cancelLabel = cancel,
            onConfirm = { onEvent(SessionsEvent.ConfirmRevoke) },
            onDismiss = { onEvent(SessionsEvent.CancelRevoke) },
            danger = true,
            busy = state.confirmRevoke in state.busy,
            testTag = AuthTestTags.REVOKE_CONFIRM,
        )
    } else if (state.confirmRevokeOthers) {
        ConfirmDialog(
            title = stapelString(AuthUiStrings.SessionsSignOutAllConfirmTitle),
            confirmLabel = stapelString(AuthUiStrings.SessionsSignOutAll),
            cancelLabel = cancel,
            onConfirm = { onEvent(SessionsEvent.ConfirmRevoke) },
            onDismiss = { onEvent(SessionsEvent.CancelRevoke) },
            danger = true,
            busy = state.revokingOthers,
            testTag = AuthTestTags.REVOKE_CONFIRM,
        )
    }
}

/** The default session row: device icon, name, details, markers, and the row's actions. */
@Composable
internal fun DefaultSessionRow(row: SessionRowState, actions: SessionRowActions, modifier: Modifier) {
    val markers = buildList {
        if (row.isCurrent) add(stapelString(AuthUiStrings.SessionThisDevice))
        if (row.isSuspicious) add(stapelString(AuthUiStrings.SessionSuspicious))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
        StapelListItem(
            headline = row.name,
            supporting = listOfNotNull(row.details.takeIf { it.isNotBlank() }, row.ipAddress, actions.lastUsed).joinToString(" · "),
            overline = markers.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            leading = { StapelIconView(StapelIconIds.Device, null, tint = if (row.isSuspicious) StapelTheme.colors.warning else StapelTheme.colors.textMuted) },
        )
        if (actions.onRevoke != null || actions.onConfirmMine != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                actions.onConfirmMine?.let {
                    StapelButton(stapelString(AuthUiStrings.SessionsConfirmMe), it, variant = ButtonVariant.Text, enabled = !actions.busy)
                }
                actions.onRevoke?.let {
                    StapelButton(stapelString(AuthUiStrings.SessionsSignOut), it, variant = ButtonVariant.Text, loading = actions.busy)
                }
            }
        }
    }
}

/** The sign-out confirmation (a dialog; a guest is told what signing out loses). */
@Composable
public fun SignOutConfirmDialog(state: SignOutConfirmState, onEvent: (SignOutEvent) -> Unit) {
    ConfirmDialog(
        title = stapelString(AuthUiStrings.SignOutConfirmTitle),
        message = stapelString(if (state.isGuest) AuthUiStrings.SignOutGuestBody else AuthUiStrings.SignOutConfirmBody),
        confirmLabel = stapelString(AuthUiStrings.AccountMenuSignOut),
        cancelLabel = stapelString(AuthUiStrings.Cancel),
        onConfirm = { onEvent(SignOutEvent.Confirm) },
        onDismiss = { onEvent(SignOutEvent.Cancel) },
        danger = state.isGuest,
        busy = state.busy,
        testTag = AuthTestTags.SIGN_OUT_CONFIRM,
    )
}

/**
 * The account control for the top bar (web `AccountMenu`, embed `auth.account_menu` in
 * `topbar.end`): a button named "Account menu, signed in as …" opening a sheet with the identity,
 * "Account" (when [onOpen] is given) and "Sign out", which asks for confirmation.
 *
 * @param identity what the user is known as (e-mail, phone or username).
 * @param onSignOut runs the sign-out after the confirmation.
 */
@Composable
public fun AccountMenu(
    identity: String,
    isGuest: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    signingOut: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    StapelIconButton(
        StapelIconIds.User,
        stapelString(AuthUiStrings.AccountMenuLabelNamed, "name" to identity),
        { open = true },
        modifier,
        testTag = AuthTestTags.ACCOUNT_MENU,
    )
    if (open) {
        StapelDialog(onDismiss = { open = false }, dismissLabel = stapelString(AuthUiStrings.Close), title = identity) {
            Column(verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                onOpen?.let {
                    StapelButton(stapelString(AuthUiStrings.AccountMenuOpen), {
                        open = false
                        it()
                    }, variant = ButtonVariant.Secondary, fillWidth = true)
                }
                StapelButton(
                    stapelString(AuthUiStrings.AccountMenuSignOut),
                    {
                        open = false
                        confirm = true
                    },
                    variant = ButtonVariant.Secondary,
                    leadingIcon = StapelIconIds.LogOut,
                    fillWidth = true,
                    testTag = AuthTestTags.ACCOUNT_MENU_SIGN_OUT,
                )
            }
        }
    }
    if (confirm || signingOut) {
        LocalAuthScreens.current.SignOutConfirm(SignOutConfirmState(isGuest = isGuest, busy = signingOut)) { event ->
            confirm = false
            if (event == SignOutEvent.Confirm) onSignOut()
        }
    }
}
