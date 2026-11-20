package stapel.profiles.internal

import stapel.image.Branch
import stapel.image.StapelImage
import stapel.image.VariantMeta
import stapel.profiles.api.model.ContactListResponse
import stapel.profiles.api.model.ContactResponse
import stapel.profiles.api.model.LanguageResponse
import stapel.profiles.api.model.PatchedProfileUpdateRequest
import stapel.profiles.api.model.ProfilePublic
import stapel.profiles.api.model.ProfilePublicResponse
import stapel.profiles.api.model.ProfileResponse
import stapel.profiles.api.model.StapelImageDTO
import stapel.profiles.model.AvatarRef
import stapel.profiles.model.Contact
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.Contacts
import stapel.profiles.model.Disclosed
import stapel.profiles.model.Language
import stapel.profiles.model.MyProfile
import stapel.profiles.model.NotificationPreferences
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.model.PublicProfile
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.model.ThemePreference
import stapel.wire.Maybe
import stapel.profiles.api.model.StapelImage as ApiStapelImage

private fun variant(tier: String, branch: String?, url: String, width: Int?, height: Int?): VariantMeta = VariantMeta(
    tier = tier,
    branch = when (branch) {
        "w" -> Branch.W
        "h" -> Branch.H
        else -> null
    },
    url = url,
    width = width,
    height = height,
)

internal fun StapelImageDTO.toImage(): StapelImage = StapelImage(
    source = source,
    url = url,
    mime = mime,
    width = width,
    height = height,
    aspect = aspect,
    square = square,
    previewB64 = previewB64,
    variants = variants.orEmpty().map { variant(it.tier, it.branch, it.url, it.width, it.height) },
)

internal fun ApiStapelImage.toImage(): StapelImage = StapelImage(
    source = source,
    url = url,
    mime = mime,
    width = width,
    height = height,
    aspect = aspect,
    square = square,
    previewB64 = previewB64,
    variants = variants.map { variant(it.tier, it.branch, it.url, it.width, it.height) },
)

internal fun LanguageResponse.toDomain(): Language = Language(code, name, flag)

internal fun ProfileResponse.toDomain(): MyProfile = MyProfile(
    userId = userId,
    displayName = displayName,
    theme = ThemePreference.of(theme),
    avatar = avatarImage?.toImage(),
    avatarRef = avatar?.takeIf { it.isNotEmpty() }?.let { AvatarRef(it, avatarSource) },
    location = locationDisplayNameBroad,
    appLanguage = appLanguage?.toDomain(),
    useDeviceLanguage = useDeviceLanguage,
    autoTranslateContent = autoTranslateContent,
    understands = understands,
    notifications = NotificationPreferences(emailMessages, emailSystem, pushMessages, pushSystem),
    initialSetupPassed = initialSetupPassed,
    followersCount = followersCount,
    followingCount = followingCount,
    createdAt = createdAt,
)

internal fun ProfilePublicResponse.toDomain(viewerId: String?): PublicProfile = PublicProfile(
    userId = userId,
    displayName = displayName,
    avatar = avatarImage?.toImage(),
    location = Disclosed.text(locationDisplayNameBroad),
    followersCount = followersCount,
    followingCount = followingCount,
    relationship = if (viewerId != null && viewerId == userId) RelationshipStatus.Self else RelationshipStatus.of(relationshipStatus),
    sellerType = sellerType,
    createdAt = createdAt,
    hasPhone = contacts.phone,
)

internal fun ProfilePublic.toDomain(viewerId: String?): PublicProfile = PublicProfile(
    userId = userId,
    displayName = displayName.orEmpty(),
    avatar = avatarImage?.toImage(),
    location = Disclosed.text(locationDisplayNameBroad),
    followersCount = followersCount,
    followingCount = followingCount,
    relationship = if (viewerId != null && viewerId == userId) RelationshipStatus.Self else RelationshipStatus.of(relationshipStatus),
    sellerType = sellerType,
    createdAt = createdAt.toString(),
    hasPhone = contacts.phone,
)

internal fun ContactResponse.toDomain(): Contact = Contact(
    id = id,
    value = value,
    label = label,
    policy = ContactPolicy.of(policy),
    enabled = enabled,
    verified = verified,
    revealCount = revealCount,
)

internal fun ContactListResponse.toDomain(): Contacts =
    Contacts(contacts.map { it.toDomain() }, policies.mapNotNull { ContactPolicy.of(it) })

private fun <T : Any> present(value: T?): Maybe<T?> = if (value == null) Maybe.Absent else Maybe.Present(value)

internal fun ProfileUpdate.toRequest(): PatchedProfileUpdateRequest = PatchedProfileUpdateRequest(
    displayName = present(displayName),
    theme = present(theme?.wire),
    avatarSource = present(avatar?.source),
    avatar = present(avatar?.ref),
    appLanguage = present(appLanguage),
    useDeviceLanguage = present(useDeviceLanguage),
    autoTranslateContent = present(autoTranslateContent),
    emailMessages = present(notifications?.emailMessages),
    emailSystem = present(notifications?.emailSystem),
    pushMessages = present(notifications?.pushMessages),
    pushSystem = present(notifications?.pushSystem),
    initialSetupPassed = present(initialSetupPassed),
)
