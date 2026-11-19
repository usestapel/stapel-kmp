package stapel.auth.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.auth.api.AuthApi
import stapel.auth.api.model.VerificationComplete
import stapel.auth.api.model.VerificationInitiate
import stapel.auth.error.AuthError
import stapel.auth.model.TotpProof
import stapel.network.VerificationHandler
import stapel.network.VerificationOutcome
import stapel.result.StapelResult
import stapel.result.VerificationChallenge

/** Where a step-up verification stands. */
public sealed interface StepUpState {
    /** Nothing is waiting for verification. */
    public data object Idle : StepUpState

    /** A request is parked; the user picks a factor. */
    public data class Picking(
        /** The server's challenge (factors, scope). */
        val challenge: VerificationChallenge,
        /** The operation that raised it. */
        val operationId: String,
        /** Error of the previous attempt, if any. */
        val error: AuthError?,
    ) : StepUpState

    /** A factor was initiated (a code may have been sent); waiting for the proof. */
    public data class AwaitingCode(
        /** The server's challenge. */
        val challenge: VerificationChallenge,
        /** The chosen factor. */
        val factor: String,
        /** True while the proof is being checked. */
        val submitting: Boolean,
        /** Error of the previous attempt, if any. */
        val error: AuthError?,
    ) : StepUpState
}

/**
 * Step-up verification (`auth.step_up_verification`): the [VerificationHandler] the authenticated
 * transport calls on a `403 error.403.verification_required`. It parks the request, publishes
 * [state] for a UI (or a headless host), and resumes the request with the grant once the user
 * proved a factor; [cancel] fails the parked request with the original 403.
 *
 * v0 covers code factors (`totp`, `email_otp`, `sms_otp`, backup codes); passkey factors need the
 * passkey adapter (a later slice) and are offered by the server only to clients that ask for them.
 *
 * @param api the authenticated client, read lazily (the transport that calls this handler is the one
 *   the client is built on).
 */
public class StepUpController(private val api: () -> AuthApi) : VerificationHandler {
    private val mutable = MutableStateFlow<StepUpState>(StepUpState.Idle)
    private val serial = Mutex()
    private var pending: CompletableDeferred<VerificationOutcome>? = null

    /** Current step. */
    public val state: StateFlow<StepUpState> = mutable.asStateFlow()

    /** Parks the calling request until the user verifies or [cancel]s; one challenge at a time. */
    override suspend fun resolve(challenge: VerificationChallenge, operationId: String): VerificationOutcome =
        serial.withLock {
            val waiter = CompletableDeferred<VerificationOutcome>()
            pending = waiter
            mutable.value = StepUpState.Picking(challenge, operationId, null)
            try {
                waiter.await()
            } finally {
                pending = null
                mutable.value = StepUpState.Idle
            }
        }

    /** Starts [factor] (one of the challenge's factors); the server may send a code. */
    public suspend fun chooseFactor(factor: String): StapelResult<Unit, AuthError> {
        val picking = mutable.value as? StepUpState.Picking ?: return StapelResult.Ok(Unit)
        return when (val r = api().verificationInitiate(picking.challenge.challengeId, VerificationInitiate(factor))) {
            is StapelResult.Ok -> {
                mutable.value = StepUpState.AwaitingCode(picking.challenge, factor, submitting = false, error = null)
                StapelResult.Ok(Unit)
            }
            is StapelResult.Err -> {
                val error = AuthError.of(r.error)
                mutable.value = picking.copy(error = error)
                StapelResult.Err(error)
            }
        }
    }

    /** Submits the proof for the chosen factor; on success the parked request is retried with the grant. */
    public suspend fun submit(proof: TotpProof): StapelResult<Unit, AuthError> {
        val awaiting = mutable.value as? StepUpState.AwaitingCode ?: return StapelResult.Ok(Unit)
        mutable.value = awaiting.copy(submitting = true, error = null)
        val body = when (proof) {
            is TotpProof.Code -> VerificationComplete(factor = awaiting.factor, code = proof.value)
            is TotpProof.BackupCode -> VerificationComplete(factor = awaiting.factor, backupCode = proof.value)
        }
        return when (val r = api().verificationComplete(awaiting.challenge.challengeId, body)) {
            is StapelResult.Ok -> {
                pending?.complete(VerificationOutcome.Verified(r.value.verificationToken))
                StapelResult.Ok(Unit)
            }
            is StapelResult.Err -> {
                val error = AuthError.of(r.error)
                mutable.value = awaiting.copy(submitting = false, error = error)
                StapelResult.Err(error)
            }
        }
    }

    /** Declines: the parked request returns its 403. */
    public fun cancel() {
        pending?.complete(VerificationOutcome.Declined)
    }
}
