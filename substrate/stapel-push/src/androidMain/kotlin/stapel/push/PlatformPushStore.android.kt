package stapel.push

import android.content.Context
import kotlinx.coroutines.withContext
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.application

public actual fun platformPushStore(platform: PlatformContext, dispatchers: StapelDispatchers): PushStore {
    val prefs = platform.application.getSharedPreferences("stapel.push", Context.MODE_PRIVATE)
    return object : PushStore {
        override suspend fun load(): PushRecord = withContext(dispatchers.io) {
            PushRecord(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                optedOut = prefs.getBoolean(KEY_OPTED_OUT, false),
                registeredFingerprint = prefs.getString(KEY_FINGERPRINT, null),
                registeredAccount = prefs.getString(KEY_ACCOUNT, null),
            )
        }

        override suspend fun save(record: PushRecord) {
            withContext(dispatchers.io) {
                prefs.edit()
                    .putBoolean(KEY_ENABLED, record.enabled)
                    .putBoolean(KEY_OPTED_OUT, record.optedOut)
                    .putString(KEY_FINGERPRINT, record.registeredFingerprint)
                    .putString(KEY_ACCOUNT, record.registeredAccount)
                    .commit()
            }
        }
    }
}
