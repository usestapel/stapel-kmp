package stapel.config

/** The OS family the client runs on. */
public enum class ClientPlatform(
    /** The `Sec-CH-UA-Platform` value. */
    public val hintName: String,
) {
    /** Android. */
    Android("Android"),

    /** iOS / iPadOS. */
    Ios("iOS"),

    /** Desktop JVM (any OS; [ClientInfo.osName] says which). */
    Desktop("Desktop"),
}

/**
 * Who the client is, for the `User-Agent` and the client-hint headers (the backend's session
 * list shows them).
 *
 * @property appId the app's id (`com.example.app`).
 * @property appVersion the app's marketing version.
 * @property build the app's build number.
 * @property platform the OS family.
 * @property osName the OS name (`Android`, `iOS`, `Mac OS X`).
 * @property osVersion the OS version.
 * @property deviceModel the device model, when known.
 * @property sdkVersion the stapel-kmp version.
 */
public data class ClientInfo(
    public val appId: String,
    public val appVersion: String,
    public val build: String,
    public val platform: ClientPlatform,
    public val osName: String,
    public val osVersion: String,
    public val deviceModel: String?,
    public val sdkVersion: String = STAPEL_KMP_VERSION,
) {
    /** `<appId>/<appVersion> (<osName> <osVersion>; <model>) stapel-kmp/<sdkVersion>`. */
    public val userAgent: String
        get() = buildString {
            append(appId).append('/').append(appVersion)
            append(" (").append(osName).append(' ').append(osVersion)
            if (!deviceModel.isNullOrBlank()) append("; ").append(deviceModel)
            append(") stapel-kmp/").append(sdkVersion)
        }

    /** `Sec-CH-UA-Platform`, `-Platform-Version` and (when known) `-Model`, values quoted as the spec wants. */
    public val clientHints: Map<String, String>
        get() = buildMap {
            put("Sec-CH-UA-Platform", quote(platform.hintName))
            put("Sec-CH-UA-Platform-Version", quote(osVersion))
            if (!deviceModel.isNullOrBlank()) put("Sec-CH-UA-Model", quote(deviceModel))
        }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

/**
 * The [ClientInfo] of the running device: OS name and version and the device model read from the
 * platform (Android `Build`, iOS `UIDevice`, JVM system properties).
 */
public expect fun platformClientInfo(appId: String, appVersion: String, build: String): ClientInfo
