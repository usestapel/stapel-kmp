package shop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import org.koin.dsl.module
import stapel.app.di.StapelAppContent
import stapel.auth.testing.AuthTags
import stapel.auth.testing.awaitTag
import stapel.auth.testing.signInWithMockOtp
import stapel.profiles.StapelProfiles
import stapel.profiles.testing.ProfilesTags
import stapel.profiles.testing.chooseLanguage
import stapel.profiles.testing.openSettingsSection
import stapel.profiles.testing.renameInSettings
import stapel.profiles.ui.AvatarPicker
import stapel.profiles.ui.SettingsRoute
import stapel.result.state.Resource
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import stapel.wire.FilePart
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The generated app with the profiles brick, walked like a person against a scaffolded stand with auth,
 * profiles and cdn (prototype posture) at PROFILES_WALK_BASE_URL: sign in with the mock code, open the
 * settings, rename, reach the auth security screen from inside the settings, choose a language, and
 * change the avatar through a picker (uploaded to stapel-cdn, stored, decoded with its variant ladder). Compiled in only with `-Pstapel.walk.profilesBaseUrl=<url>`;
 * runs on the JVM and on the iOS simulator.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilesWalkTest {
    private val picker = AvatarPicker {
        val bytes = Base64.decode(PICTURE)
        FilePart("walk.png", "image/png", bytes.size.toLong()) { Buffer().apply { write(bytes) } }
    }

    @Test
    fun aCustomerSetsUpTheProfileFromTheSettings() = runComposeUiTest {
        val app = createShopApp(testPlatformContext(), testTarget, PROFILES_WALK_BASE_URL, avatarPicker = picker) {
            overrides(module { single<TokenVault> { InMemoryTokenVault() } })
            global(false)
        }
        try {
            setContent { StapelAppContent(app) }
            signInWithMockOtp("walk+${Random.nextLong(1_000_000_000L)}@example.com")
            awaitTag("shop.home")

            // Settings: the container of profiles' sections and of auth's security screen.
            runOnIdle { checkNotNull(app.navigator.value).navigate(SettingsRoute) }
            awaitTag(ProfilesTags.SETTINGS)
            renameInSettings("Walker ${Random.nextInt(1000)}")
            openSettingsSection("auth.security", expectTag = AuthTags.SESSIONS)
            runOnIdle { checkNotNull(app.navigator.value).back() }
            awaitTag(ProfilesTags.SETTINGS)

            openSettingsSection("profiles.language", expectTag = ProfilesTags.LANGUAGE)
            chooseLanguage("ru")
            waitUntil(timeoutMillis = 20_000) {
                val ru = hasTestTag("${ProfilesTags.LANGUAGE_CHOICE}.ru")
                onAllNodes(ru and (isOn() or isSelected()), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            runOnIdle { checkNotNull(app.navigator.value).back() }
            awaitTag(ProfilesTags.SETTINGS)

            // The avatar: picked, uploaded to stapel-cdn (the app installs cdnModule()), stored with one PATCH;
            // the answer decodes with its variant ladder and the profile carries the image.
            val watcher = CoroutineScope(Dispatchers.Default)
            val avatarSeen = MutableStateFlow(false)
            watcher.launch {
                app.koin.get<StapelProfiles>().me.me().collect { if ((it as? Resource.Ready)?.data?.avatar != null) avatarSeen.value = true }
            }
            try {
                onNode(hasTestTag(ProfilesTags.AVATAR_CHANGE)).performScrollTo().performClick()
                waitUntil(timeoutMillis = 30_000) { avatarSeen.value || onAllNodes(hasTestTag(ProfilesTags.ERROR)).fetchSemanticsNodes().isNotEmpty() }
                if (!avatarSeen.value) fail("the avatar change ended in an error on screen instead of a stored, decoded avatar")
                waitUntil(timeoutMillis = 20_000) { onAllNodes(hasTestTag(ProfilesTags.AVATAR_CHANGE) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                assertTrue(onAllNodes(hasTestTag(ProfilesTags.ERROR)).fetchSemanticsNodes().isEmpty(), "no error after the avatar change")
            } finally {
                watcher.cancel()
            }
        } finally {
            app.close()
        }
    }

    private companion object {
        /** A 16 x 16 RGB PNG (the CDN decodes and resizes it). */
        const val PICTURE: String =
            "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAABlklEQVR4nBXRURVEIQhFUSMYgQhGMAIRiGCEE8EIRiACEYhABCLMG7/ZrMt1jMEcyGAN9kAHNjgDBnfwBj6IQQ5q0IMxJnMikzXZE53Y5EyY3Mmb+CQmOalJzw8IUxBhCVtQwYQjIFzhCS6EkEIJLR9YzIUs1mIvdGGLs2BxF2/hi1jkoha9PrCZG9mszd7oxjZnw+Zu3sY3sclNbXp/QJmKKEvZiiqmHAXlKk9xJZRUSmn9gDENMZaxDTXMOAbGNZ7hRhhplNH2gcM8yGEd9kEPdjgHDvfwDn6IQx7q0OcD/wK/Sr4jv9hfkG/1N/x/Fx44BCQU9Pc94zIvclmXfdGLXc79j9/Lu/glLnmpS98PPOZDHuuxH/qwx3n/5ffxHv6IRz7q0e8DznTEWc521DHn+D/KdZ7jTjjplNP+gWAGEqxgBxpYcOIf/AYv8CCCDCro+EAyE0lWshNNLDn5P/MmL/Ekkkwq6fxAMQspVrELLaw49S/lFq/wIoosquj6QDMbaVazG22sOf2v8Dav8SaabKrp5geIAnAQEbP2+wAAAABJRU5ErkJggg=="
    }
}
