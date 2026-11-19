package stapel.config

import android.os.Build

/** Reads `Build.VERSION.RELEASE` and `Build.MANUFACTURER` / `Build.MODEL`. */
public actual fun platformClientInfo(appId: String, appVersion: String, build: String): ClientInfo =
    ClientInfo(
        appId = appId,
        appVersion = appVersion,
        build = build,
        platform = ClientPlatform.Android,
        osName = "Android",
        osVersion = Build.VERSION.RELEASE ?: "",
        deviceModel = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").ifBlank { null },
    )
