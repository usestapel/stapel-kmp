package stapel.i18n

internal actual fun platformLocaleTag(): String = java.util.Locale.getDefault().toLanguageTag()
