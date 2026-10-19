package com.brahim.whatscraft.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.brahim.whatscraft.MainActivity
import com.brahim.whatscraft.R

/**
 * Notification plumbing: channels, ids, tap targets and actions.
 *
 * Nothing here decides what text may be shown; [Notifier] applies the user's
 * preview setting, which can keep message words off the lock screen.
 */
object Notifications {

    const val CHANNEL_MESSAGES = "whatscraft.messages"
    const val CHANNEL_RELAY = "whatscraft.relay"

    const val RELAY_NOTIFICATION_ID = 1
    private const val FRIEND_NOTIFICATION_ID = 2

    /** Ids below this are reserved for the relay and friend notifications. */
    private const val FIRST_CONVERSATION_ID = 3

    const val KEY_CONVERSATION_ID = "conversationId"
    const val KEY_REPLY_TEXT = "replyText"

    const val ACTION_REPLY = "com.brahim.whatscraft.action.REPLY"

    /**
     * Stable, positive notification id per conversation. Never returns a
     * reserved id (1 = relay, 2 = friends), so a chat can not replace or cancel
     * the foreground service notification.
     */
    fun idFor(conversationId: String): Int =
        (conversationId.hashCode() and 0x7FFFFFFF).coerceAtLeast(FIRST_CONVERSATION_ID)

    // ------------------------------------------------------------- intents ---

    /** Tapping a message notification opens that chat. */
    fun openChat(context: Context, conversationId: String): PendingIntent = PendingIntent.getActivity(
        context,
        idFor(conversationId),
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("whatscraft://chat/$conversationId"))
            .putExtra(KEY_CONVERSATION_ID, conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        flags(),
    )

    /** Direct reply from the notification shade. */
    fun replyAction(context: Context, conversationId: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notification_action_reply),
            broadcast(context, intent(context, ACTION_REPLY, conversationId), idFor(conversationId) xor 0x22),
        )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .extend(NotificationCompat.Action.WearableExtender().setHintDisplayActionInline(true))
            .build()
    }

    private fun intent(context: Context, action: String, conversationId: String) = Intent(context, NotificationActionReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("whatscraft://chat/$conversationId"))
        .putExtra(KEY_CONVERSATION_ID, conversationId)

    private fun broadcast(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, flags())

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // ----------------------------------------------------------- builders ----

    /** The always-on notification that keeps the relay connection alive. */
    fun relay(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_RELAY)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.relay_notification_title))
        .setContentText(context.getString(R.string.relay_notification_text))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setShowWhen(false)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags(),
            ),
        )
        .build()

    fun friend(context: Context, title: String, text: String): Notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                FRIEND_NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags(),
            ),
        )
        .build()

    const val FRIENDS_ID = FRIEND_NOTIFICATION_ID

    /** True when the user has granted POST_NOTIFICATIONS (or is on an old Android). */
    fun allowed(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
