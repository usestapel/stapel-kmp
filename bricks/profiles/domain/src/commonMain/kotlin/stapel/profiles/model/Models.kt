package stapel.profiles.model

import stapel.image.StapelImage

/** The UI theme a person chose (`theme`); [Unknown] keeps a value this client does not know. */
public sealed interface ThemePreference {
    /** The wire value. */
    public val wire: String

    /** Always light. */
    public data object Light : ThemePreference {
        override val wire: String = "light"
    }

    /** Always dark. */
    public data object Dark : ThemePreference {
        override val wire: String = "dark"
    }

    /** Follow the device. */
    public data object System : ThemePreference {
        override val wire: String = "system"
    }

    /** A value newer than this client. */
    public data class Unknown(override val wire: String) : ThemePreference

    /** Parsing. */
    public companion object {
        /** The three choices a screen offers, in display order. */
        public val choices: List<ThemePreference> = listOf(Light, Dark, System)

        /** [wire] as a preference; anything unknown is kept as [Unknown]. */
        public fun of(wire: String): ThemePreference = choices.firstOrNull { it.wire == wire } ?: Unknown(wire)
    }
}

/**
 * A language of the server's catalogue (`/languages/`).
 *
 * @property code ISO 639-1 code (`en`), what a profile stores.
 * @property name the language's English name.
 * @property flag path of a flag image on the backend, when there is one.
 */
public data class Language(val code: String, val name: String, val flag: String?)

/** The two notification categories a person controls. */
public enum class NotificationCategory {
    /** Messages from other people. */
    Messages,

    /** System notices. */
    System,
}

/** The two channels a notification category can use. */
public enum class NotificationChannel {
    /** E-mail. */
    Email,

    /** Push to the person's devices. */
    Push,
}

/**
 * The four category × channel switches of a profile.
 *
 * @property emailMessages messages by e-mail.
 * @property emailSystem system notices by e-mail.
 * @property pushMessages messages by push.
 * @property pushSystem system notices by push.
 */
public data class NotificationPreferences(
    val emailMessages: Boolean,
    val emailSystem: Boolean,
    val pushMessages: Boolean,
    val pushSystem: Boolean,
) {
    /** The switch of [category] on [channel]. */
    public fun isOn(category: NotificationCategory, channel: NotificationChannel): Boolean = when (category) {
        NotificationCategory.Messages -> if (channel == NotificationChannel.Email) emailMessages else pushMessages
        NotificationCategory.System -> if (channel == NotificationChannel.Email) emailSystem else pushSystem
    }

    /** A copy with the switch of [category] on [channel] set to [on]. */
    public fun with(category: NotificationCategory, channel: NotificationChannel, on: Boolean): NotificationPreferences = when (category) {
        NotificationCategory.Messages -> if (channel == NotificationChannel.Email) copy(emailMessages = on) else copy(pushMessages = on)
        NotificationCategory.System -> if (channel == NotificationChannel.Email) copy(emailSystem = on) else copy(pushSystem = on)
    }
}

/**
 * Where a stored avatar reference points (`avatar_source`). The two halves of an avatar, [ref] and
 * [source], are one value in two columns and always travel together.
 *
 * @property ref `avatar/<hash>` for a CDN upload, a URL, or a Gravatar e-mail hash.
 * @property source `cdn`, `file`, `url` or `gravatar`.
 */
public data class AvatarRef(val ref: String, val source: String) {
    /** The CDN reference an upload produced. */
    public companion object {
        /** A stapel-cdn reference (`avatar/<hash>`), tagged `cdn`. */
        public fun cdn(ref: String): AvatarRef = AvatarRef(ref, "cdn")
    }
}

/**
 * The signed-in person's own profile (`GET /me`).
 *
 * @property userId account id.
 * @property displayName the name others see; empty when not set.
 * @property theme UI theme preference.
 * @property avatar the renderable avatar, or null when there is none.
 * @property avatarRef the stored avatar reference and its source, or null.
 * @property location the coarse location shown to others; empty when not set.
 * @property appLanguage the chosen app language, or null for "follow the device".
 * @property useDeviceLanguage whether the app follows the device language.
 * @property autoTranslateContent whether content in other languages is translated.
 * @property understands language codes the person reads.
 * @property notifications notification switches.
 * @property initialSetupPassed whether the first-run prompt was completed.
 * @property followersCount people following this person.
 * @property followingCount people this person follows.
 * @property createdAt account creation instant as the server sent it (ISO 8601).
 */
public data class MyProfile(
    val userId: String,
    val displayName: String,
    val theme: ThemePreference,
    val avatar: StapelImage?,
    val avatarRef: AvatarRef?,
    val location: String,
    val appLanguage: Language?,
    val useDeviceLanguage: Boolean,
    val autoTranslateContent: Boolean,
    val understands: List<String>,
    val notifications: NotificationPreferences,
    val initialSetupPassed: Boolean,
    val followersCount: Int,
    val followingCount: Int,
    val createdAt: String,
)

