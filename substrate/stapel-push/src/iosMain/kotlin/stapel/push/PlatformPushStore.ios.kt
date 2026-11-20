package stapel.push

import platform.Foundation.NSUserDefaults
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

public actual fun platformPushStore(platform: PlatformContext, dispatchers: StapelDispatchers): PushStore {
    val defaults = NSUserDefaults.standardUserDefaults
    return object : PushStore {
        override suspend fun load(): PushRecord = PushRecord(
            enabled = defaults.boolForKey(KEY_ENABLED),
            optedOut = defaults.boolForKey(KEY_OPTED_OUT),
            registeredFingerprint = defaults.stringForKey(KEY_FINGERPRINT),
            registeredAccount = defaults.stringForKey(KEY_ACCOUNT),
        )

        override suspend fun save(record: PushRecord) {
            defaults.setBool(record.enabled, KEY_ENABLED)
            defaults.setBool(record.optedOut, KEY_OPTED_OUT)
            setOrRemove(defaults, KEY_FINGERPRINT, record.registeredFingerprint)
            setOrRemove(defaults, KEY_ACCOUNT, record.registeredAccount)
        }
    }
}

private fun setOrRemove(defaults: NSUserDefaults, key: String, value: String?) {
    if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
}
