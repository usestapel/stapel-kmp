package stapel.session.vault

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

public actual fun platformTokenVault(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    config: TokenVaultConfig,
): TokenVault {
    val service = "${NSBundle.mainBundle.bundleIdentifier ?: "stapel"}.${config.namespace}"
    return KeychainTokenVault(service, dispatchers, SystemKeychain(service, config.iosAccessGroup))
}

/** Raised for a Keychain status other than success / not found. */
internal class KeychainException(val status: Int, operation: String) : Exception("Keychain $operation failed: OSStatus $status") {
    /**
     * The process has no Keychain at all (`errSecMissingEntitlement` -34018: an unsigned build or no
     * application identifier; `errSecNotAvailable` -25291: no keychain in this process): nothing can
     * ever have been written, so there is nothing to read or wipe.
     */
    val noStore: Boolean get() = status == ERR_SEC_MISSING_ENTITLEMENT || status == ERR_SEC_NOT_AVAILABLE

    companion object {
        /** `errSecMissingEntitlement`. */
        const val ERR_SEC_MISSING_ENTITLEMENT: Int = -34018

        /** `errSecNotAvailable`. */
        const val ERR_SEC_NOT_AVAILABLE: Int = -25291
    }
}

/** The three Keychain operations the vault needs; [SystemKeychain] is the real one. Throws [KeychainException]. */
internal interface KeychainAccess {
    /** The item's data, or null when there is none. */
    fun copy(): ByteArray?

    /** Adds the item (it must not exist). */
    fun add(bytes: ByteArray)

    /** Deletes the item; absent is fine. */
    fun delete()
}

/**
 * One generic-password item, `AfterFirstUnlockThisDeviceOnly`. Keychain items survive an uninstall;
 * [installMarkerKey] in `NSUserDefaults` (which does not) detects the first run after a reinstall
 * and deletes the stale item before anything reads it. A wiped or never-written item reads as
 * [VaultRead.Empty] (no session), and so does a Keychain the app has no entitlement for (nothing can
 * have been written there); a write to such a Keychain still fails, so the session manager logs it.
 */
internal class KeychainTokenVault(
    service: String,
    private val dispatchers: StapelDispatchers,
    private val keychain: KeychainAccess,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : TokenVault {
    val installMarkerKey: String = "$service.installed"
    private val lock = Mutex()
    private var installChecked = false

    override suspend fun read(): VaultRead = io {
        // A store the app cannot have (no entitlement) was never written: no session, not a lost one.
        when (val bytes = withoutStore(null) { keychain.copy() }) {
            null -> VaultRead.Empty
            else -> decodeRecord(bytes)?.let { VaultRead.Present(it) } ?: VaultRead.Unreadable("unrecognised record")
        }
    }

    override suspend fun write(session: StoredSession) {
        io {
            keychain.delete()
            keychain.add(encodeRecord(session))
        }
    }

    override suspend fun clear() {
        io { withoutStore(Unit) { keychain.delete() } }
    }

    private inline fun <T> withoutStore(absent: T, block: () -> T): T = try {
        block()
    } catch (e: KeychainException) {
        if (e.noStore) absent else throw e
    }

    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) {
        lock.withLock {
            if (!installChecked) {
                if (!defaults.boolForKey(installMarkerKey)) {
                    // First run of this installation: an item found now belongs to a previous install.
                    withoutStore(Unit) { keychain.delete() }
                    defaults.setBool(true, installMarkerKey)
                }
                installChecked = true
            }
            block()
        }
    }
}

/** The Security-framework Keychain: one generic-password item keyed by service + account. */
@OptIn(ExperimentalForeignApi::class)
internal class SystemKeychain(private val service: String, private val accessGroup: String?) : KeychainAccess {
    private val account = "session"

    private fun baseQuery(): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        query.put(kSecAttrService, service)
        query.put(kSecAttrAccount, account)
        accessGroup?.let { query.put(kSecAttrAccessGroup, it) }
        return query
    }

    @OptIn(BetaInteropApi::class)
    override fun copy(): ByteArray? = memScoped {
        val query = baseQuery()
        try {
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
                errSecItemNotFound -> null
                else -> throw KeychainException(status, "read")
            }
        } finally {
            CFRelease(query)
        }
    }

    @OptIn(BetaInteropApi::class)
    override fun add(bytes: ByteArray) {
        val query = baseQuery()
        try {
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            val retained = CFBridgingRetain(data)
            try {
                CFDictionaryAddValue(query, kSecValueData, retained)
                val status = SecItemAdd(query, null)
                if (status != errSecSuccess) throw KeychainException(status, "write")
            } finally {
                CFRelease(retained)
            }
        } finally {
            CFRelease(query)
        }
    }

    override fun delete() {
        val query = baseQuery()
        try {
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException(status, "delete")
        } finally {
            CFRelease(query)
        }
    }

    private fun CFMutableDictionaryRef.put(key: CFTypeRef?, value: String) {
        val retained = CFBridgingRetain(value)
        CFDictionaryAddValue(this, key, retained)
        CFRelease(retained)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
