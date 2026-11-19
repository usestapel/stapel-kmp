package stapel.result.state

import stapel.result.CoreErrorCodes
import stapel.result.ErrorCode
import stapel.result.ErrorParams
import stapel.result.StapelError

/**
 * Whether an action (a button, a menu item) can run now. A block always carries a reason code —
 * there is no "disabled, reason unknown".
 */
public sealed interface ActionAvailability {
    /** The action can run. */
    public data object Available : ActionAvailability

    /**
     * The action cannot run.
     *
     * @property code the reason a person reads, rendered through the error-text catalogs.
     * @property params placeholders of [code]'s template.
     * @property cause the failure behind the block, when there is one.
     */
    public data class Blocked(
        public val code: ErrorCode,
        public val params: ErrorParams = ErrorParams.Empty,
        public val cause: StapelError? = null,
    ) : ActionAvailability
}

/** The first [ActionAvailability.Blocked] among [gates], or [ActionAvailability.Available]. */
public fun firstBlock(vararg gates: ActionAvailability): ActionAvailability =
    gates.firstOrNull { it is ActionAvailability.Blocked } ?: ActionAvailability.Available

/**
 * Blocks while [state] is loading (`stapel.action.blocked.loading`) or failed
 * (`stapel.action.blocked.load_failed`, with the failure as cause); when ready, [whenReady] decides.
 */
public inline fun <T> requireReady(
    state: Resource<T>,
    whenReady: (T) -> ActionAvailability = { ActionAvailability.Available },
): ActionAvailability = when (state) {
    is Resource.Loading -> ActionAvailability.Blocked(CoreErrorCodes.ActionBlockedLoading)
    is Resource.Failed -> ActionAvailability.Blocked(CoreErrorCodes.ActionBlockedLoadFailed, cause = state.error)
    is Resource.Ready -> whenReady(state.data)
}
