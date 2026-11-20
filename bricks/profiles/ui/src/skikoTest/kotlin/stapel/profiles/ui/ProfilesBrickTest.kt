package stapel.profiles.ui

import org.koin.dsl.module
import stapel.app.di.stapelApp
import stapel.cache.session.CacheSessionBinding
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.compose.navContribution
import stapel.navigation.resolveNav
import stapel.profiles.StapelProfiles
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.avatar.CdnAvatarUploader
import stapel.profiles.di.AvatarUpload
import stapel.profiles.ui.di.ProfilesBrick
import stapel.runtime.PlatformContext
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import stapel.testing.FakeApiTransport
import stapel.testing.testEnvironment
import stapel.wire.ApiTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@kotlinx.serialization.Serializable
private data object Security : Route

/** `ProfilesBrick()` is the whole profiles wiring of an app. */
class ProfilesBrickTest {
    private fun app(brick: ProfilesBrick = ProfilesBrick()) = stapelApp(PlatformContext()) {
        environment(testEnvironment())
        bricks(brick)
        overrides(module {
            single<TokenVault> { InMemoryTokenVault() }
            single<ApiTransport> { FakeApiTransport() }
            // The sign-in owner (the auth brick) binds the refresher in an app; profiles needs a session, not its own.
            single<stapel.session.TokenRefresher> { stapel.session.TokenRefresher { stapel.session.RefreshOutcome.Transient("test") } }
        })
        global(false)
    }

    @Test
    fun theDescriptorAloneWiresTheDomainNavigationStringsAndErrorTexts() {
        app().use { app ->
            assertNotNull(app.koin.getOrNull<StapelProfiles>())
            assertIs<CdnAvatarUploader>(app.koin.get<AvatarUploader>())
            assertNotNull(app.koin.getOrNull<CacheSessionBinding>(), "exactly one cache-session binding (the builder's)")
            assertEquals(listOf(PROFILES_UI_ARTIFACT), app.contributions.map { it.manifest.artifact })
            assertEquals(
                listOf("profiles.settings", "profiles.language", "profiles.notifications", "profiles.contacts", "profiles.connections", "profiles.public"),
                app.contributions.single().manifest.destinations.map { it.id.value },
            )
            assertTrue(app.strings.t(ProfilesStrings.NavSettings) != ProfilesStrings.NavSettings.key, "the profiles bundle is registered")
            assertTrue(app.contributions.single().manifest.destinations.single { it.id == ProfilesNavIds.Public }.pageSlots.contains(ProfilesNavIds.PageActions))
        }
        app(ProfilesBrick(avatarUpload = AvatarUpload.Off)).use { app ->
            assertSame(AvatarUploader.None, app.koin.get<AvatarUploader>())
            assertTrue(!app.koin.get<StapelProfiles>().canUploadAvatar)
        }
    }

    @Test
    fun anotherLibrarysDestinationUnderSettingsBecomesASection() {
        // What auth.security does: it names profiles.settings as its parent by id.
        val security = navContribution("io.github.usestapel:other", "0") {
            destination<Security>(
                id = "auth.security", path = "security", labelKey = "auth.nav.security", icon = NavIcons.ShieldCheck,
                codec = PathCodec.constant(Security), placement = Placement.Submenu(ProfilesNavIds.Settings), order = 10, content = {},
            )
        }
        app().use { app ->
            val nav = resolveNav((app.contributions + security).map { it.manifest })
            val sections = nav.byId.getValue(ProfilesNavIds.Settings).settingsSections().map { it.id.value }
            assertEquals(listOf("auth.security", "profiles.language", "profiles.notifications", "profiles.contacts"), sections)
            // Without its parent the security section is dropped with a reason, never silently.
            val alone = resolveNav(listOf(security.manifest))
            assertTrue(DestinationId("auth.security") !in alone.byId)
        }
    }
}
