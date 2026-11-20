@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.image.compose

import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import stapel.session.RefreshOutcome
import stapel.session.SessionDependencies
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenPair
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.StoredSession
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** The image caches go with the session: a sign-out clears Coil's memory and disk caches through the session's logout hook. */
class SessionEndClearsImagesTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }

    private fun TestScope.signedIn(): SessionManager {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val access = b64.encode("{\"alg\":\"none\"}".encodeToByteArray()) + "." +
            b64.encode("{\"user_id\":7,\"exp\":4102444800}".encodeToByteArray()) + ".sig"
        return SessionManager(
            SessionDependencies(
                vault = InMemoryTokenVault(StoredSession(TokenPair(access, "refresh"), SessionKind.Authenticated)),
                scope = backgroundScope,
                clock = clock,
            ),
            refresher = { RefreshOutcome.Transient("offline in tests") },
        )
    }

    private fun loader(): ImageLoader {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stapel-image-cache-${Random.nextLong().toULong()}"
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(1_000_000).build() }
            .diskCache { DiskCache.Builder().directory(dir).maxSizeBytes(1_000_000).build() }
            .build()
    }

    private fun ImageLoader.seed() {
        memoryCache!![MemoryCache.Key("avatar/a")] = MemoryCache.Value(ColorImage(0xFF00FF00.toInt(), 4, 4))
        val disk = diskCache!!
        val editor = assertNotNull(disk.openEditor("avatar/a"))
        disk.fileSystem.write(editor.data) { writeUtf8("png bytes") }
        editor.commitAndOpenSnapshot()?.close()
        assertNotNull(disk.openSnapshot("avatar/a")).close()
        assertNotNull(memoryCache!![MemoryCache.Key("avatar/a")])
    }

    @Test
    fun aSignOutClearsTheMemoryAndDiskCaches() = runTest {
        val session = signedIn()
        session.restore()
        runCurrent()
        assertIs<SessionState.Authenticated>(session.state.value)
        val loader = loader()
        loader.seed()
        loader.clearCachesOnSessionEnd(session)

        session.logout()
        runCurrent()
        assertNull(loader.memoryCache!![MemoryCache.Key("avatar/a")], "the memory cache outlived the session")
        assertNull(loader.diskCache!!.openSnapshot("avatar/a"), "the disk cache outlived the session")
    }

    @Test
    fun aClosedHandleStopsClearing() = runTest {
        val session = signedIn()
        session.restore()
        runCurrent()
        val loader = loader()
        loader.seed()
        loader.clearCachesOnSessionEnd(session).close()

        session.logout()
        runCurrent()
        assertNotNull(loader.diskCache!!.openSnapshot("avatar/a"), "the harness sees a kept disk entry").close()
        assertNotNull(loader.memoryCache!![MemoryCache.Key("avatar/a")], "the harness sees a kept memory entry")
    }
}
