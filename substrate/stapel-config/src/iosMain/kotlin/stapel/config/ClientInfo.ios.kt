package stapel.config

import platform.UIKit.UIDevice

/** Reads `UIDevice.currentDevice` (`systemName`, `systemVersion`, `model`). */
public actual fun platformClientInfo(appId: String, appVersion: String, build: String): ClientInfo {
    val device = UIDevice.currentDevice
    return ClientInfo(
        appId = appId,
        appVersion = appVersion,
        build = build,
        platform = ClientPlatform.Ios,
        osName = device.systemName,
        osVersion = device.systemVersion,
        deviceModel = device.model,
    )
}
