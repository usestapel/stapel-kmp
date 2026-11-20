package stapel.notifications.push

import stapel.notifications.api.NotificationsApi
import stapel.notifications.api.model.DeviceTokenRequest
import stapel.notifications.api.model.DeviceUnregisterRequest
import stapel.push.DeviceStatus
import stapel.push.PushDevice
import stapel.push.PushDeviceRegistry
import stapel.push.PushToken
import stapel.push.RegistryError
import stapel.result.StapelError
import stapel.result.StapelResult

/**
 * stapel-push's device registry over the notifications brick (stapel-notifications 0.31+):
 * `POST devices/ {token, platform, device_id}` (the session comes from the access token's claim),
 * `POST devices/unregister/ {device_id}` (the token never travels in a URL), and `GET devices/`
 * matched by `token_fingerprint` for the status.
 */
public class NotificationsPushRegistry(private val api: NotificationsApi) : PushDeviceRegistry {
    /** `POST devices/ {token, platform, device_id}`; 409 → held by another account, 403 → not allowed. */
    override suspend fun register(device: PushDevice): StapelResult<Unit, RegistryError> =
        when (val r = api.registerDeviceToken(DeviceTokenRequest(device.token.value, device.platform.wire, device.installationId))) {
            is StapelResult.Ok -> StapelResult.Ok(Unit)
            is StapelResult.Err -> StapelResult.Err(registryError(r.error))
        }

    /** `POST devices/unregister/ {device_id}`; a 404 (nothing registered) is success. */
    override suspend fun unregister(installationId: String): StapelResult<Unit, RegistryError> =
        when (val r = api.unregisterPushDevice(DeviceUnregisterRequest(deviceId = installationId))) {
            is StapelResult.Ok -> StapelResult.Ok(Unit)
            // Nothing registered for this installation: what the caller wanted.
            is StapelResult.Err -> if (r.error.status == 404) StapelResult.Ok(Unit) else StapelResult.Err(registryError(r.error))
        }

    /** `GET devices/`, matched by `token_fingerprint`. */
    override suspend fun status(token: PushToken): StapelResult<DeviceStatus, RegistryError> =
        when (val r = api.listDeviceTokens()) {
            is StapelResult.Ok -> {
                val row = r.value.firstOrNull { it.tokenFingerprint == token.fingerprint }
                StapelResult.Ok(
                    when {
                        row == null -> DeviceStatus.Absent
                        row.isActive -> DeviceStatus.Registered
                        else -> DeviceStatus.Inactive
                    },
                )
            }
            is StapelResult.Err -> StapelResult.Err(registryError(r.error))
        }

    private fun registryError(error: StapelError): RegistryError = when (error.status) {
        409 -> RegistryError.HeldByAnotherAccount(error)
        403 -> RegistryError.NotAllowed(error)
        else -> RegistryError.Failed(error)
    }
}
