package stapel.profiles.testing

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsState
import stapel.profiles.ui.ProfileSettingsScreen
import stapel.profiles.ui.ProfilesUiBundle
import stapel.profiles.ui.SettingsSection
import stapel.tokens.ThemeMode
import stapel.ui.StapelTheme
import stapel.ui.skin.FoundationSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class ProfilesWalkerTest {
    @Test
    fun theWalkersDriveTheRealSettingsScreenAndNameTheStepThatDidNotHappen() = runComposeUiTest {
        val intents = mutableListOf<ProfileSettingsIntent>()
        val opened = mutableListOf<DestinationId>()
        val strings = StapelStrings { register(CoreUiBundle); register(ProfilesUiBundle) }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) {
                ProfileSettingsScreen(
                    ProfileSettingsState(profile = ProfilesSamples.me, loading = false),
                    listOf(SettingsSection(DestinationId("profiles.language"), "profiles.nav.language", NavIcons.Message)),
                    { intents += it },
                    { opened += it },
                    null,
                )
            }
        }
        // The name is never "stored" here (no presenter answers), so the walk must say which step stalled.
        val e = assertFailsWith<ProfilesWalkException> { renameInSettings("Grace", timeoutMillis = 500) }
        assertEquals(true, "is stored" in e.message.orEmpty(), e.message)
        assertEquals(listOf<ProfileSettingsIntent>(ProfileSettingsIntent.SetDisplayName("Grace")), intents)
        assertFailsWith<ProfilesWalkException> { openSettingsSection("profiles.language", expectTag = "never.there", timeoutMillis = 500) }
        assertEquals(listOf(DestinationId("profiles.language")), opened)
    }
}
