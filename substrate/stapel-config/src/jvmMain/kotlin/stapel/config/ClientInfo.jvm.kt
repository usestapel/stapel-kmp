package stapel.config

/** Reads the `os.name` and `os.version` system properties; no device model on desktop. */
public actual fun platformClientInfo(appId: String, appVersion: String, build: String): ClientInfo =
    ClientInfo(
        appId = appId,
        appVersion = appVersion,
        build = build,
        platform = ClientPlatform.Desktop,
        osName = System.getProperty("os.name") ?: "Desktop",
        osVersion = System.getProperty("os.version") ?: "",
        deviceModel = null,
    )
