package stapel.profiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import stapel.i18n.StringKey
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.Person
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.presentation.ConnectionsIntent
import stapel.profiles.presentation.ConnectionsState
import stapel.profiles.presentation.PublicProfileIntent
import stapel.profiles.presentation.PublicProfileState
import stapel.profiles.presentation.RelationshipAction
import stapel.ui.ConfirmDialog
import stapel.ui.EmptyState
import stapel.ui.ErrorState
import stapel.ui.PaneMeasure
import stapel.ui.StapelAvatar
import stapel.ui.StapelButton
import stapel.ui.StapelListItem
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelTabs
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.TabsVariant
import stapel.ui.stapelPlural
import stapel.ui.stapelString

/**
 * A person's public profile (`profiles.public`, `/u/:userId`, web `PublicProfilePage`): avatar, name,
 * location, counters, the relationship controls (follow / unfollow, block / unblock, block confirmed)
 * for a signed-in stranger, and the `page.actions` slot other libraries fill.
 */
@Composable
public fun PublicProfileScreen(
    state: PublicProfileState,
    onIntent: (PublicProfileIntent) -> Unit,
    pageActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    StapelPage(modifier.testTag(ProfilesTestTags.PUBLIC), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
            val profile = state.profile
            when {
                profile == null && state.loadError?.reason == ProfilesErrorReason.NotFound ->
                    EmptyState(stapelString(ProfilesUiStrings.PublicNotFound), description = stapelString(ProfilesStrings.PublicUnwritten))
                profile == null && state.loadError != null ->
                    ErrorState(state.loadError!!, onRetry = { onIntent(PublicProfileIntent.Refresh) }, testTag = ProfilesTestTags.ERROR)
                profile == null -> StapelProgress(modifier = Modifier.fillMaxWidth())
                else -> {
                    val name = profile.displayName.ifBlank { stapelString(ProfilesStrings.PersonUnnamed) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4)) {
                        StapelAvatar(contentDescription = name, image = profile.avatar, size = AvatarSize.XLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s1)) {
                            StapelScreenTitle(name)
                            if (profile.location.isNotBlank()) StapelText(profile.location, color = StapelTheme.colors.textMuted)
                        }
                    }
                    StapelText(
                        stapelPlural(ProfilesUiStrings.PublicFollowers, profile.followersCount.toLong(), "count" to profile.followersCount) + " · " +
                            stapelPlural(ProfilesStrings.PublicCountFollowing, profile.followingCount.toLong(), "count" to profile.followingCount),
                    )
                    when (profile.relationship) {
                        RelationshipStatus.Self -> StapelText(stapelString(ProfilesStrings.RelationshipSelf), color = StapelTheme.colors.textMuted)
                        RelationshipStatus.Blocked -> StapelText(stapelString(ProfilesStrings.RelationshipBlockedNotice), color = StapelTheme.colors.textMuted)
                        else -> if (!state.signedIn) StapelText(stapelString(ProfilesUiStrings.PublicSignInHint), color = StapelTheme.colors.textMuted)
                    }
                    if (state.actions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s2)) {
                            for (action in state.actions) {
                                StapelButton(
                                    stapelString(actionLabel(action)),
                                    { if (action == RelationshipAction.Block) confirmBlock = true else onIntent(PublicProfileIntent.Act(action)) },
                                    variant = if (action == RelationshipAction.Follow) ButtonVariant.Primary else ButtonVariant.Secondary,
                                    enabled = !state.acting,
                                    testTag = "${ProfilesTestTags.PUBLIC_ACTION}.${action.name.lowercase()}",
                                )
                            }
                        }
                    }
                    state.actionError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    pageActions()
                    if (confirmBlock) {
                        ConfirmDialog(
                            title = stapelString(ProfilesStrings.RelationshipConfirmBlockTitle, "name" to name),
                            message = stapelString(ProfilesStrings.RelationshipConfirmBlockBody),
                            confirmLabel = stapelString(ProfilesStrings.RelationshipBlock),
                            cancelLabel = stapelString(ProfilesUiStrings.Cancel),
                            onConfirm = {
                                confirmBlock = false
                                onIntent(PublicProfileIntent.Act(RelationshipAction.Block))
                            },
                            onDismiss = { confirmBlock = false },
                            danger = true,
                        )
                    }
                }
            }
        }
    }
}

