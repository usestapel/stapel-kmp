package stapel.runtime

/**
 * The analytics type seam (the port of `@stapel/core`'s analytics types). The implementation —
 * consent, PII guard, batching, collector — is the optional `stapel-analytics` artifact; without
 * it, [Analytics.Noop] is bound.
 */
public interface Analytics {
    /** Records a named event with flat string properties. */
    public fun track(event: String, props: Map<String, String> = emptyMap())

    /** Records a screen view. */
    public fun page(name: String, props: Map<String, String> = emptyMap())

    /** Sets (or clears, with null) the hashed identity of the current user. */
    public fun identify(userHash: String?)

    /** Holder of [Noop]. */
    public companion object {
        /** Records nothing. */
        public val Noop: Analytics = object : Analytics {
            override fun track(event: String, props: Map<String, String>) {}
            override fun page(name: String, props: Map<String, String>) {}
            override fun identify(userHash: String?) {}
        }
    }
}

/** Phase of a flow step. */
public enum class FlowStepPhase(
    /** The wire value (`started`). */
    public val wire: String,
) {
    /** The step began. */
    Started("started"),

    /** The step finished successfully. */
    Completed("completed"),

    /** The step failed. */
    Failed("failed"),
}

/**
 * Emits `flow.<flowId>.<step>` with `phase` plus [props] — the funnel instrumentation flow
 * machines call on every transition (same event shape as the web).
 */
public fun Analytics.trackFlowStep(flowId: String, step: String, phase: FlowStepPhase, props: Map<String, String> = emptyMap()) {
    track("flow.$flowId.$step", mapOf("phase" to phase.wire) + props)
}
