package stapel.profiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
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
import stapel.result.text.ErrorTextResolver
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelErrorText
import stapel.ui.StapelInput
import stapel.ui.StapelTheme
import stapel.ui.material3.Material3Skin
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.stapelLayout

enum class TestSkin { Foundation, Material3 }

class Harness {
    var skin by mutableStateOf(TestSkin.Foundation)
    var locale by mutableStateOf("en")
    var fontScale by mutableStateOf(1f)
    var content by mutableStateOf<@Composable () -> Unit>({})

    @Composable
    fun Render() {
        key(skin, locale) {
            val strings = remember { StapelStrings { initialLocale = locale; register(CoreUiBundle); register(ProfilesUiBundle) } }
            val layout = stapelLayout(StapelDefaultTokens, 360.dp, input = StapelInput.Touch, platformTouchFloor = 48.dp)
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalStapelErrorText provides ErrorTextResolver(profilesErrorCatalogs()),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                val body: @Composable () -> Unit = { Box(Modifier.width(360.dp).testTag("test.root")) { content() } }
                if (skin == TestSkin.Material3) {
                    StapelMaterialTheme(mode = ThemeMode.Light, skin = Material3Skin, strings = strings, layout = layout, content = body)
                } else {
                    StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layout = layout, content = body)
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.harness(content: @Composable () -> Unit = {}): Harness {
    val h = Harness()
    h.content = content
    setContent { h.Render() }
    return h
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.eachSkinAndLocale(h: Harness, block: (TestSkin, String) -> Unit) {
    for (s in TestSkin.entries) for (l in listOf("en", "ru")) {
        h.skin = s
        h.locale = l
        waitForIdle()
        block(s, l)
    }
}

class Events<E> {
    val all = mutableListOf<E>()
    val sink: (E) -> Unit = { all += it }
}

val ada = MyProfile(
    userId = "u1", displayName = "Ada", theme = ThemePreference.System, avatar = null, avatarRef = null, location = "LU",
    appLanguage = Language("ru", "Russian", null), useDeviceLanguage = false, autoTranslateContent = false, understands = emptyList(),
    notifications = NotificationPreferences(emailMessages = true, emailSystem = true, pushMessages = false, pushSystem = true),
    initialSetupPassed = true, followersCount = 2, followingCount = 3, createdAt = "2026-01-02T03:04:05Z",
)

val languages = listOf(Language("en", "English", null), Language("ru", "Russian", null))

fun grace(relationship: RelationshipStatus = RelationshipStatus.Neutral) = PublicProfile(
    userId = "u2", displayName = "Grace", avatar = null, location = "Paris", followersCount = 1, followingCount = 1,
    relationship = relationship, sellerType = null, createdAt = null, hasPhone = false,
)

val sections = listOf(
    SettingsSection(DestinationId("auth.security"), "auth.nav.security", NavIcons.ShieldCheck),
    SettingsSection(DestinationId("profiles.language"), "profiles.nav.language", NavIcons.Message),
)

val numbers = Contacts(
    listOf(Contact(1, "+15550142", "Work", ContactPolicy.Members, enabled = true, verified = false, revealCount = 0)),
    listOf(ContactPolicy.Members, ContactPolicy.Verified, ContactPolicy.Nobody),
)

val people = listOf(Person("u2", grace()), Person("u9", null))
