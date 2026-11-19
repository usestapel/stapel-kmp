package stapel.i18n

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal actual fun platformLocaleTag(): String = NSLocale.currentLocale.localeIdentifier.replace('_', '-')
