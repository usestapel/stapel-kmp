package stapel.session.vault

import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

/**
 * The platform's secure vault. What each one protects against (and what it does not):
 *
 * - **Android**: an AES-256-GCM key generated in `AndroidKeyStore` (non-exportable, not bound to user
 *   authentication); the ciphertext lives in one file under `noBackupFilesDir`, so cloud/adb backup
 *   and device transfer never carry it. Not protected: a rooted device or code running as the app.
 *   A Keystore failure reads as [VaultRead.Unreadable] (a forced sign-in), never as plaintext.
 * - **iOS**: one Keychain generic-password item, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`,
 *   service `<bundle id>.<namespace>`. Not in backups, not readable before the first unlock after
 *   boot. Keychain items outlive an uninstall, so a marker in `NSUserDefaults` (which does not)
 *   wipes the item on the first launch after a reinstall.
 * - **Desktop (JVM)**: one file in the per-user application data directory with owner-only
 *   permissions (POSIX `0600`, directory `0700`; on Windows the user-profile ACL). This protects
 *   against other OS users only — **anything running as the same user can read it**. There is no
 *   encryption with a key stored next to the file, because that would be decoration. An app that
 *   needs more supplies its own [TokenVault].
 */
public expect fun platformTokenVault(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    config: TokenVaultConfig = TokenVaultConfig(),
): TokenVault
