package stapel.notifications.push

import stapel.notifications.api.NotificationsApi
import stapel.notifications.api.model.FeedReadRequest
import stapel.push.Importance
import stapel.push.PushChannelSpec
import stapel.push.PushMessage
import stapel.push.PushOpenListener
import stapel.push.PushRouting
import stapel.result.StapelResult
import stapel.runtime.NoopLogger
import stapel.runtime.StapelLogger
import stapel.runtime.info

/**
 * The routing of the notifications brick's pushes: the canonical `url` first, then the legacy link
 * keys the backend still sends; one Android channel per routing group (`android.channel_id` equals
 * `data.group`).
 *
 * @param fallbackUri what a tap opens when the push links nowhere, e.g. the feed's deep link; null = bring the app forward.
 * @param channelName the user-visible name of a group's channel, already localized.
 */
@Suppress("DEPRECATION")
public fun notificationsPushRouting(
    fallbackUri: String? = null,
    channelName: (NotificationPushGroup) -> String = ::defaultChannelName,
): PushRouting = PushRouting(
    linkKeys = listOf("url", "chat_url", "listing_url", "notifications_chat_url"),
    fallbackUri = fallbackUri,
    channels = NotificationPushGroup.entries.filter { it != NotificationPushGroup.Unknown }.map { group ->
        PushChannelSpec(group.wire, channelName(group), if (group == NotificationPushGroup.Messages) Importance.High else Importance.Default)
    },
)

/** English channel names; an app passes its own localized ones. */
public fun defaultChannelName(group: NotificationPushGroup): String = when (group) {
    NotificationPushGroup.Auth -> "Account and security"
    NotificationPushGroup.Billing -> "Billing"
    NotificationPushGroup.Messages -> "Messages"
    NotificationPushGroup.System -> "Updates"
    NotificationPushGroup.Unknown -> "Notifications"
}

/**
 * A tapped push is the feed row it names (`data.notification_id`): mark it read, so the feed and the
 * badge agree with what the person just opened. Failures are logged; a tap never fails on them.
 */
public class MarkReadOnOpen(
    private val api: NotificationsApi,
    private val logger: StapelLogger = NoopLogger,
) : PushOpenListener {
    /** Marks the row of `data.notification_id` read; a message without one sends nothing. */
    override suspend fun onOpened(message: PushMessage) {
        val id = NotificationPushData.parse(message.data)?.notificationId ?: return
        when (val r = api.markNotificationFeedRead(FeedReadRequest(ids = listOf(id)))) {
            is StapelResult.Ok -> logger.info(TAG) { "stapel.push marked read" }
            is StapelResult.Err -> logger.info(TAG) { "stapel.push mark read failed ${r.error.code.value}" }
        }
    }

    private companion object {
        const val TAG = "stapel.push"
    }
}