/**
 * A partial update of the own profile (`PATCH /me`). Null fields are left as they are; the avatar is
 * set only as a whole [AvatarRef] (see `MyProfileRepository.setAvatar`).
 *
 * @property displayName new display name.
 * @property theme new theme.
 * @property appLanguage new app language code (from the catalogue).
 * @property useDeviceLanguage follow the device language.
 * @property autoTranslateContent translate content.
 * @property notifications new notification switches (all four are sent).
 * @property avatar new avatar reference with its source.
 * @property initialSetupPassed mark the first-run prompt done.
 */
public data class ProfileUpdate(
    val displayName: String? = null,
    val theme: ThemePreference? = null,
    val appLanguage: String? = null,
    val useDeviceLanguage: Boolean? = null,
    val autoTranslateContent: Boolean? = null,
    val notifications: NotificationPreferences? = null,
    val avatar: AvatarRef? = null,
    val initialSetupPassed: Boolean? = null,
) {
    /** True when nothing would be sent. */
    val isEmpty: Boolean
        get() = displayName == null && theme == null && appLanguage == null && useDeviceLanguage == null &&
            autoTranslateContent == null && notifications == null && avatar == null && initialSetupPassed == null
}

/** How the viewer relates to another person (`relationship_status`, from the viewer's side). */
public sealed interface RelationshipStatus {
    /** The wire value, or null when the server sent none (anonymous viewer). */
    public val wire: String?

    /** No relationship. */
    public data object Neutral : RelationshipStatus {
        override val wire: String = "neutral"
    }

    /** The viewer follows this person. */
    public data object Following : RelationshipStatus {
        override val wire: String = "following"
    }

    /** The viewer blocked this person. */
    public data object Blocked : RelationshipStatus {
        override val wire: String = "blocked"
    }

    /** The viewer is this person. */
    public data object Self : RelationshipStatus {
        override val wire: String? = null
    }

    /** The server sent nothing (anonymous viewer) or a value this client does not know. */
    public data class Unknown(override val wire: String?) : RelationshipStatus

    /** Parsing. */
    public companion object {
        /** [wire] as a status. */
        public fun of(wire: String?): RelationshipStatus = when (wire) {
            "neutral" -> Neutral
            "following" -> Following
            "blocked" -> Blocked
            else -> Unknown(wire)
        }
    }
}

/**
 * Another person's public profile (`GET /{user_id}`, rows of `/batch`).
 *
 * @property userId account id.
 * @property displayName the name; empty when not set.
 * @property avatar the renderable avatar, or null.
 * @property location the coarse location; empty when not set.
 * @property followersCount followers.
 * @property followingCount people followed.
 * @property relationship the viewer's relationship to this person.
 * @property sellerType `private` / `business`, when the deployment has sellers.
 * @property createdAt account creation instant (ISO 8601), when the deployment publishes it.
 * @property hasPhone whether a phone number can be asked for.
 */
public data class PublicProfile(
    val userId: String,
    val displayName: String,
    val avatar: StapelImage?,
    val location: String,
    val followersCount: Int,
    val followingCount: Int,
    val relationship: RelationshipStatus,
    val sellerType: String?,
    val createdAt: String?,
    val hasPhone: Boolean,
)

/** The three lists of the connections screen. */
public enum class ConnectionKind {
    /** People who follow the viewer. */
    Followers,

    /** People the viewer follows. */
    Following,

    /** People the viewer blocked. */
    Blocked,
}

/**
 * One person in a connections list: the id from the list and, when the batch answered, the profile.
 *
 * @property userId account id.
 * @property profile the public profile, or null when the person has none.
 */
public data class Person(val userId: String, val profile: PublicProfile?)

/** Who may be handed a contact (`policy`). */
public enum class ContactPolicy(
    /** The wire value. */
    public val wire: String,
) {
    /** Any registered account. */
    Members("members"),

    /** Accounts with a verified e-mail or phone. */
    Verified("verified"),

    /** Nobody: stored, never handed out. */
    Nobody("nobody"),
    ;

    /** Parsing. */
    public companion object {
        /** [wire] as a policy, or null when unknown. */
        public fun of(wire: String): ContactPolicy? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * One of the person's own phone numbers (`/contacts`).
 *
 * @property id contact id.
 * @property value the number, E.164.
 * @property label the owner's label; empty when not set.
 * @property policy who may be handed it; null for a value this client does not know.
 * @property enabled the owner's on/off switch.
 * @property verified whether it was confirmed by SMS.
 * @property revealCount how many times it was handed out.
 */
public data class Contact(
    val id: Int,
    val value: String,
    val label: String,
    val policy: ContactPolicy?,
    val enabled: Boolean,
    val verified: Boolean,
    val revealCount: Int,
)

/**
 * The contacts screen's data: the numbers and the policies the server offers.
 *
 * @property contacts the numbers.
 * @property policies policies the server accepts, in its order.
 */
public data class Contacts(val contacts: List<Contact>, val policies: List<ContactPolicy>)

/** Masks [number] to its last two digits (`•••• 42`), the way a list shows a number until asked. */
public fun maskPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length <= 2) "••••" else "•••• " + digits.takeLast(2)
}
