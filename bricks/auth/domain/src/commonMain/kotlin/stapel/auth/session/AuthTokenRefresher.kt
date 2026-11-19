package stapel.auth.session

import stapel.auth.api.AuthApi
import stapel.auth.api.model.TokenRefreshRequest
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportCarrier
import stapel.session.RefreshOutcome
import stapel.session.SessionEnd
import stapel.session.TokenPair
import stapel.session.TokenRefresher

/**
 * The `stapel-session` refresh seam: `POST /token/refresh/ {"refresh": …}` over [refreshApi], which
 * must be built on the BARE transport (no authenticator, so a refresh never re-enters refresh).
 *
 * | Answer | Outcome |
 * |---|---|
 * | 200 | [RefreshOutcome.Rotated] (refresh rotates on every call) |
 * | `error.401.refresh_revoked`, `error.401.token_revoked` | [RefreshOutcome.Rejected] [SessionEnd.Revoked] |
 * | `error.401.refresh_invalid`, `error.401.refresh_not_provided`, `error.401.user_not_found` | Rejected [SessionEnd.Expired] |
 * | `error.401.account_disabled` | Rejected [SessionEnd.AccountDisabled] |
 * | any other 401 | Rejected [SessionEnd.Unknown] |
 * | no answer, timeout, 5xx, 429, any other status | [RefreshOutcome.Transient]: an unmeasured refresh never signs anybody out |
 */
public class AuthTokenRefresher(private val refreshApi: AuthApi) : TokenRefresher {
    /** Exchanges [current]'s refresh token for a new pair and classifies a refusal per the table above. */
    override suspend fun refresh(current: TokenPair): RefreshOutcome =
        when (val answer = refreshApi.createTokenRefresh(TokenRefreshRequest(refresh = current.refreshToken))) {
            is StapelResult.Ok -> RefreshOutcome.Rotated(TokenPair(answer.value.access, answer.value.refresh))
            is StapelResult.Err -> classifyRefreshFailure(answer.error)
        }
}

/** The table of [AuthTokenRefresher], exposed for the session's own diagnostics and tests. */
internal fun classifyRefreshFailure(error: StapelError): RefreshOutcome {
    val envelope = error.envelope ?: (error as? TransportCarrier)?.failure?.envelope
    val status = envelope?.status ?: error.status
    val code = envelope?.code?.value ?: error.code.value
    if (status != 401) return RefreshOutcome.Transient(code)
    val cause = when (code) {
        "error.401.refresh_revoked", "error.401.token_revoked" -> SessionEnd.Revoked
        "error.401.refresh_invalid", "error.401.refresh_not_provided", "error.401.user_not_found" -> SessionEnd.Expired
        "error.401.account_disabled" -> SessionEnd.AccountDisabled
        else -> SessionEnd.Unknown
    }
    return RefreshOutcome.Rejected(cause, code)
}
