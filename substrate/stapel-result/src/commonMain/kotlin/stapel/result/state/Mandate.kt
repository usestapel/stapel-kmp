package stapel.result.state

import stapel.result.StapelError

/** Who the app is acting as. */
public enum class MandatePrincipal {
    /** Nobody is signed in. */
    Anonymous,

    /** A guest session: real tokens, anonymous principal. */
    Guest,

    /** A signed-in member. */
    Member,
}

/** Whether the acting principal is known yet. */
public sealed interface MandateState {
    /**
     * The principal is known.
     *
     * @property principal who the app acts as.
     */
    public data class Resolved(public val principal: MandatePrincipal) : MandateState

    /** Still being determined; nothing that depends on the principal may act. */
    public data object Asking : MandateState

    /**
     * Could not be determined.
     *
     * @property error why.
     */
    public data class Unavailable(public val error: StapelError) : MandateState
}
