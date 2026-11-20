package stapel.profiles.presentation

import kotlinx.coroutines.flow.collectLatest
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.profiles.StapelProfiles
import stapel.profiles.error.ProfilesError
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.Contacts
import stapel.result.StapelResult
import stapel.result.state.Resource

/**
 * A number waiting for its SMS code.
 *
 * @property contactId the number.
 * @property expiresInSeconds how long the code lives, when the server said.
 * @property confirming the code is being checked.
 */
public data class PendingVerification(val contactId: Int, val expiresInSeconds: Int?, val confirming: Boolean = false)

/**
 * State of [ContactsPresenter].
 *
 * @property contacts the numbers and offered policies, once read.
 * @property loading true until the first read settled.
 * @property loadError why the list could not be read; `RegistrationRequired` = a guest account.
 * @property adding a number is being added.
 * @property busy the contact an action runs on.
 * @property verification the number waiting for its code.
 * @property error why the last action was refused.
 */
public data class ContactsState(
    val contacts: Contacts? = null,
    val loading: Boolean = true,
    val loadError: ProfilesError? = null,
    val adding: Boolean = false,
    val busy: Int? = null,
    val verification: PendingVerification? = null,
    val error: ProfilesError? = null,
)

/** Intents of [ContactsPresenter]. */
public sealed interface ContactsIntent {
    /** Adds a number. */
    public data class Add(
        /** The number as typed. */
        val number: String,
        /** A label; empty = none. */
        val label: String = "",
        /** Who may be handed it; null = the server's default. */
        val policy: ContactPolicy? = null,
    ) : ContactsIntent

    /** Deletes a number. */
    public data class Delete(
        /** The number. */
        val contactId: Int,
    ) : ContactsIntent

    /** Changes who may be handed a number. */
    public data class SetPolicy(
        /** The number. */
        val contactId: Int,
        /** The policy. */
        val policy: ContactPolicy,
    ) : ContactsIntent

    /** Switches a number on or off. */
    public data class SetEnabled(
        /** The number. */
        val contactId: Int,
        /** On or off. */
        val enabled: Boolean,
    ) : ContactsIntent

    /** Sends an SMS code to a number. */
    public data class RequestCode(
        /** The number. */
        val contactId: Int,
    ) : ContactsIntent

    /** Confirms the pending number with [code]. */
    public data class ConfirmCode(
        /** The code from the SMS. */
        val code: String,
    ) : ContactsIntent

    /** Abandons the pending verification. */
    public data object CancelVerification : ContactsIntent
}

/** The person's phone numbers (`profiles.contacts`): list, add, delete, policy, switch, SMS confirmation. */
public class ContactsPresenter(private val profiles: StapelProfiles, context: PresenterContext) :
    Presenter<ContactsState, ContactsIntent, ProfilesEffect>(ContactsState(), context) {

    init {
        launch(OBSERVE) {
            profiles.contacts.contacts().collectLatest { r ->
                when (r) {
                    is Resource.Loading -> Unit
                    is Resource.Ready -> reduce { copy(contacts = r.data, loading = false, loadError = null) }
                    is Resource.Failed -> reduce { copy(loading = false, loadError = ProfilesError.of(r.error)) }
                }
            }
        }
    }

    override suspend fun handle(intent: ContactsIntent) {
        val repo = profiles.contacts
        when (intent) {
            is ContactsIntent.Add -> launch(ADD) {
                reduce { copy(adding = true, error = null) }
                when (val r = repo.add(intent.number.trim(), intent.label.trim(), intent.policy)) {
                    is StapelResult.Ok -> {
                        reduce { copy(adding = false) }
                        emit(ProfilesEffect.Saved)
                    }
                    is StapelResult.Err -> reduce { copy(adding = false, error = r.error) }
                }
            }
            is ContactsIntent.Delete -> act(intent.contactId) { repo.delete(intent.contactId) }
            is ContactsIntent.SetPolicy -> act(intent.contactId) { repo.update(intent.contactId, policy = intent.policy) }
            is ContactsIntent.SetEnabled -> act(intent.contactId) { repo.update(intent.contactId, enabled = intent.enabled) }
            is ContactsIntent.RequestCode -> launch(VERIFY) {
                reduce { copy(busy = intent.contactId, error = null) }
                when (val r = repo.requestCode(intent.contactId)) {
                    is StapelResult.Ok -> reduce { copy(busy = null, verification = PendingVerification(intent.contactId, r.value)) }
                    is StapelResult.Err -> reduce { copy(busy = null, error = r.error) }
                }
            }
            is ContactsIntent.ConfirmCode -> {
                val pending = state.value.verification ?: return
                launch(VERIFY) {
                    reduce { copy(verification = pending.copy(confirming = true), error = null) }
                    when (val r = repo.confirmCode(pending.contactId, intent.code.trim())) {
                        is StapelResult.Ok -> {
                            reduce { copy(verification = null) }
                            emit(ProfilesEffect.Saved)
                        }
                        is StapelResult.Err -> reduce { copy(verification = pending.copy(confirming = false), error = r.error) }
                    }
                }
            }
            ContactsIntent.CancelVerification -> reduce { copy(verification = null, error = null) }
        }
    }

    private fun act(contactId: Int, block: suspend () -> StapelResult<*, ProfilesError>) {
        launch("contact-$contactId") {
            reduce { copy(busy = contactId, error = null) }
            val r = block()
            reduce { copy(busy = null, error = (r as? StapelResult.Err)?.error) }
        }
    }

    private companion object {
        const val OBSERVE = "contacts"
        const val ADD = "add"
        const val VERIFY = "verify"
    }
}
