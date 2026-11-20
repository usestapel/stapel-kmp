package stapel.profiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import stapel.i18n.StringKey
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.Contact
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.maskPhoneNumber
import stapel.profiles.presentation.ContactsIntent
import stapel.profiles.presentation.ContactsState
import stapel.ui.ConfirmDialog
import stapel.ui.EmptyState
import stapel.ui.ErrorState
import stapel.ui.PaneMeasure
import stapel.ui.SectionHeader
import stapel.ui.StapelButton
import stapel.ui.StapelChipGroup
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelSurface
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelToggle
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ChipSpec
import stapel.ui.skin.TextFieldKind
import stapel.ui.skin.ToggleKind
import stapel.ui.stapelPlural
import stapel.ui.stapelString

/**
 * Phone numbers (`profiles.contacts`, web `ContactsManager`): each number masked to its last two
 * digits until shown, confirmed or not (an unconfirmed number is handed to nobody, and the screen says
 * so), its policy chosen from what the server offers, its switch, delete (confirmed), and the SMS
 * confirmation; a field to add a number.
 */
@Composable
public fun ContactsScreen(state: ContactsState, onIntent: (ContactsIntent) -> Unit, modifier: Modifier = Modifier) {
    var confirmDelete by rememberSaveable { mutableStateOf<Int?>(null) }
    StapelPage(modifier.testTag(ProfilesTestTags.CONTACTS), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
            StapelScreenTitle(stapelString(ProfilesStrings.ContactsTitle))
            StapelText(stapelString(ProfilesStrings.ContactsSubtitle), color = StapelTheme.colors.textMuted)
            val data = state.contacts
            when {
                data == null && state.loadError != null -> ErrorState(state.loadError!!, testTag = ProfilesTestTags.ERROR)
                data == null -> StapelProgress(modifier = Modifier.fillMaxWidth())
                else -> {
                    state.error?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    if (data.contacts.isEmpty()) {
                        EmptyState(stapelString(ProfilesStrings.ContactsEmpty), description = stapelString(ProfilesStrings.ContactsEmptyHint))
                    }
                    for (contact in data.contacts) {
                        ContactCard(contact, data.policies, state, onIntent, onDelete = { confirmDelete = contact.id })
                    }
                    AddNumber(state, onIntent)
                }
            }
        }
    }
    confirmDelete?.let { id ->
        ConfirmDialog(
            title = stapelString(ProfilesStrings.ContactsConfirmDeleteTitle),
            message = stapelString(ProfilesStrings.ContactsConfirmDeleteBody),
            confirmLabel = stapelString(ProfilesStrings.ContactsDelete),
            cancelLabel = stapelString(ProfilesUiStrings.Cancel),
            onConfirm = {
                confirmDelete = null
                onIntent(ContactsIntent.Delete(id))
            },
            onDismiss = { confirmDelete = null },
            danger = true,
        )
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    policies: List<ContactPolicy>,
    state: ContactsState,
    onIntent: (ContactsIntent) -> Unit,
    onDelete: () -> Unit,
) {
    var shown by rememberSaveable(contact.id) { mutableStateOf(false) }
    val busy = state.busy == contact.id
    StapelSurface(Modifier.fillMaxWidth(), bordered = true, testTag = "${ProfilesTestTags.CONTACT_ROW}.${contact.id}") {
        Column(Modifier.fillMaxWidth().padding(StapelTheme.spacing.s3), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                StapelText(if (shown) contact.value else maskPhoneNumber(contact.value), style = StapelTheme.type.title)
                StapelButton(
                    stapelString(if (shown) ProfilesStrings.ContactsHideNumber else ProfilesStrings.ContactsShowNumber),
                    { shown = !shown },
                    variant = ButtonVariant.Text,
                )
            }
            if (contact.label.isNotBlank()) StapelText(contact.label, color = StapelTheme.colors.textMuted)
            StapelText(
                stapelString(if (contact.verified) ProfilesStrings.ContactsVerified else ProfilesStrings.ContactsUnverified),
                color = if (contact.verified) StapelTheme.colors.success else StapelTheme.colors.warning,
            )
            if (!contact.verified) StapelText(stapelString(ProfilesStrings.ContactsUnverifiedHint), color = StapelTheme.colors.textMuted)
            if (policies.isNotEmpty()) {
                StapelChipGroup(
                    chips = policies.map { p ->
                        ChipSpec(stapelString(policyLabel(p)), selected = contact.policy == p, onClick = { onIntent(ContactsIntent.SetPolicy(contact.id, p)) })
                    },
                    label = stapelString(ProfilesStrings.ContactsPolicyLabel),
                )
            }
            StapelToggle(
                ToggleKind.Switch,
                checked = contact.enabled,
                onCheckedChange = { onIntent(ContactsIntent.SetEnabled(contact.id, it)) },
                label = stapelString(ProfilesStrings.ContactsEnabledLabel),
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            val pending = state.verification?.takeIf { it.contactId == contact.id }
            if (pending != null) {
                Verification(pending.expiresInSeconds, pending.confirming, state, onIntent)
            } else if (!contact.verified) {
                StapelButton(
                    stapelString(if (busy) ProfilesStrings.ContactsVerifySending else ProfilesStrings.ContactsVerify),
                    { onIntent(ContactsIntent.RequestCode(contact.id)) },
                    variant = ButtonVariant.Secondary,
                    loading = busy,
                )
            }
            StapelButton(stapelString(ProfilesStrings.ContactsDelete), onDelete, variant = ButtonVariant.Danger, enabled = !busy)
        }
    }
}

