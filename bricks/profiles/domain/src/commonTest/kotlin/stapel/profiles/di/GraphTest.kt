@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.profiles.di

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cache.di.cacheModule
import stapel.di.stapelKoin
import stapel.mvi.PresenterContext
import stapel.network.WireBody
import stapel.network.WirePart
import stapel.network.di.networkModule
import stapel.profiles.StapelProfiles
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.avatar.CdnAvatarUploader
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.picture
import stapel.profiles.presentation.PublicProfilePresenter
import stapel.result.StapelResult
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.session.RefreshOutcome
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.TokenPair
import stapel.session.TokenRefresher
import stapel.session.di.sessionModule
import stapel.session.vault.InMemoryTokenVault
import stapel.testing.FakeTransport
import stapel.testing.TestDispatchers
import stapel.testing.di.testOverridesModule
import stapel.testing.testEnvironment
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** `profilesModule()` composed with the substrate it needs, over the real network pipeline and a fake wire. */
class GraphTest {
    private val refresher: Module = module { single<TokenRefresher> { TokenRefresher { RefreshOutcome.Transient("tests") } } }

    private fun token(userId: String): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return "eyJhbGciOiJub25lIn0." + b64.encode("""{"user_id":"$userId","exp":4102444800}""".encodeToByteArray()) + ".sig"
    }

    private fun graph(scope: kotlinx.coroutines.test.TestScope, wire: FakeTransport, upload: AvatarUpload = AvatarUpload.Cdn) = stapelKoin(
        modules = listOf(
            runtimeModule(object : PlatformContext {}),
            networkModule(testEnvironment()),
            sessionModule(),
            cacheModule(),
            refresher,
            profilesModule(upload),
        ),
        overrides = listOf(
            testOverridesModule(
                appScope = scope.backgroundScope,
                dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
                transport = wire,
                vault = InMemoryTokenVault(),
            ),
        ),
        global = false,
    )

    @OptIn(KoinInternalApi::class)
    @Test
    fun everyBindingResolves() = runTest {
        val app = graph(this, FakeTransport())
        try {
            val koin = app.koin
            val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
                .filter { it.scopeQualifier == koin.scopeRegistry.rootScope.scopeQualifier && it.kind == org.koin.core.definition.Kind.Singleton }
            for (d in definitions) koin.get<Any>(d.primaryType, d.qualifier)
            assertIs<CdnAvatarUploader>(koin.get<AvatarUploader>())
            assertTrue(koin.get<StapelProfiles>().canUploadAvatar)
            val ctx = PresenterContext(backgroundScope)
            koin.get<PublicProfilePresenter> { org.koin.core.parameter.parametersOf(ctx, "u2", true) }
        } finally {
            app.close()
        }
        val off = graph(this, FakeTransport(), AvatarUpload.Off)
        try {
            assertSame(AvatarUploader.None, off.koin.get<AvatarUploader>())
        } finally {
            off.close()
        }
    }

    @Test
    fun theAvatarIsAMultipartUploadToTheCdnThenOnePatchWithReferenceAndSource() = runTest {
        val wire = FakeTransport()
        val app = graph(this, wire)
        try {
            val koin = app.koin
            val session = koin.get<SessionManager>()
            session.restore()
            session.adopt(TokenPair(token("u1"), "r"), SessionKind.Authenticated)
            runCurrent()
            val hash = "a".repeat(64)
            wire.enqueueJson(201, """{"image":{"id":1,"prefix":"avatar/$hash","type":"avatar"}}""")
            wire.enqueueJson(200, ME.replace("AVATAR", "avatar/$hash"))
            val r = koin.get<StapelProfiles>().me.setAvatar(picture())
            assertEquals("avatar/$hash", (r as StapelResult.Ok).value.avatarRef?.ref)

            val (upload, patch) = wire.requests
            assertEquals("POST", upload.method)
            assertEquals("https://api.test/cdn/api/v1/upload/avatar/", upload.url)
            assertEquals("Bearer ${token("u1")}", upload.header("Authorization"))
            val part = (upload.body as WireBody.Multipart).parts.single() as WirePart.File
            assertEquals("file", part.name)
            assertEquals("image/png", part.contentType)
            assertEquals(listOf<Byte>(1, 2, 3, 4), part.bytes.toList())

            assertEquals("PATCH", patch.method)
            assertEquals("https://api.test/profiles/api/v1/me", patch.url)
            val body = (patch.body as WireBody.Bytes).text()
            assertTrue("\"avatar\":\"avatar/$hash\"" in body && "\"avatar_source\":\"cdn\"" in body, body)
        } finally {
            app.close()
        }
    }

    @Test
    fun aRefusedFileIsAnInvalidAvatarAndNothingIsPatched() = runTest {
        val wire = FakeTransport()
        val app = graph(this, wire)
        try {
            val session = app.koin.get<SessionManager>()
            session.restore()
            session.adopt(TokenPair(token("u1"), "r"), SessionKind.Authenticated)
            runCurrent()
            wire.enqueueEnvelope(400, "error.400.invalid_image")
            val r = app.koin.get<StapelProfiles>().me.setAvatar(picture())
            assertEquals(ProfilesErrorReason.InvalidAvatar, (r as StapelResult.Err).error.reason)
            assertEquals(1, wire.requests.size)
            assertEquals(RelationshipStatus.Neutral, RelationshipStatus.of("neutral"))
        } finally {
            app.close()
        }
    }

    private companion object {
        const val ME = """{"user_id":"u1","display_name":"Ada","theme":"system","avatar_source":"cdn","avatar":"AVATAR","avatar_image":null,
            "location_id":null,"location_display_name_narrow":"","location_display_name_broad":"","app_language":null,"understands":[],
            "use_device_language":true,"auto_detected_language":"","auto_translate_content":false,"email_messages":true,"email_system":true,
            "push_messages":true,"push_system":true,"essential_cookies_accepted":false,"initial_setup_passed":true,"followers_count":0,
            "following_count":0,"created_at":"2026-01-02T03:04:05Z","updated_at":"2026-01-02T03:04:05Z"}"""
    }
}
