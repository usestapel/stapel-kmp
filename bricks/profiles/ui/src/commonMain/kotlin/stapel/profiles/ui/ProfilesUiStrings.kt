package stapel.profiles.ui

import stapel.i18n.I18nBundle
import stapel.i18n.PluralKey
import stapel.i18n.StringKey
import stapel.profiles.api.error.ProfilesErrorCatalog
import stapel.result.text.ErrorCatalog

/**
 * Typed keys of copy that exists only on mobile (the web pair composes these screens differently).
 * They live under `profiles.ui.*`; the copy shared with the web pair is [ProfilesStrings].
 */
public object ProfilesUiStrings {
    /** "More" — the settings list of sections (the container's children). */
    public val SettingsSections: StringKey = StringKey("profiles.ui.settings.sections")

    /** "Your avatar" — the avatar's accessible name on the settings screen. */
    public val AvatarLabel: StringKey = StringKey("profiles.ui.avatar.label")

    /** "Use the device language". */
    public val LanguageDevice: StringKey = StringKey("profiles.ui.language.device")

    /** "Translate content in other languages". */
    public val AutoTranslate: StringKey = StringKey("profiles.ui.language.auto_translate")

    /** "Cancel". */
    public val Cancel: StringKey = StringKey("profiles.ui.cancel")

    /** "{count} followers" on a public profile. */
    public val PublicFollowers: PluralKey = PluralKey("profiles.ui.public.followers")

    /** "Profile not found" — a public profile that does not exist. */
    public val PublicNotFound: StringKey = StringKey("profiles.ui.public.not_found")

    /** "Sign in to follow" — a public profile seen without a session. */
    public val PublicSignInHint: StringKey = StringKey("profiles.ui.public.sign_in_hint")
}

private val MOBILE_EN: Map<String, String> = mapOf(
    "profiles.ui.settings.sections" to "More",
    "profiles.ui.avatar.label" to "Your avatar",
    "profiles.ui.language.device" to "Use the device language",
    "profiles.ui.language.auto_translate" to "Translate content in other languages",
    "profiles.ui.cancel" to "Cancel",
    "profiles.ui.public.followers.one" to "{count} follower",
    "profiles.ui.public.followers.other" to "{count} followers",
    "profiles.ui.public.not_found" to "Profile not found",
    "profiles.ui.public.sign_in_hint" to "Sign in to follow people.",
)

private val MOBILE_RU: Map<String, String> = mapOf(
    "profiles.ui.settings.sections" to "Ещё",
    "profiles.ui.avatar.label" to "Ваш аватар",
    "profiles.ui.language.device" to "Как на устройстве",
    "profiles.ui.language.auto_translate" to "Переводить содержимое на других языках",
    "profiles.ui.cancel" to "Отмена",
    "profiles.ui.public.followers.one" to "{count} подписчик",
    "profiles.ui.public.followers.few" to "{count} подписчика",
    "profiles.ui.public.followers.many" to "{count} подписчиков",
    "profiles.ui.public.followers.other" to "{count} подписчиков",
    "profiles.ui.public.not_found" to "Профиль не найден",
    "profiles.ui.public.sign_in_hint" to "Войдите, чтобы подписываться на людей.",
)

/** Every plural family of this library's copy (shared and mobile-only), for `I18nBundle.audit`. */
public val ProfilesPluralFamilies: Set<String> = setOf(
    ProfilesStrings.ContactsRevealBudget.key,
    ProfilesStrings.ContactsVerifyAttemptsLeft.key,
    ProfilesStrings.ListCountBlocked.key,
    ProfilesStrings.ListCountFollowers.key,
    ProfilesStrings.ListCountFollowing.key,
    ProfilesStrings.PublicCountFollowing.key,
    ProfilesUiStrings.PublicFollowers.key,
)

/** Keys this library has that the web pair does not (all under `profiles.ui.*`). */
public val ProfilesUiMobileOnlyKeys: Set<String> get() = MOBILE_EN.keys

/** This library's copy in English and Russian: the web pair's keys plus the mobile-only ones. */
public val ProfilesUiBundle: I18nBundle = I18nBundle(
    namespace = "profiles",
    locales = mapOf("en" to { PROFILES_WEB_EN + MOBILE_EN }, "ru" to { PROFILES_WEB_RU + MOBILE_RU }),
)

/**
 * The error catalogs of this library, in the order the app registers them: the English contract
 * texts of the domain library ([ProfilesErrorCatalog]) and the Russian texts ([ProfilesErrorTextsRu]).
 */
public fun profilesErrorCatalogs(): List<ErrorCatalog> = listOf(ProfilesErrorCatalog, ProfilesErrorTextsRu)
