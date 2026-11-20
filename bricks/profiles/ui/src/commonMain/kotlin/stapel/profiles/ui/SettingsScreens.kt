package stapel.profiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import stapel.i18n.StringKey
import stapel.navigation.DestinationId
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.model.ThemePreference
import stapel.profiles.presentation.LanguageCatalogueState
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsState
import stapel.ui.EmptyState
import stapel.ui.ErrorState
import stapel.ui.PaneMeasure
import stapel.ui.SectionHeader
import stapel.ui.StapelAvatar
import stapel.ui.StapelButton
import stapel.ui.StapelListItem
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelTabs
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.StapelToggle
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.ButtonVariant
import stapel.ui.skin.ListItemAccessory
import stapel.ui.skin.TabsVariant
import stapel.ui.skin.ToggleKind
import stapel.ui.stapelString

/**
 * The settings container (`profiles.settings`, web `ProfileSettings`): the avatar with "Change avatar"
 * (when the app can pick a picture and upload it), the display name, the theme, then a row for every
 * section installed under this destination — this library's language, notifications and phone numbers,
 * and other libraries' (`auth.security`) — in the navigation's order.
 */
@Composable
public fun ProfileSettingsScreen(
    state: ProfileSettingsState,
    sections: List<SettingsSection>,
    onIntent: (ProfileSettingsIntent) -> Unit,
    onOpen: (DestinationId) -> Unit,
    onPickAvatar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StapelPage(modifier.testTag(ProfilesTestTags.SETTINGS), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4)) {
            StapelScreenTitle(stapelString(ProfilesStrings.SettingsTitle))
            StapelText(stapelString(ProfilesStrings.SettingsSubtitle), color = StapelTheme.colors.textMuted)
            val profile = state.profile
            when {
                profile == null && state.loadError != null ->
                    ErrorState(state.loadError!!, onRetry = { onIntent(ProfileSettingsIntent.Refresh) }, testTag = ProfilesTestTags.ERROR)
                profile == null -> StapelProgress(modifier = Modifier.fillMaxWidth())
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s4)) {
                        StapelAvatar(
                            contentDescription = profile.displayName.ifBlank { stapelString(ProfilesUiStrings.AvatarLabel) },
                            image = profile.avatar,
                            size = AvatarSize.XLarge,
                            testTag = ProfilesTestTags.AVATAR,
                        )
                        if (onPickAvatar != null && state.canUploadAvatar) {
                            StapelButton(
                                stapelString(if (state.uploading) ProfilesStrings.SettingsAvatarUploading else ProfilesStrings.SettingsAvatarChange),
                                onPickAvatar,
                                variant = ButtonVariant.Secondary,
                                loading = state.uploading,
                                testTag = ProfilesTestTags.AVATAR_CHANGE,
                            )
                        }
                    }
                    state.avatarError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    val name = rememberTextFieldState(profile.displayName)
                    StapelTextField(
                        name,
                        stapelString(ProfilesStrings.SettingsFieldDisplayName),
                        Modifier.fillMaxWidth(),
                        testTag = ProfilesTestTags.DISPLAY_NAME,
                    )
                    StapelButton(
                        stapelString(if (state.saving) ProfilesStrings.ProfileSaving else ProfilesStrings.ProfileSave),
                        { onIntent(ProfileSettingsIntent.SetDisplayName(name.text.toString())) },
                        enabled = name.text.toString().trim() != profile.displayName,
                        loading = state.saving,
                        testTag = ProfilesTestTags.DISPLAY_NAME_SAVE,
                    )
                    state.saveError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    SectionHeader(stapelString(ProfilesStrings.SettingsFieldTheme))
                    val themes = ThemePreference.choices
                    StapelTabs(
                        items = themes.map { stapelString(themeLabel(it)) },
                        selectedIndex = themes.indexOf(profile.theme).coerceAtLeast(0),
                        onSelect = { onIntent(ProfileSettingsIntent.SetTheme(themes[it])) },
                        variant = TabsVariant.Segmented,
                        testTag = ProfilesTestTags.THEME,
                    )
                }
            }
            if (sections.isNotEmpty()) {
                SectionHeader(stapelString(ProfilesUiStrings.SettingsSections))
                Column(Modifier.fillMaxWidth()) {
                    for (section in sections) {
                        StapelListItem(
                            headline = stapelString(StringKey(section.labelKey)),
                            accessory = ListItemAccessory.Chevron,
                            onClick = { onOpen(section.id) },
                            testTag = "${ProfilesTestTags.SECTION}.${section.id.value}",
                        )
                    }
                }
            }
        }
    }
}

private fun themeLabel(theme: ThemePreference): StringKey = when (theme) {
    ThemePreference.Light -> ProfilesStrings.SettingsThemeLight
    ThemePreference.Dark -> ProfilesStrings.SettingsThemeDark
    else -> ProfilesStrings.SettingsThemeSystem
}

