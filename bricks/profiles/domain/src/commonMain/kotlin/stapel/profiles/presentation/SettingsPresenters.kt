package stapel.profiles.presentation

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.channels.Channel
import stapel.profiles.StapelProfiles
import stapel.profiles.error.ProfilesError
import stapel.profiles.model.Language
import stapel.profiles.model.MyProfile
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.model.NotificationPreferences
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.model.ThemePreference
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.wire.FilePart

/** One-shot effects of the settings presenters. */
public sealed interface ProfilesEffect {
    /** A change was stored on the server. */
    public data object Saved : ProfilesEffect
}

/**
 * State of [ProfileSettingsPresenter] (and of the language and notification screens, which edit the
 * same profile).
 *
 * @property profile the profile, once read.
 * @property loading true until the first read settled.
 * @property loadError why the profile could not be read (shown only while there is no [profile]).
 * @property saving a change is being stored.
 * @property saveError why the last change was refused.
 * @property uploading an avatar upload is running.
 * @property uploadProgress the upload's progress in 0..1, when the size is known.
 * @property avatarError why the last avatar could not be set.
 * @property canUploadAvatar whether the app offers avatar upload.
 */
public data class ProfileSettingsState(
    val profile: MyProfile? = null,
    val loading: Boolean = true,
    val loadError: ProfilesError? = null,
    val saving: Boolean = false,
    val saveError: ProfilesError? = null,
    val uploading: Boolean = false,
    val uploadProgress: Float? = null,
    val avatarError: ProfilesError? = null,
    val canUploadAvatar: Boolean = false,
)

/** Intents of [ProfileSettingsPresenter]. */
public sealed interface ProfileSettingsIntent {
    /** Re-reads the profile from the network. */
    public data object Refresh : ProfileSettingsIntent

    /** Stores a new display name (trimmed; an unchanged name sends nothing). */
    public data class SetDisplayName(
        /** The new name. */
        val name: String,
    ) : ProfileSettingsIntent

    /** Stores a theme preference. */
    public data class SetTheme(
        /** The new theme. */
        val theme: ThemePreference,
    ) : ProfileSettingsIntent

    /** Uploads [file] and stores it as the avatar (one operation). */
    public data class SetAvatar(
        /** The picked picture. */
        val file: FilePart,
    ) : ProfileSettingsIntent

    /** Follows the device language ([code] null) or sets an app language from the catalogue. */
    public data class SetLanguage(
        /** Language code, or null for the device language. */
        val code: String?,
    ) : ProfileSettingsIntent

    /** Turns content translation on or off. */
    public data class SetAutoTranslate(
        /** On or off. */
        val on: Boolean,
    ) : ProfileSettingsIntent

    /** Turns one notification switch on or off. */
    public data class SetNotification(
        /** The category. */
        val category: NotificationCategory,
        /** The channel. */
        val channel: NotificationChannel,
        /** On or off. */
        val on: Boolean,
    ) : ProfileSettingsIntent
}

/**
 * The own profile for the settings, language and notification screens: observes `/me` (cache first,
 * revalidated), stores changes with `PATCH /me` and the avatar through the uploader. A refused change
 * leaves the profile as the server has it and reports [ProfileSettingsState.saveError].
 */