@Composable
private fun Verification(expiresIn: Int?, confirming: Boolean, state: ContactsState, onIntent: (ContactsIntent) -> Unit) {
    val code = rememberTextFieldState()
    StapelText(stapelString(ProfilesStrings.ContactsVerifySent))
    expiresIn?.let { StapelText(stapelString(ProfilesStrings.ContactsVerifyExpiresIn, "seconds" to it), color = StapelTheme.colors.textMuted) }
    val attempts = state.error?.takeIf { it.reason == ProfilesErrorReason.InvalidCode }?.attemptsRemaining
    StapelTextField(
        code,
        stapelString(ProfilesStrings.ContactsVerifyCodeField),
        Modifier.fillMaxWidth(),
        kind = TextFieldKind.Number,
        placeholder = stapelString(ProfilesStrings.ContactsVerifyCodePlaceholder),
        error = attempts?.let { stapelPlural(ProfilesStrings.ContactsVerifyAttemptsLeft, it.toLong(), "count" to it) },
        testTag = ProfilesTestTags.CONTACT_CODE,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
        StapelButton(
            stapelString(if (confirming) ProfilesStrings.ContactsVerifyConfirming else ProfilesStrings.ContactsVerifyConfirm),
            { onIntent(ContactsIntent.ConfirmCode(code.text.toString())) },
            enabled = code.text.isNotBlank(),
            loading = confirming,
        )
        StapelButton(stapelString(ProfilesStrings.ContactsVerifyCancel), { onIntent(ContactsIntent.CancelVerification) }, variant = ButtonVariant.Text)
    }
}

@Composable
private fun AddNumber(state: ContactsState, onIntent: (ContactsIntent) -> Unit) {
    val number = rememberTextFieldState()
    val label = rememberTextFieldState()
    SectionHeader(stapelString(ProfilesStrings.ContactsAdd))
    StapelTextField(
        number,
        stapelString(ProfilesStrings.ContactsNumberField),
        Modifier.fillMaxWidth(),
        kind = TextFieldKind.Phone,
        placeholder = stapelString(ProfilesStrings.ContactsNumberPlaceholder),
        testTag = ProfilesTestTags.CONTACT_NUMBER,
    )
    StapelTextField(
        label,
        stapelString(ProfilesStrings.ContactsLabelField),
        Modifier.fillMaxWidth(),
        placeholder = stapelString(ProfilesStrings.ContactsLabelPlaceholder),
    )
    StapelButton(
        stapelString(if (state.adding) ProfilesStrings.ContactsAdding else ProfilesStrings.ContactsAdd),
        {
            onIntent(ContactsIntent.Add(number.text.toString(), label.text.toString()))
            number.clearText()
            label.clearText()
        },
        enabled = number.text.isNotBlank(),
        loading = state.adding,
        testTag = ProfilesTestTags.CONTACT_ADD,
    )
}

private fun policyLabel(policy: ContactPolicy): StringKey = when (policy) {
    ContactPolicy.Members -> ProfilesStrings.ContactsPolicyMembers
    ContactPolicy.Verified -> ProfilesStrings.ContactsPolicyVerified
    ContactPolicy.Nobody -> ProfilesStrings.ContactsPolicyNobody
}