/**
 * The language screen (`profiles.language`, web `LanguageSettings`): follow the device or pick an app
 * language from the server's catalogue, and the content translation switch.
 */
@Composable
public fun LanguageScreen(
    state: ProfileSettingsState,
    catalogue: LanguageCatalogueState,
    onIntent: (ProfileSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    StapelPage(modifier.testTag(ProfilesTestTags.LANGUAGE), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
            StapelScreenTitle(stapelString(ProfilesStrings.LanguageTitle))
            StapelText(stapelString(ProfilesStrings.LanguageSubtitle), color = StapelTheme.colors.textMuted)
            val profile = state.profile
            when {
                profile == null && state.loadError != null ->
                    ErrorState(state.loadError!!, onRetry = { onIntent(ProfileSettingsIntent.Refresh) }, testTag = ProfilesTestTags.ERROR)
                profile == null || catalogue.loading -> StapelProgress(modifier = Modifier.fillMaxWidth())
                else -> {
                    state.saveError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    catalogue.error?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    SectionHeader(stapelString(ProfilesStrings.LanguageFieldAppLanguage))
                    val current = profile.appLanguage?.code.takeUnless { profile.useDeviceLanguage }
                    StapelToggle(
                        ToggleKind.Radio,
                        checked = current == null,
                        onCheckedChange = { if (it) onIntent(ProfileSettingsIntent.SetLanguage(null)) },
                        label = stapelString(ProfilesUiStrings.LanguageDevice),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.saving,
                        testTag = "${ProfilesTestTags.LANGUAGE_CHOICE}.device",
                    )
                    if (catalogue.languages.isEmpty() && catalogue.error == null) {
                        EmptyState(stapelString(ProfilesStrings.LanguageCatalogueEmpty))
                    }
                    for (language in catalogue.languages) {
                        StapelToggle(
                            ToggleKind.Radio,
                            checked = current == language.code,
                            onCheckedChange = { if (it) onIntent(ProfileSettingsIntent.SetLanguage(language.code)) },
                            label = language.name,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                            testTag = "${ProfilesTestTags.LANGUAGE_CHOICE}.${language.code}",
                        )
                    }
                    StapelToggle(
                        ToggleKind.Switch,
                        checked = profile.autoTranslateContent,
                        onCheckedChange = { onIntent(ProfileSettingsIntent.SetAutoTranslate(it)) },
                        label = stapelString(ProfilesUiStrings.AutoTranslate),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = ProfilesTestTags.AUTO_TRANSLATE,
                    )
                }
            }
        }
    }
}

/**
 * The notifications screen (`profiles.notifications`, web `NotificationPreferences`): one switch per
 * category and channel, each stored as it is flipped.
 */
@Composable
public fun NotificationsScreen(state: ProfileSettingsState, onIntent: (ProfileSettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    StapelPage(modifier.testTag(ProfilesTestTags.NOTIFICATIONS), measure = PaneMeasure.Reading) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StapelTheme.spacing.s3)) {
            StapelScreenTitle(stapelString(ProfilesStrings.NotifPrefsTitle))
            StapelText(stapelString(ProfilesStrings.NotifPrefsSubtitle), color = StapelTheme.colors.textMuted)
            val prefs = state.profile?.notifications
            when {
                prefs == null && state.loadError != null ->
                    ErrorState(state.loadError!!, onRetry = { onIntent(ProfileSettingsIntent.Refresh) }, testTag = ProfilesTestTags.ERROR)
                prefs == null -> StapelProgress(modifier = Modifier.fillMaxWidth())
                else -> {
                    state.saveError?.let { ErrorState(it, testTag = ProfilesTestTags.ERROR) }
                    for (category in NotificationCategory.entries) {
                        val categoryLabel = stapelString(categoryKey(category))
                        SectionHeader(categoryLabel)
                        for (channel in NotificationChannel.entries) {
                            val channelLabel = stapelString(channelKey(channel))
                            StapelToggle(
                                ToggleKind.Switch,
                                checked = prefs.isOn(category, channel),
                                onCheckedChange = { onIntent(ProfileSettingsIntent.SetNotification(category, channel, it)) },
                                label = channelLabel,
                                modifier = Modifier.fillMaxWidth(),
                                supporting = stapelString(ProfilesStrings.NotifPrefsToggleLabel, "category" to categoryLabel, "channel" to channelLabel),
                                testTag = "${ProfilesTestTags.NOTIFICATION_SWITCH}.${category.name.lowercase()}.${channel.name.lowercase()}",
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryKey(category: NotificationCategory): StringKey = when (category) {
    NotificationCategory.Messages -> ProfilesStrings.NotifPrefsCategoryMessages
    NotificationCategory.System -> ProfilesStrings.NotifPrefsCategorySystem
}

private fun channelKey(channel: NotificationChannel): StringKey = when (channel) {
    NotificationChannel.Email -> ProfilesStrings.NotifPrefsChannelEmail
    NotificationChannel.Push -> ProfilesStrings.NotifPrefsChannelPush
}
