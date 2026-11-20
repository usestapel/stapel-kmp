package stapel.cache.room

import kotlinx.coroutines.runBlocking
import stapel.cache.CacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.room.di.cacheRoomModule
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.stapelKoin
import stapel.runtime.NoopLogger
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDataDirectoryTest {
    private val home = createTempDirectory("stapel-home").toFile()
    private val savedHome = System.getProperty("user.home")

    @AfterTest
    fun cleanUp() {
        System.setProperty("user.home", savedHome)
        home.deleteRecursively()
    }

    private val noEnv: (String) -> String? = { null }

    @Test
    fun perOsUserDataDirectory() {
        assertEquals("/h/Library/Application Support/com.example.shop", desktopDataDirectory("com.example.shop", "Mac OS X", "/h", noEnv))
        assertEquals("/h/.local/share/com.example.shop", desktopDataDirectory("com.example.shop", "Linux", "/h", noEnv))
        assertEquals("/xdg/com.example.shop", desktopDataDirectory("com.example.shop", "Linux", "/h", mapOf("XDG_DATA_HOME" to "/xdg")::get))
        assertEquals(
            "/h/.local/share/com.example.shop",
            desktopDataDirectory("com.example.shop", "Linux", "/h", mapOf("XDG_DATA_HOME" to "relative/xdg")::get),
            "a relative XDG_DATA_HOME is ignored (XDG base directory rule)",
        )
        val windows = desktopDataDirectory("com.example.shop", "Windows 11", "/h", mapOf("LOCALAPPDATA" to "/local", "APPDATA" to "/roaming")::get)
        assertEquals(File("/local", "com.example.shop").absolutePath, windows, "the local, not the roaming, profile")
    }

    @Test
    fun anAppIdThatIsNotOneDirectoryNameIsRefused() {
        for (bad in listOf("", ".", "..", "a/b", "a\\b", "shop app")) {
            assertFailsWith<IllegalArgumentException>(bad) { desktopDataDirectory(bad, "Linux", "/h", noEnv) }
        }
    }

    @Test
    fun cacheRoomModuleWithoutADirectoryUsesTheAppsDataDirectoryOwnerOnly() = runBlocking {
        System.setProperty("user.home", home.absolutePath)
        val expected = File(desktopDataDirectory("com.example.shop"))
        if (!expected.startsWith(home)) {
            // XDG_DATA_HOME / LOCALAPPDATA point outside the temporary home: never write to the real one.
            println("skipped: the per-user data directory $expected is not under user.home")
            return@runBlocking
        }
        val app = stapelKoin(
            listOf(runtimeModule(PlatformContext(), NoopLogger), module { single { environment() } }, cacheRoomModule()),
            global = false,
        )
        try {
            val stores = app.koin.get<CacheStoreFactory>().open(PartitionId.App)
            stores.close()
        } finally {
            app.close()
        }
        val root = File(expected, "stapel-cache")
        assertTrue(File(root, "app/cache.db").isFile, "cache.db under $root")
        if (posix()) {
            assertEquals("rwx------", permissions(root.toPath()))
            assertEquals("rwx------", permissions(File(root, "app").toPath()))
        }
    }

    @Test
    fun withoutAnEnvironmentTheDesktopDefaultNamesBothFixes() {
        val app = stapelKoin(listOf(runtimeModule(PlatformContext(), NoopLogger), cacheRoomModule()), global = false)
        try {
            val e = runCatching { app.koin.get<CacheStoreFactory>() }.exceptionOrNull()
            val message = generateSequence(e) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
            assertTrue("StapelEnvironment" in message && "cacheRoomModule(directory" in message, message)
        } finally {
            app.close()
        }
    }

    @Test
    fun aWiderCacheDirectoryIsTightenedToOwnerOnly() = runBlocking {
        if (!posix()) return@runBlocking
        val root = File(home, "stapel-cache").apply { mkdirs() }
        Files.setPosixFilePermissions(root.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        val factory = RoomCacheStoreFactory(home.absolutePath, RoomCacheOptions(kotlinx.coroutines.Dispatchers.IO))
        factory.open(PartitionId.account("account-1")).close()
        assertEquals("rwx------", permissions(root.toPath()))
        assertEquals("rwx------", permissions(factory.partitionDir(PartitionId.account("account-1")).toString().let { Path.of(it) }))
    }

    private fun environment() = StapelEnvironment(
        name = "test",
        posture = Posture.Prototype,
        client = ClientInfo("com.example.shop", "1.0", "1", ClientPlatform.Desktop, "Test OS", "1", null),
    )

    private fun posix() = "posix" in java.nio.file.FileSystems.getDefault().supportedFileAttributeViews()

    private fun permissions(path: Path) = PosixFilePermissions.toString(Files.getPosixFilePermissions(path))
}
