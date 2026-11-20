package stapel.push

import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

/**
 * The platform's [PushStore]: Android `SharedPreferences` (`stapel.push`), iOS `NSUserDefaults`
 * (removed with the app), desktop memory (push is unsupported there). Never a secret: the record
 * holds a fingerprint, not a token.
 */
public expect fun platformPushStore(platform: PlatformContext, dispatchers: StapelDispatchers): PushStore

internal const val KEY_ENABLED = "stapel.push.enabled"
internal const val KEY_OPTED_OUT = "stapel.push.opted_out"
internal const val KEY_FINGERPRINT = "stapel.push.registered_fingerprint"
internal const val KEY_ACCOUNT = "stapel.push.registered_account"
