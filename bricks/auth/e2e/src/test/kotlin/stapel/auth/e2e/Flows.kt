package stapel.auth.e2e

import stapel.auth.error.AuthErrorReason
import stapel.auth.model.OtpChannel
import stapel.auth.model.SignInOutcome
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Signs [device] in through the real OTP presenter with the stand's mock code. */
suspend fun signIn(device: Device, channel: OtpChannel, identifier: String): SignInOutcome.Session {
    val p = OtpSignInPresenter(device.auth, device.context())
    p.dispatch(OtpSignInIntent.RequestCode(channel, identifier))
    var sent = p.state.await { it is OtpSignInState.CodeSent || it is OtpSignInState.RequestFailed || it is OtpSignInState.Locked }
    val limited = (sent as? OtpSignInState.RequestFailed)?.error
    if (limited?.reason == AuthErrorReason.RateLimited) {
        // The same identifier on a second device inside the stand's (short) resend cooldown: wait it out once.
        kotlinx.coroutines.delay((limited.retryAfter ?: 2.seconds).coerceAtMost(5.seconds) + 500.milliseconds)
        p.dispatch(OtpSignInIntent.RequestCode(channel, identifier))
        sent = p.state.await { it is OtpSignInState.CodeSent || it is OtpSignInState.Locked || (it is OtpSignInState.RequestFailed && it.error !== limited) }
    }
    check(sent is OtpSignInState.CodeSent) { "code request failed: $sent" }
    p.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
    val done = p.state.await { it is OtpSignInState.Done || (it is OtpSignInState.CodeSent && it.error != null) }
    check(done is OtpSignInState.Done) { "sign-in failed: $done" }
    return done.outcome
}

suspend fun signInByEmail(device: Device, email: String): SignInOutcome.Session = signIn(device, OtpChannel.Email, email)

suspend fun signInByPhone(device: Device, phone: String): SignInOutcome.Session = signIn(device, OtpChannel.Phone, phone)
