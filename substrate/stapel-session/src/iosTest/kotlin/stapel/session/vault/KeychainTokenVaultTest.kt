package stapel.session.vault

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A Keychain double with the real one's semantics (add fails on an existing item). */
private class FakeKeychain : KeychainAccess {
    var item: ByteArray? = null
    val log = mutableListOf<String>()

    override fun copy(): ByteArray? = item.also { log += "copy" }

    override fun add(bytes: ByteArray) {
        log += "add"
        if (item != null) throw KeychainException(-25299, "write") // errSecDuplicateItem
        item = bytes
    }

    override fun delete() {
        log += "delete"
        item = null
    }
}

/** G-S2 (iOS simulator): vault logic over the Keychain seam, and the reinstall wipe through real NSUserDefaults. */
class KeychainTokenVaultTest {
    private val service = "stapel.test.keychain-vault"
    private val keychain = FakeKeychain()

    private fun vault() = KeychainTokenVault(service, TestDispatchers, keychain)

    @AfterTest
    fun cleanUp() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey("$service.installed")
    }

    @Test
    fun roundTripAndReplace() = runTest {
        val vault = vault()
        assertIs<VaultRead.Empty>(vault.read())
        vault.write(sample)
        vault.write(sample) // replace = delete + add, never a duplicate-item failure
        assertSameRecord(sample, (vault().read() as VaultRead.Present).session)
        vault.clear()
        assertIs<VaultRead.Empty>(vault.read())
    }

    @Test
    fun firstRunAfterReinstallWipesTheSurvivingItem() = runTest {
        NSUserDefaults.standardUserDefaults.setBool(true, "$service.installed")
        keychain.item = encodeRecord(sample) // left behind by the previous installation
        assertIs<VaultRead.Present>(vault().read(), "without a reinstall the item survives a new process")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("$service.installed") // what an uninstall does
        assertIs<VaultRead.Empty>(vault().read())
        assertTrue(NSUserDefaults.standardUserDefaults.boolForKey("$service.installed"))
    }

    @Test
    fun garbageItemIsUnreadable() = runTest {
        NSUserDefaults.standardUserDefaults.setBool(true, "$service.installed")
        keychain.item = "x".encodeToByteArray()
        assertIs<VaultRead.Unreadable>(vault().read())
    }

    /**
     * The real Security framework. A bare simulator test process has no keychain (no app host, no
     * application-identifier; ad-hoc-signed entitlements are killed by the simulator), so the call
     * must come back as errSecNotAvailable (-25291) there; where a keychain exists, the full round
     * trip must hold. Either way the query construction is exercised against the real API.
     */
    @Test
    fun realKeychainEitherWorksOrIsReportedUnavailable() = runTest {
        val real = SystemKeychain("stapel.test.system-keychain", accessGroup = null)
        val status = try {
            real.delete()
            real.add(encodeRecord(sample))
            assertSameRecord(sample, decodeRecord(real.copy()!!)!!)
            real.delete()
            assertEquals(null, real.copy())
            0
        } catch (e: KeychainException) {
            e.status
        }
        assertTrue(status == 0 || status == -25291, "unexpected OSStatus $status")
        println("real Keychain in this test process: ${if (status == 0) "available, round trip verified" else "NOT AVAILABLE (errSecNotAvailable) - unverified here"}")
    }
}