public class ProfileSettingsPresenter(private val profiles: StapelProfiles, context: PresenterContext) :
    Presenter<ProfileSettingsState, ProfileSettingsIntent, ProfilesEffect>(
        ProfileSettingsState(canUploadAvatar = profiles.canUploadAvatar),
        context,
    ) {

    // Switches flipped but not yet confirmed by the server: a revalidation that lands in between must not
    // undo them on screen, and the next switch is built on them, not on the server's older answer.
    @kotlin.concurrent.Volatile
    private var pendingNotifications: NotificationPreferences? = null

    // One PATCH at a time, in intent order: two requests in flight could reach the server swapped.
    private val saves = Channel<ProfileUpdate>(Channel.UNLIMITED)

    @kotlin.concurrent.Volatile
    private var queued = 0

    init {
        launch(SAVES) { for (update in saves) store(update) }
        launch(OBSERVE) {
            profiles.me.me().collectLatest { r ->
                when (r) {
                    is Resource.Loading -> Unit
                    is Resource.Ready -> reduce { copy(profile = r.data.withPending(), loading = false, loadError = null) }
                    is Resource.Failed -> reduce { copy(loading = false, loadError = ProfilesError.of(r.error)) }
                }
            }
        }
    }

    private fun MyProfile.withPending(): MyProfile = pendingNotifications?.let { copy(notifications = it) } ?: this

    override suspend fun handle(intent: ProfileSettingsIntent) {
        when (intent) {
            ProfileSettingsIntent.Refresh -> launch(REFRESH) {
                reduce { copy(loading = profile == null, loadError = null) }
                when (val r = profiles.me.get()) {
                    is StapelResult.Ok -> reduce { copy(profile = r.value, loading = false) }
                    is StapelResult.Err -> reduce { copy(loading = false, loadError = r.error) }
                }
            }
            is ProfileSettingsIntent.SetDisplayName -> {
                val name = intent.name.trim()
                if (name == state.value.profile?.displayName) return
                save(ProfileUpdate(displayName = name))
            }
            is ProfileSettingsIntent.SetTheme -> save(ProfileUpdate(theme = intent.theme))
            is ProfileSettingsIntent.SetLanguage -> save(
                if (intent.code == null) ProfileUpdate(useDeviceLanguage = true)
                else ProfileUpdate(appLanguage = intent.code, useDeviceLanguage = false),
            )
            is ProfileSettingsIntent.SetAutoTranslate -> save(ProfileUpdate(autoTranslateContent = intent.on))
            is ProfileSettingsIntent.SetNotification -> {
                val current = pendingNotifications ?: state.value.profile?.notifications ?: return
                val next = current.with(intent.category, intent.channel, intent.on)
                pendingNotifications = next
                reduce { copy(profile = profile?.copy(notifications = next)) }
                save(ProfileUpdate(notifications = next))
            }
            is ProfileSettingsIntent.SetAvatar -> launch(AVATAR) {
                reduce { copy(uploading = true, uploadProgress = null, avatarError = null) }
                val r = profiles.me.setAvatar(intent.file) { sent, total ->
                    if (total != null && total > 0) reduce { copy(uploadProgress = (sent.toFloat() / total).coerceIn(0f, 1f)) }
                }
                when (r) {
                    is StapelResult.Ok -> {
                        reduce { copy(profile = r.value, uploading = false, uploadProgress = null) }
                        emit(ProfilesEffect.Saved)
                    }
                    is StapelResult.Err -> reduce { copy(uploading = false, uploadProgress = null, avatarError = r.error) }
                }
            }
        }
    }

    // A second change never cancels the first: saves queue and run one after another.
    private fun save(update: ProfileUpdate) {
        queued++
        saves.trySend(update)
    }

    private suspend fun store(update: ProfileUpdate) {
        reduce { copy(saving = true, saveError = null) }
        val r = profiles.me.update(update)
        queued--
        if (update.notifications != null && update.notifications == pendingNotifications) pendingNotifications = null
        when (r) {
            is StapelResult.Ok -> {
                reduce { copy(profile = r.value.withPending(), saving = queued > 0) }
                emit(ProfilesEffect.Saved)
            }
            is StapelResult.Err -> {
                pendingNotifications = null
                reduce { copy(saving = queued > 0, saveError = r.error) }
                // Back to what the server holds (an optimistic switch is undone).
                (profiles.me.get() as? StapelResult.Ok)?.let { ok -> reduce { copy(profile = ok.value) } }
            }
        }
    }

    private companion object {
        const val OBSERVE = "me"
        const val SAVES = "saves"
        const val REFRESH = "refresh"
        const val AVATAR = "avatar"
    }
}

/**
 * State of [LanguageCataloguePresenter].
 *
 * @property languages the catalogue, sorted by name.
 * @property loading true until the first read settled.
 * @property error why the catalogue could not be read.
 */
public data class LanguageCatalogueState(
    val languages: List<Language> = emptyList(),
    val loading: Boolean = true,
    val error: ProfilesError? = null,
)

/** The language catalogue for the language screen (public, cache-first). Takes no intents. */
public class LanguageCataloguePresenter(private val profiles: StapelProfiles, context: PresenterContext) :
    Presenter<LanguageCatalogueState, Nothing, Nothing>(LanguageCatalogueState(), context) {
    init {
        launch {
            profiles.languages.languages().collectLatest { r ->
                when (r) {
                    is Resource.Loading -> Unit
                    is Resource.Ready -> reduce { copy(languages = r.data, loading = false, error = null) }
                    is Resource.Failed -> reduce { copy(loading = false, error = ProfilesError.of(r.error)) }
                }
            }
        }
    }

    override suspend fun handle(intent: Nothing) {}
}
