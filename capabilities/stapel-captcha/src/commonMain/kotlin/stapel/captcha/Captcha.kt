package stapel.captcha

import stapel.network.NetworkPlugin
import stapel.network.Recovery
import stapel.network.RequestContext
import stapel.result.StapelError
import stapel.wire.CaptchaRequirement

/** Why a token is being asked for. */
public enum class CaptchaReason {
    /** Before the first attempt ([CaptchaPlugin] with `proactive = true`). */
    Proactive,

    /** The server answered `error.400.captcha_required`. */
    Required,

    /** The server answered `error.400.captcha_invalid` (the token was rejected or expired). */
    Rejected,
}

/** What the solver is asked to produce a token for. */
public data class CaptchaRequest(
    /** The backend's action name (`@captcha_protected(action=…)`), or `default`. */
    val action: String,
    /** Why. */
    val reason: CaptchaReason,
    /** The operation that needs it. */
    val operationId: String,
)

/**
 * Produces a provider token (Turnstile, hCaptcha, reCAPTCHA…). The provider widget, if any, is the
 * implementation's business: this artifact is headless.
 */
public fun interface CaptchaSolver {
    /** A token, or null when the user abandoned the challenge (the original error is returned). */
    public suspend fun solve(request: CaptchaRequest): String?
}

/** Codes the backend answers when a captcha-protected operation lacks a valid token. */
public object CaptchaCodes {
    /** No token was sent while captcha is active. */
    public const val REQUIRED: String = "error.400.captcha_required"

    /** The token failed verification. */
    public const val INVALID: String = "error.400.captcha_invalid"
}

/**
 * The captcha network plug-in. On [CaptchaCodes.REQUIRED] / [CaptchaCodes.INVALID] it asks the
 * [solver] once and retries once with the token, placed where the operation reads it: the
 * `X-Captcha-Token` header and/or the `captcha_token` body field (from the contract's
 * `x-stapel-captcha`; an operation without that declaration gets both). With [proactive], operations
 * that declare captcha get a token before their first attempt. In prototype posture the backend's
 * verifier is a no-op and this plug-in never fires.
 */
public class CaptchaPlugin(
    private val solver: CaptchaSolver,
    private val proactive: Boolean = false,
) : NetworkPlugin {
    /** `stapel.captcha`. */
    override val id: String = "stapel.captcha"

    /** First of the plug-ins (after authentication and verification). */
    override val order: Int = 300

    /** With `proactive`, attaches a token to the first attempt of an operation that declares captcha. */
    override suspend fun decorate(context: RequestContext) {
        val requirement = context.operation.captcha ?: return
        if (!proactive || context.attempt != 1) return
        val token = solver.solve(CaptchaRequest(requirement.action, CaptchaReason.Proactive, context.operation.id)) ?: return
        attach(context, requirement, token)
    }

    /** On a captcha refusal: one solve, one retry with the token; abandoned = the refusal stands. */
    override suspend fun recover(context: RequestContext, error: StapelError): Recovery {
        val reason = when (error.code.value) {
            CaptchaCodes.REQUIRED -> CaptchaReason.Required
            CaptchaCodes.INVALID -> CaptchaReason.Rejected
            else -> return Recovery.Pass
        }
        val requirement = context.operation.captcha ?: Undeclared
        val token = solver.solve(CaptchaRequest(requirement.action, reason, context.operation.id)) ?: return Recovery.Pass
        return Recovery.Retry { attach(it, requirement, token) }
    }

    private fun attach(context: RequestContext, requirement: CaptchaRequirement, token: String) {
        requirement.header?.let { context.setHeader(it, token) }
        requirement.field?.let { context.setBodyField(it, token) }
    }

    private companion object {
        /** Where the backend looks when the contract did not say: header first, then the body field. */
        val Undeclared = CaptchaRequirement(action = "default", header = "X-Captcha-Token", field = "captcha_token")
    }
}
