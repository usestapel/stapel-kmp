package stapel.session.vault

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import stapel.session.SessionKind
import stapel.session.TokenPair
import kotlin.time.Instant

/** The persisted session record: written as one unit. */
public class StoredSession(
    /** The token pair. */
    public val tokens: TokenPair,
    /** Principal kind. */
    public val kind: SessionKind,
    /**
     * Set just before a refresh call and cleared when its outcome is persisted. Found on start, it
     * means a process died mid-rotation (see `SessionConfig.rotationHandoffWindow`).
     */
    public val rotationStartedAt: Instant? = null,
) {
    /** Kind and marker only: tokens are never printed. */
    override fun toString(): String = "StoredSession($kind, rotating=${rotationStartedAt != null})"
}

/**
 * Secure storage of one [StoredSession]. Implementations do blocking work on their own I/O
 * dispatcher (main-safe) and never fall back to plaintext.
 */
public interface TokenVault {
    /** Reads the record. */
    public suspend fun read(): VaultRead

    /** Replaces the record atomically. Throws when the record could not be written. */
    public suspend fun write(session: StoredSession)

    /** Deletes the record. */
    public suspend fun clear()
}

/** Result of [TokenVault.read]. */
public sealed interface VaultRead {
    /** Nothing stored. */
    public data object Empty : VaultRead

    /** A record. */
    public class Present(
        /** The record. */
        public val session: StoredSession,
    ) : VaultRead

    /** Something is stored but cannot be decrypted or parsed. */
    public class Unreadable(
        /** Diagnostic text (never contains token material). */
        public val reason: String,
    ) : VaultRead
}

/** Platform vault options. */
public data class TokenVaultConfig(
    /** Name of the record (file name, Keychain service suffix, Keystore alias). */
    val namespace: String = "stapel.session",
    /** iOS only: Keychain access group for sharing with app extensions. */
    val iosAccessGroup: String? = null,
)

/** A vault in memory: for tests, and the honest choice for a kiosk that must forget on exit. */
public class InMemoryTokenVault(initial: StoredSession? = null) : TokenVault {
    private val lock = Mutex()
    private var record: StoredSession? = initial

    override suspend fun read(): VaultRead = lock.withLock { record?.let { VaultRead.Present(it) } ?: VaultRead.Empty }

    override suspend fun write(session: StoredSession) {
        lock.withLock { record = session }
    }

    override suspend fun clear() {
        lock.withLock { record = null }
    }
}
