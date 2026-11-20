package stapel.analytics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Where the hub keeps its state between launches: the consent answer (`consent`), the undelivered
 * events (`queue`) and its own anonymous id (`anon`). Values are the hub's own text; keys are
 * lower-case words. Writes replace the value whole; null deletes it.
 */
public interface AnalyticsStorage {
    /** The value under [key], or null. */
    public suspend fun read(key: String): String?

    /** Replaces (null: deletes) the value under [key]. */
    public suspend fun write(key: String, value: String?)
}

/** Keeps everything in memory (tests, previews). */
public class InMemoryAnalyticsStorage : AnalyticsStorage {
    private val lock = Mutex()
    private val values = mutableMapOf<String, String>()

    /** A copy of everything stored. */
    public suspend fun snapshot(): Map<String, String> = lock.withLock { values.toMap() }

    override suspend fun read(key: String): String? = lock.withLock { values[key] }

    override suspend fun write(key: String, value: String?): Unit = lock.withLock {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/**
 * One file per key in [directory] (created on first write), each replaced atomically through a
 * temporary file. Platform entry point: [platformAnalyticsStorage].
 *
 * @param directory absolute path of the directory that holds the files.
 * @param ioContext where file work runs: the app's IO dispatcher.
 */
public class FileAnalyticsStorage(directory: String, private val ioContext: CoroutineContext) : AnalyticsStorage {
    private val root = Path(directory)
    private val lock = Mutex()

    override suspend fun read(key: String): String? = lock.withLock {
        withContext(ioContext) {
            val file = fileOf(key)
            if (!SystemFileSystem.exists(file)) null else SystemFileSystem.source(file).buffered().use { it.readString() }
        }
    }

    override suspend fun write(key: String, value: String?): Unit = lock.withLock {
        withContext(ioContext) {
            val file = fileOf(key)
            if (value == null) {
                if (SystemFileSystem.exists(file)) SystemFileSystem.delete(file)
                return@withContext
            }
            SystemFileSystem.createDirectories(root)
            val tmp = Path(root, "$key.tmp")
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(value) }
            SystemFileSystem.atomicMove(tmp, file)
        }
    }

    private fun fileOf(key: String): Path {
        require(KEY.matches(key)) { "analytics storage key '$key' must be lower-case letters" }
        return Path(root, "$key.json")
    }

    private companion object {
        val KEY = Regex("[a-z]+")
    }
}

/**
 * The default storage: [FileAnalyticsStorage] in the app's private, non-backed-up directory —
 * Android `noBackupFilesDir/<namespace>`, iOS `Application Support/<namespace>`, desktop the user's
 * data directory (`~/Library/Application Support`, `%APPDATA%`, `$XDG_DATA_HOME`) `/<namespace>`.
 */
public expect fun platformAnalyticsStorage(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    namespace: String = "stapel-analytics",
): AnalyticsStorage
