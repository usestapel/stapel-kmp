package stapel.profiles.testing

import stapel.profiles.model.Contact
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.Contacts
import stapel.profiles.model.Language
import stapel.profiles.model.MyProfile
import stapel.profiles.model.NotificationPreferences
import stapel.profiles.model.Person
import stapel.profiles.model.PublicProfile
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.model.ThemePreference

/** Sample domain values for previews, stories and an app's UI tests (no backend involved). */
public object ProfilesSamples {
    /** A signed-in person's own profile with a name, a language and mixed notification switches. */
    public val me: MyProfile = MyProfile(
        userId = "00000000-0000-4000-8000-000000000001",
        displayName = "Ada Lovelace",
        theme = ThemePreference.System,
        avatar = null,
        avatarRef = null,
        location = "London",
        appLanguage = Language("en", "English", null),
        useDeviceLanguage = false,
        autoTranslateContent = false,
        understands = listOf("en", "fr"),
        notifications = NotificationPreferences(emailMessages = true, emailSystem = true, pushMessages = false, pushSystem = true),
        initialSetupPassed = true,
        followersCount = 42,
        followingCount = 15,
        createdAt = "2025-01-15T12:00:00Z",
    )

    /** A short language catalogue. */
    public val languages: List<Language> = listOf(
        Language("de", "German", null),
        Language("en", "English", null),
        Language("fr", "French", null),
        Language("ru", "Russian", null),
    )

    /** Another person, seen by a signed-in viewer with no relationship. */
    public fun person(
        userId: String = "00000000-0000-4000-8000-000000000002",
        name: String = "Grace Hopper",
        relationship: RelationshipStatus = RelationshipStatus.Neutral,
    ): PublicProfile = PublicProfile(
        userId = userId,
        displayName = name,
        avatar = null,
        location = "Arlington",
        followersCount = 120,
        followingCount = 30,
        relationship = relationship,
        sellerType = null,
        createdAt = "2025-01-15T12:00:00Z",
        hasPhone = false,
    )

    /** Three people for a connections list (one without a profile). */
    public val people: List<Person> = listOf(
        Person("00000000-0000-4000-8000-000000000002", person()),
        Person("00000000-0000-4000-8000-000000000003", person("00000000-0000-4000-8000-000000000003", "Alan Turing")),
        Person("00000000-0000-4000-8000-000000000004", null),
    )

    /** One confirmed and one unconfirmed number. */
    public val contacts: Contacts = Contacts(
        listOf(
            Contact(1, "+15550142", "Work", ContactPolicy.Members, enabled = true, verified = true, revealCount = 7),
            Contact(2, "+15550199", "", ContactPolicy.Verified, enabled = false, verified = false, revealCount = 0),
        ),
        listOf(ContactPolicy.Members, ContactPolicy.Verified, ContactPolicy.Nobody),
    )
}