private fun actionLabel(action: RelationshipAction): StringKey = when (action) {
    RelationshipAction.Follow -> ProfilesStrings.RelationshipFollow
    RelationshipAction.Unfollow -> ProfilesStrings.RelationshipUnfollow
    RelationshipAction.Block -> ProfilesStrings.RelationshipBlock
    RelationshipAction.Unblock -> ProfilesStrings.RelationshipUnblock
}

/**
 * Connections (`profiles.connections`, web `ConnectionsPage`): followers / following / blocked behind
 * one segmented control; a row per person (avatar or monogram, name, location) opening their profile,
 * with "Unfollow" on the following list and "Unblock" on the blocked one; an empty state per list.
 */
@Composable
public fun ConnectionsScreen(
    state: ConnectionsState,
    onIntent: (ConnectionsIntent) -> Unit,
    onOpenPerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StapelPage(modifier.testTag(ProfilesTestTags.CONNECTIONS), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
            StapelScreenTitle(stapelString(ProfilesStrings.ConnectionsTitle))
            StapelText(stapelString(ProfilesStrings.ConnectionsSubtitle), color = StapelTheme.colors.textMuted)
            val kinds = ConnectionKind.entries
            StapelTabs(
                items = kinds.map { stapelString(kindLabel(it)) },
                selectedIndex = kinds.indexOf(state.kind),
                onSelect = { onIntent(ConnectionsIntent.Show(kinds[it])) },
                variant = TabsVariant.Segmented,
            )
            state.actionError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
            val people = state.people
            when {
                people == null && state.error != null ->
                    ErrorState(state.error!!, onRetry = { onIntent(ConnectionsIntent.Refresh) }, testTag = ProfilesTestTags.ERROR)
                people == null -> StapelProgress(modifier = Modifier.fillMaxWidth())
                people.isEmpty() -> EmptyState(stapelString(emptyTitle(state.kind)), description = stapelString(emptyHint(state.kind)))
                else -> Column(Modifier.fillMaxWidth()) {
                    for (person in people) PersonRow(person, state, onIntent, onOpenPerson)
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: Person, state: ConnectionsState, onIntent: (ConnectionsIntent) -> Unit, onOpenPerson: (String) -> Unit) {
    val profile = person.profile
    val name = profile?.displayName?.ifBlank { null } ?: stapelString(if (profile == null) ProfilesStrings.PersonMissing else ProfilesStrings.PersonUnnamed)
    val action = when (state.kind) {
        ConnectionKind.Following -> RelationshipAction.Unfollow
        ConnectionKind.Blocked -> RelationshipAction.Unblock
        ConnectionKind.Followers -> null
    }
    StapelListItem(
        headline = name,
        supporting = profile?.location?.ifBlank { null },
        leading = { StapelAvatar(contentDescription = name, image = profile?.avatar, size = AvatarSize.Medium) },
        trailing = action?.let { a ->
            {
                StapelButton(
                    stapelString(actionLabel(a)),
                    { onIntent(ConnectionsIntent.Act(person.userId, a)) },
                    variant = ButtonVariant.Text,
                    loading = state.acting == person.userId,
                )
            }
        },
        onClick = if (profile != null) ({ onOpenPerson(person.userId) }) else null,
        testTag = "${ProfilesTestTags.PERSON}.${person.userId}",
    )
}

private fun kindLabel(kind: ConnectionKind): StringKey = when (kind) {
    ConnectionKind.Followers -> ProfilesStrings.ListFollowers
    ConnectionKind.Following -> ProfilesStrings.ListFollowing
    ConnectionKind.Blocked -> ProfilesStrings.ListBlocked
}

private fun emptyTitle(kind: ConnectionKind): StringKey = when (kind) {
    ConnectionKind.Followers -> ProfilesStrings.ListEmptyFollowers
    ConnectionKind.Following -> ProfilesStrings.ListEmptyFollowing
    ConnectionKind.Blocked -> ProfilesStrings.ListEmptyBlocked
}

private fun emptyHint(kind: ConnectionKind): StringKey = when (kind) {
    ConnectionKind.Followers -> ProfilesStrings.ListEmptyFollowersHint
    ConnectionKind.Following -> ProfilesStrings.ListEmptyFollowingHint
    ConnectionKind.Blocked -> ProfilesStrings.ListEmptyBlockedHint
}
