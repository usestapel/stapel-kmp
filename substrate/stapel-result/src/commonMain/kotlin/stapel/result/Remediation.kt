package stapel.result

/**
 * What a person (or the app) can do about an error: the backend's finite `REMEDIATION_VOCAB`,
 * plus [Unknown] for a value a newer backend adds.
 *
 * @property wire the vocabulary word on the wire (`wait_and_retry`); empty for [Unknown].
 */
public enum class Remediation(public val wire: String) {
    /** Try the same thing again. */
    Retry("retry"),

    /** Wait (see `retry_after*` params) and try again. */
    WaitAndRetry("wait_and_retry"),

    /** Sign in again. */
    Reauthenticate("reauthenticate"),

    /** Complete a step-up verification. */
    Verify("verify"),

    /** Change the input (highlight the field named by `params.field`). */
    FixInput("fix_input"),

    /** Nothing the person can do; escalate to support. */
    ContactSupport("contact_support"),

    /** A defect in the client or the server. */
    Bug("bug"),

    /** A vocabulary word this client does not know. */
    Unknown(""),
    ;

    /** Holder of [fromWire] and [default]. */
    public companion object {
        /** The value for a wire word; [Unknown] for anything outside the vocabulary. */
        public fun fromWire(wire: String): Remediation =
            entries.firstOrNull { it != Unknown && it.wire == wire } ?: Unknown

        private val VERIFY = Regex("verification|step_up|enrollment")
        private val RETRYABLE_400 = Regex(
            "expired|challenge|not_pending|qr_(expired|fulfilled|not_found)|magic_link_invalid|code_required",
        )

        /**
         * The backend's heuristic for a code with no declared remediation, ported exactly from
         * `stapel_core.django.api.errors.default_remediation` so both sides agree.
         *
         * @param code the error code (`error.<status>.<name>`).
         * @param status its HTTP status.
         * @param paramNames the names of the code's params.
         */
        public fun default(code: String, status: Int, paramNames: Collection<String>): Remediation {
            val name = code.split('.').drop(2).joinToString(".")
            return when {
                VERIFY.containsMatchIn(name) -> Verify
                paramNames.any { it.startsWith("retry_after") } || status in setOf(422, 423, 429) -> WaitAndRetry
                "sso_required" in name -> Reauthenticate
                status == 401 -> Reauthenticate
                status == 500 -> ContactSupport
                status == 409 -> FixInput
                status == 404 -> if ("not_found" in name) Retry else FixInput
                status == 403 -> Retry
                status == 400 -> if (RETRYABLE_400.containsMatchIn(name)) Retry else FixInput
                else -> Retry
            }
        }
    }
}
