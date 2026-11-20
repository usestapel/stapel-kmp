package stapel.runtime

import platform.Foundation.NSUserDefaults

/** `NSUserDefaults.standardUserDefaults`, key `stapel.locale.app`. */
public actual fun platformLocalePreferenceStorage(platform: PlatformContext): LocalePreferenceStorage =
    object : LocalePreferenceStorage {
        private val defaults = NSUserDefaults.standardUserDefaults

        override fun read(): String? = defaults.stringForKey(KEY)

        override fun write(tag: String?) {
            if (tag == null) defaults.removeObjectForKey(KEY) else defaults.setObject(tag, KEY)
        }
    }

private const val KEY = "stapel.locale.app"
