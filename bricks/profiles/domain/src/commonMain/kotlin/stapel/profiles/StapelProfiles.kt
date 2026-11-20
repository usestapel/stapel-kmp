package stapel.profiles

import stapel.cache.StapelCache
import stapel.profiles.api.ProfilesApi
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.repo.ConnectionsRepository
import stapel.profiles.repo.ContactsRepository
import stapel.profiles.repo.LanguagesRepository
import stapel.profiles.repo.MyProfileRepository
import stapel.profiles.repo.PeopleRepository

/**
 * The profiles library's façade: everything a project wires, built from plain values
 * (`di.profilesModule()` is one way to call it).
 *
 * @param api the generated client on the AUTHENTICATED transport (the own profile, contacts and
 *   relationship actions need the session; public profiles and the catalogue work without one).
 * @param cache the app's cache; its partitions must follow the session, which is what wipes the own
 *   profile, the lists and the contacts on every sign-out.
 * @param avatarUploader turns a picked picture into an avatar reference ([AvatarUploader.None] = the
 *   app offers no upload).
 * @param viewer the signed-in account id, or null (a profile of the viewer reads as `Self`).
 */
public class StapelProfiles(
    /** The generated client. */
    public val api: ProfilesApi,
    cache: StapelCache,
    avatarUploader: AvatarUploader = AvatarUploader.None,
    viewer: () -> String? = { null },
) {
    /** Whether this app can upload an avatar. */
    public val canUploadAvatar: Boolean = avatarUploader !== AvatarUploader.None

    /** `GET`/`PATCH /me` and the avatar. */
    public val me: MyProfileRepository = MyProfileRepository(api, cache, avatarUploader)

    /** The language catalogue. */
    public val languages: LanguagesRepository = LanguagesRepository(api, cache)

    /** Public profiles, the people batch and relationship actions. */
    public val people: PeopleRepository = PeopleRepository(api, cache, viewer)

    /** Followers, following and blocked. */
    public val connections: ConnectionsRepository = ConnectionsRepository(api, cache, people, viewer)

    /** The person's own phone numbers. */
    public val contacts: ContactsRepository = ContactsRepository(api, cache)
}
