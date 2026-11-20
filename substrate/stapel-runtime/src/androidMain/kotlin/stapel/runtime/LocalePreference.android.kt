package stapel.runtime

import android.content.Context

/** `SharedPreferences` `stapel.locale`, key `app`. */
public actual fun platformLocalePreferenceStorage(platform: PlatformContext): LocalePreferenceStorage {
    val prefs = platform.application.getSharedPreferences("stapel.locale", Context.MODE_PRIVATE)
    return object : LocalePreferenceStorage {
        override fun read(): String? = prefs.getString("app", null)

        override fun write(tag: String?) {
            prefs.edit().apply { if (tag == null) remove("app") else putString("app", tag) }.apply()
        }
    }
}
