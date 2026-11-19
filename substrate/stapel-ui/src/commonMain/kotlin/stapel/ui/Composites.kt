package stapel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.i18n.CoreUiStrings
import stapel.result.StapelError
import stapel.result.state.Resource
import stapel.result.text.LocaleTag
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.SnackbarSpec
import kotlin.coroutines.resume

/**
 * An error as a banner: the sentence and detail line come from [LocalStapelErrorText]'s ladder in the
 * current locale; a retry action when [onRetry] is given.
 */
@Composable
public fun ErrorState(
    error: StapelError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val strings = LocalStapelStrings.current
    val locale by strings.locale.collectAsState()
    val text = LocalStapelErrorText.current.describe(error, LocaleTag(locale))
    StapelBanner(
        tone = StapelTone.Error,
        message = text.message,
        modifier = modifier,
        detail = text.detail,
        action = onRetry?.let { ButtonSpec(stapelString(CoreUiStrings.Retry), it, variant = ButtonVariant.Secondary) },
        onDismiss = onDismiss,
        testTag = testTag,
    )
}

/**
 * "Nothing here": reachable only from a load that succeeded (see [LoadList]). A [StatusPage] at
 * [size] (compact by default) with up to two actions.
 */
@Composable
public fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: StapelIcon? = null,
    action: ButtonSpec? = null,
    testTag: String? = null,
    secondaryAction: ButtonSpec? = null,
    size: StatusSize = StatusSize.Compact,
) {
    StatusPage(title, modifier, description, icon, StapelTone.Neutral, action, secondaryAction, null, size, testTag)
}

/** Loading → [loading]; Failed → [ErrorState] (with retry); Ready → [content]. */
@Composable
public fun <T> LoadBoundary(
    state: Resource<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loading: @Composable () -> Unit = { StapelProgress(label = stapelString(CoreUiStrings.Loading)) },
    content: @Composable (T) -> Unit,
) {
    Box(modifier) {
        when (state) {
            is Resource.Loading -> loading()
            is Resource.Failed -> ErrorState(state.error, onRetry = onRetry)
            is Resource.Ready -> content(state.data)
        }
    }
}

/**
 * A loaded list with four distinct arms: loading, failed, empty (only after a successful load) and
 * rows. Rows are laid out in a plain column (it lives inside `StapelPage`'s scroll); a long list
 * belongs in a lazy list of the screen's own.
 */
@Composable
public fun <T> LoadList(
    state: Resource<List<T>>,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    item: @Composable (T) -> Unit,
) {
    Column(modifier) {
        when (state) {
            is Resource.Loading -> StapelProgress(label = stapelString(CoreUiStrings.Loading))
            is Resource.Failed -> ErrorState(state.error, onRetry = onRetry)
            is Resource.Ready -> if (state.data.isEmpty()) empty() else state.data.forEach { item(it) }
        }
    }
}

/** A confirmation dialog: [confirmLabel] runs [onConfirm]; [busy] shows the confirm action loading. */
@Composable
public fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    danger: Boolean = false,
    busy: Boolean = false,
    testTag: String? = null,
) {
    StapelDialog(
        onDismiss = onDismiss,
        dismissLabel = cancelLabel,
        title = title,
        dismissible = !busy,
        testTag = testTag,
        footer = {
            StapelButton(cancelLabel, onDismiss, variant = ButtonVariant.Text, enabled = !busy)
            StapelButton(confirmLabel, onConfirm, variant = if (danger) ButtonVariant.Danger else ButtonVariant.Primary, loading = busy)
        },
    ) {
        message?.let { StapelText(it, color = StapelTheme.colors.textMuted) }
    }
}

/** The queue behind [StapelSnackbarHost]: one message at a time, in order. */
@Stable
public class StapelSnackbarHostState {
    private val mutex = Mutex()
    private var current by mutableStateOf<Pair<SnackbarSpec, CancellableContinuation<Unit>>?>(null)

    /** The message on screen, if any. */
    public val currentSpec: SnackbarSpec? get() = current?.first

    /** Shows [spec] after the messages before it, suspending until it is dismissed or its action runs. */
    public suspend fun show(spec: SnackbarSpec) {
        mutex.withLock {
            try {
                suspendCancellableCoroutine { cont -> current = spec to cont }
            } finally {
                current = null
            }
        }
    }

    /** Dismisses the message on screen. */
    public fun dismissCurrent() {
        current?.second?.let { if (it.isActive) it.resume(Unit) }
    }
}

/** Draws the [state]'s current message through the skin's Snackbar slot. */
@Composable
public fun StapelSnackbarHost(state: StapelSnackbarHostState, modifier: Modifier = Modifier) {
    val spec = state.currentSpec ?: return
    StapelTheme.skin.Snackbar(
        SnackbarSpec(
            message = spec.message,
            tone = spec.tone,
            actionLabel = spec.actionLabel,
            onAction = spec.onAction?.let { action -> { action(); state.dismissCurrent() } },
            onDismiss = { spec.onDismiss?.invoke(); state.dismissCurrent() },
            testTag = spec.testTag,
        ),
        modifier.padding(StapelTheme.spacing.s2),
    )
}
