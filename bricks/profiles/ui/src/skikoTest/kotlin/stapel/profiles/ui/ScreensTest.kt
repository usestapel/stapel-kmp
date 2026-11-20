package stapel.profiles.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import stapel.navigation.DestinationId
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.model.ThemePreference
import stapel.profiles.presentation.ConnectionsIntent
import stapel.profiles.presentation.ConnectionsState
import stapel.profiles.presentation.ContactsIntent
import stapel.profiles.presentation.ContactsState
import stapel.profiles.presentation.LanguageCatalogueState
import stapel.profiles.presentation.PendingVerification
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsState
import stapel.profiles.presentation.PublicProfileIntent
import stapel.profiles.presentation.PublicProfileState
import stapel.profiles.presentation.RelationshipAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    private fun ComposeUiTest.exists(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun settingsRenameThemeAndSectionsInBothSkinsAndLocales() = runComposeUiTest {
        val intents = Events<ProfileSettingsIntent>()
        val opened = Events<DestinationId>()
        val h = harness { ProfileSettingsScreen(ProfileSettingsState(profile = ada, loading = false, canUploadAvatar = true), sections, intents.sink, opened.sink, onPickAvatar = {}) }
        eachSkinAndLocale(h) { skin, locale ->
            assertTrue(exists(ProfilesTestTags.AVATAR_CHANGE), "$skin/$locale: avatar change offered")
            assertTrue(exists("${ProfilesTestTags.SECTION}.auth.security"), "$skin/$locale: another library's section is listed")
        }
        h.locale = "en"
        waitForIdle()
        onNode(hasTestTag(ProfilesTestTags.DISPLAY_NAME)).performTextClearance()
        onNode(hasTestTag(ProfilesTestTags.DISPLAY_NAME)).performTextInput("Grace")
        onNode(hasTestTag(ProfilesTestTags.DISPLAY_NAME_SAVE)).performClick()
        onNode(hasText("Dark")).performClick()
        onNode(hasTestTag("${ProfilesTestTags.SECTION}.profiles.language")).performClick()
        waitForIdle()
        assertEquals(listOf<ProfileSettingsIntent>(ProfileSettingsIntent.SetDisplayName("Grace"), ProfileSettingsIntent.SetTheme(ThemePreference.Dark)), intents.all)
        assertEquals(listOf(DestinationId("profiles.language")), opened.all)
    }

    @Test
    fun withoutAPickerOrAnUploaderNoAvatarChangeIsOffered() = runComposeUiTest {
        val h = harness { ProfileSettingsScreen(ProfileSettingsState(profile = ada, loading = false, canUploadAvatar = true), emptyList(), {}, {}, onPickAvatar = null) }
        waitForIdle()
        assertTrue(!exists(ProfilesTestTags.AVATAR_CHANGE))
        h.content = { ProfileSettingsScreen(ProfileSettingsState(profile = ada, loading = false, canUploadAvatar = false), emptyList(), {}, {}, onPickAvatar = {}) }
        waitForIdle()
        assertTrue(!exists(ProfilesTestTags.AVATAR_CHANGE))
    }

    @Test
    fun languageChoicesAndTheTranslationSwitch() = runComposeUiTest {
        val intents = Events<ProfileSettingsIntent>()
        harness { LanguageScreen(ProfileSettingsState(profile = ada, loading = false), LanguageCatalogueState(languages, loading = false), intents.sink) }
        waitForIdle()
        onNode(hasTestTag("${ProfilesTestTags.LANGUAGE_CHOICE}.en")).performClick()
        onNode(hasTestTag("${ProfilesTestTags.LANGUAGE_CHOICE}.device")).performClick()
        onNode(hasTestTag(ProfilesTestTags.AUTO_TRANSLATE)).performClick()
        waitForIdle()
        assertEquals(
            listOf<ProfileSettingsIntent>(ProfileSettingsIntent.SetLanguage("en"), ProfileSettingsIntent.SetLanguage(null), ProfileSettingsIntent.SetAutoTranslate(true)),
            intents.all,
        )
    }

    @Test
    fun aNotificationSwitchNamesItsCategoryAndChannel() = runComposeUiTest {
        val intents = Events<ProfileSettingsIntent>()
        harness { NotificationsScreen(ProfileSettingsState(profile = ada, loading = false), intents.sink) }
        waitForIdle()
        onNode(hasTestTag("${ProfilesTestTags.NOTIFICATION_SWITCH}.messages.push")).performClick()
        waitForIdle()
        assertEquals(listOf<ProfileSettingsIntent>(ProfileSettingsIntent.SetNotification(NotificationCategory.Messages, NotificationChannel.Push, true)), intents.all)
    }

    @Test
    fun aNumberIsMaskedUntilShownAndConfirmedWithItsCode() = runComposeUiTest {
        val intents = Events<ContactsIntent>()
        val h = harness { ContactsScreen(ContactsState(contacts = numbers, loading = false), intents.sink) }
        waitForIdle()
        assertTrue(onAllNodes(hasText("+15550142")).fetchSemanticsNodes().isEmpty(), "masked until shown")
        assertTrue(onAllNodes(hasText("•••• 42")).fetchSemanticsNodes().isNotEmpty())
        onNode(hasText("Confirm by SMS")).performClick()
        h.content = { ContactsScreen(ContactsState(contacts = numbers, loading = false, verification = PendingVerification(1, 300)), intents.sink) }
        waitForIdle()
        onNode(hasTestTag(ProfilesTestTags.CONTACT_CODE)).performTextInput("0000")
        onNode(hasText("Confirm")).performClick()
        onNode(hasTestTag(ProfilesTestTags.CONTACT_NUMBER)).performScrollTo().performTextInput("+15550199")
        onNode(hasTestTag(ProfilesTestTags.CONTACT_ADD)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf<ContactsIntent>(ContactsIntent.RequestCode(1), ContactsIntent.ConfirmCode("0000"), ContactsIntent.Add("+15550199", "")), intents.all)
    }

    @Test
    fun connectionsSwitchListsAndOpenPeople() = runComposeUiTest {
        val intents = Events<ConnectionsIntent>()
        val opened = Events<String>()
        harness { ConnectionsScreen(ConnectionsState(kind = ConnectionKind.Following, people = people, loading = false), intents.sink, opened.sink) }
        waitForIdle()
        onNode(hasTestTag("${ProfilesTestTags.PERSON}.u2")).performClick()
        onNode(hasText("Blocked")).performClick()
        waitForIdle()
        assertEquals(listOf("u2"), opened.all, "a person without a profile is not a link")
        assertEquals<ConnectionsIntent>(ConnectionsIntent.Show(ConnectionKind.Blocked), intents.all.last())
    }

    @Test
    fun blockingAsksFirstAndTheSelfAndAnonymousViewersGetNoActions() = runComposeUiTest {
        val intents = Events<PublicProfileIntent>()
        val h = harness { PublicProfileScreen(PublicProfileState("u2", grace(), loading = false, signedIn = true), intents.sink, pageActions = {}) }
        waitForIdle()
        onNode(hasTestTag("${ProfilesTestTags.PUBLIC_ACTION}.block")).performClick()
        waitForIdle()
        assertEquals(emptyList<PublicProfileIntent>(), intents.all, "nothing until confirmed")
        onAllNodes(hasText("Block")).fetchSemanticsNodes().size.let { assertTrue(it >= 2, "the dialog's confirm") }
        onAllNodes(hasText("Block"))[1].performClick()
        waitForIdle()
        assertEquals(listOf<PublicProfileIntent>(PublicProfileIntent.Act(RelationshipAction.Block)), intents.all)
        h.content = { PublicProfileScreen(PublicProfileState("u1", grace(RelationshipStatus.Self), loading = false, signedIn = true), {}, pageActions = {}) }
        waitForIdle()
        assertTrue(!exists("${ProfilesTestTags.PUBLIC_ACTION}.follow"))
        h.content = { PublicProfileScreen(PublicProfileState("u2", grace(), loading = false, signedIn = false), {}, pageActions = {}) }
        waitForIdle()
        assertTrue(!exists("${ProfilesTestTags.PUBLIC_ACTION}.follow"))
    }

    @Test
    fun everyScreenSurvivesLargeTextInBothLocales() = runComposeUiTest {
        val h = harness()
        h.fontScale = 2f
        // sections carry auth's label key; register nothing of auth here, so draw the settings without them.
        val screens = listOf<@androidx.compose.runtime.Composable () -> Unit>(
            { ProfileSettingsScreen(ProfileSettingsState(profile = ada, loading = false, canUploadAvatar = true), sections.drop(1), {}, {}, {}) },
            { LanguageScreen(ProfileSettingsState(profile = ada, loading = false), LanguageCatalogueState(languages, loading = false), {}) },
            { NotificationsScreen(ProfileSettingsState(profile = ada, loading = false), {}) },
            { ContactsScreen(ContactsState(contacts = numbers, loading = false), {}) },
            { ConnectionsScreen(ConnectionsState(people = people, loading = false), {}, {}) },
            { PublicProfileScreen(PublicProfileState("u2", grace(), loading = false, signedIn = true), {}, pageActions = {}) },
        )
        for (screen in screens) {
            h.content = screen
            eachSkinAndLocale(h) { skin, locale ->
                assertTrue(onAllNodes(hasTestTag("test.root")).fetchSemanticsNodes().isNotEmpty())
                // Every string resolved: a missing key would show itself ("profiles.…") on screen.
                val raw = onAllNodes(hasText("profiles.", substring = true)).fetchSemanticsNodes()
                assertTrue(raw.isEmpty(), "$skin/$locale: unresolved keys on screen")
            }
        }
    }
}
