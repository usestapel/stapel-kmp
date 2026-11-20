package stapel.profiles.presentation

import kotlinx.coroutines.flow.collectLatest
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.profiles.StapelProfiles
import stapel.profiles.error.ProfilesError
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.Person
import stapel.profiles.model.PublicProfile
import stapel.profiles.model.RelationshipStatus
import stapel.result.StapelResult
import stapel.result.state.Resource

/** A relationship action. */
public enum class RelationshipAction {
    /** Follow. */
    Follow,

    /** Stop following. */
    Unfollow,

    /** Block. */
    Block,

    /** Unblock. */
    Unblock,
}

/**
 * State of [PublicProfilePresenter].
 *
 * @property userId whose profile.
 * @property profile the profile, once read.
 * @property loading true until the first read settled.
 * @property loadError why the profile could not be read; [ProfilesError.reason] `NotFound` = the person
 *   has not set up a profile.
 * @property acting a relationship action is running.
 * @property actionError why the last action was refused.
 * @property signedIn whether a viewer is signed in (actions need one).
 */
public data class PublicProfileState(
    val userId: String,
    val profile: PublicProfile? = null,
    val loading: Boolean = true,
    val loadError: ProfilesError? = null,
    val acting: Boolean = false,
    val actionError: ProfilesError? = null,
    val signedIn: Boolean = false,
) {
    /** The actions to offer: none to oneself or to an anonymous viewer. */
    val actions: List<RelationshipAction>
        get() = when (profile?.relationship.takeIf { signedIn }) {
            null, RelationshipStatus.Self -> emptyList()
            RelationshipStatus.Following -> listOf(RelationshipAction.Unfollow, RelationshipAction.Block)
            RelationshipStatus.Blocked -> listOf(RelationshipAction.Unblock)
            else -> listOf(RelationshipAction.Follow, RelationshipAction.Block)
        }
}

/** Intents of [PublicProfilePresenter]. */
public sealed interface PublicProfileIntent {
    /** Re-reads the profile. */
    public data object Refresh : PublicProfileIntent

    /** Runs [action] on the person. */
    public data class Act(
        /** The action. */
        val action: RelationshipAction,
    ) : PublicProfileIntent
}

/**
 * A person's public profile (`/u/:userId`) with the follow / block controls. After an action the
 * profile is invalidated and re-read, so the counters and status are the server's.
 *
 * @param userId whose profile.
 * @param signedIn whether a viewer is signed in.
 */
public class PublicProfilePresenter(
    private val profiles: StapelProfiles,
    context: PresenterContext,
    userId: String,
    signedIn: Boolean,
) : Presenter<PublicProfileState, PublicProfileIntent, Nothing>(PublicProfileState(userId, signedIn = signedIn), context) {

    init {
        launch(OBSERVE) {
            profiles.people.profile(userId).collectLatest { r ->
                when (r) {
                    is Resource.Loading -> Unit
                    is Resource.Ready -> reduce { copy(profile = r.data, loading = false, loadError = null) }
                    is Resource.Failed -> reduce { copy(loading = false, loadError = ProfilesError.of(r.error)) }
                }
            }
        }
    }

    override suspend fun handle(intent: PublicProfileIntent) {
        val id = state.value.userId
        when (intent) {
            PublicProfileIntent.Refresh -> launch(REFRESH) {
                when (val r = profiles.people.get(id)) {
                    is StapelResult.Ok -> reduce { copy(profile = r.value, loading = false, loadError = null) }
                    is StapelResult.Err -> reduce { copy(loading = false, loadError = r.error) }
                }
            }
            is PublicProfileIntent.Act -> launch(ACT) {
                reduce { copy(acting = true, actionError = null) }
                val r = when (intent.action) {
                    RelationshipAction.Follow -> profiles.people.follow(id)
                    RelationshipAction.Unfollow -> profiles.people.unfollow(id)
                    RelationshipAction.Block -> profiles.people.block(id)
                    RelationshipAction.Unblock -> profiles.people.unblock(id)
                }
                when (r) {
                    is StapelResult.Ok -> reduce { copy(acting = false, profile = profile?.copy(relationship = r.value)) }
                    is StapelResult.Err -> reduce { copy(acting = false, actionError = r.error) }
                }
            }
        }
    }

    private companion object {
        const val OBSERVE = "profile"
        const val REFRESH = "refresh"
        const val ACT = "act"
    }
}

/**
 * State of [ConnectionsPresenter].
 *
 * @property kind the list shown.
 * @property people its rows, once read.
 * @property loading the list is being read.
 * @property error why it could not be read.
 * @property acting the user whose row runs an action.
 * @property actionError why the last action was refused.
 */
public data class ConnectionsState(
    val kind: ConnectionKind = ConnectionKind.Followers,
    val people: List<Person>? = null,
    val loading: Boolean = true,
    val error: ProfilesError? = null,
    val acting: String? = null,
    val actionError: ProfilesError? = null,
)

/** Intents of [ConnectionsPresenter]. */
public sealed interface ConnectionsIntent {
    /** Shows [kind]. */
    public data class Show(
        /** The list. */
        val kind: ConnectionKind,
    ) : ConnectionsIntent

    /** Re-reads the list. */
    public data object Refresh : ConnectionsIntent

    /** Runs [action] on [userId] from a row; the list is re-read after. */
    public data class Act(
        /** The person. */
        val userId: String,
        /** The action. */
        val action: RelationshipAction,
    ) : ConnectionsIntent
}

/** Followers / following / blocked behind one control (`profiles.connections`). */
public class ConnectionsPresenter(private val profiles: StapelProfiles, context: PresenterContext) :
    Presenter<ConnectionsState, ConnectionsIntent, Nothing>(ConnectionsState(), context) {

    init {
        load()
    }

    override suspend fun handle(intent: ConnectionsIntent) {
        when (intent) {
            is ConnectionsIntent.Show -> {
                if (intent.kind == state.value.kind && state.value.people != null) return
                reduce { copy(kind = intent.kind, people = null) }
                load()
            }
            ConnectionsIntent.Refresh -> load()
            is ConnectionsIntent.Act -> launch(ACT) {
                reduce { copy(acting = intent.userId, actionError = null) }
                val r = when (intent.action) {
                    RelationshipAction.Follow -> profiles.people.follow(intent.userId)
                    RelationshipAction.Unfollow -> profiles.people.unfollow(intent.userId)
                    RelationshipAction.Block -> profiles.people.block(intent.userId)
                    RelationshipAction.Unblock -> profiles.people.unblock(intent.userId)
                }
                reduce { copy(acting = null, actionError = (r as? StapelResult.Err)?.error) }
                if (r is StapelResult.Ok) load()
            }
        }
    }

    private fun load() {
        val kind = state.value.kind
        launch(LOAD) {
            reduce { copy(loading = true, error = null) }
            when (val r = profiles.connections.load(kind)) {
                is StapelResult.Ok -> reduce { if (this.kind == kind) copy(people = r.value, loading = false) else this }
                is StapelResult.Err -> reduce { if (this.kind == kind) copy(loading = false, error = r.error) else this }
            }
        }
    }

    private companion object {
        const val LOAD = "load"
        const val ACT = "act"
    }
}
