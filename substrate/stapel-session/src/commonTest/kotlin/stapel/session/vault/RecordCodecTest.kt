package stapel.session.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import stapel.runtime.StapelDispatchers
import stapel.session.SessionKind
import stapel.session.TokenPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

internal object TestDispatchers : StapelDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Default
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.Default
}

internal val sample = StoredSession(
    TokenPair("access-1", "refresh-1", Instant.fromEpochMilliseconds(1_800_000_000_123)),
    SessionKind.Anonymous,
    Instant.fromEpochMilliseconds(1_800_000_000_456),
)

internal fun assertSameRecord(expected: StoredSession, actual: StoredSession) {
    assertEquals(expected.tokens, actual.tokens)
    assertEquals(expected.kind, actual.kind)
    assertEquals(expected.rotationStartedAt, actual.rotationStartedAt)
}

class RecordCodecTest {
    @Test
    fun roundTrip() {
        assertSameRecord(sample, decodeRecord(encodeRecord(sample))!!)
        val bare = StoredSession(TokenPair("a", "r"), SessionKind.Authenticated)
        assertSameRecord(bare, decodeRecord(encodeRecord(bare))!!)
    }

    @Test
    fun foreignBytesAreNotARecord() {
        assertNull(decodeRecord("garbage".encodeToByteArray()))
        assertNull(decodeRecord("{\"v\":2,\"access\":\"a\",\"refresh\":\"r\",\"kind\":\"Authenticated\"}".encodeToByteArray()))
        assertNull(decodeRecord("{\"v\":1,\"access\":\"a\",\"refresh\":\"r\",\"kind\":\"Admin\"}".encodeToByteArray()))
    }

    @Test
    fun inMemoryVault() = runTest {
        val vault = InMemoryTokenVault()
        assertIs<VaultRead.Empty>(vault.read())
        vault.write(sample)
        assertSameRecord(sample, (vault.read() as VaultRead.Present).session)
        vault.clear()
        assertIs<VaultRead.Empty>(vault.read())
    }
}

class InstallationIdentityTest {
    @Test
    fun createdOnceThenStable() = runTest {
        var stored: String? = null
        var n = 0
        val ids = stapel.runtime.IdGenerator { "id-${++n}" }
        val identity = stapel.session.StoredInstallationIdentity(ids, load = { stored }, save = { stored = it })
        assertEquals("id-1", identity.deviceId())
        assertEquals("id-1", identity.deviceId())
        assertEquals("id-1", stapel.session.StoredInstallationIdentity(ids, load = { stored }, save = { stored = it }).deviceId())
        stored = null // a reinstall
        assertEquals("id-2", stapel.session.StoredInstallationIdentity(ids, load = { stored }, save = { stored = it }).deviceId())
    }
}
