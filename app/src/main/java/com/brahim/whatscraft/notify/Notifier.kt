package com.brahim.whatscraft.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brahim.whatscraft.R
import java.util.concurrent.ConcurrentHashMap

/**
 * The one thing [com.brahim.whatscraft.data.ChatRepository] needs from the
 * platform. Keeping it an interface means the repository can be unit-tested and
 * the UI never talks to the NotificationManager directly.
 */
interface MessageNotifier {
    /**
     * [messageId] lets the notifier drop a replayed message instead of posting
     * the same line twice; callers that have no id may leave it null.
     */
    fun onMessage(
        conversationId: String,
        title: String,
        body: String,
        showPreview: Boolean,
        messageId: String? = null,
    )

    fun onFriendEvent(kind: String, name: String)
    fun cancelForConversation(conversationId: String)
    fun cancelAll()
}

/**
 * One compact notification per conversation: the sender, the newest line, and
 * an inline reply. Deliberately plain - no MessagingStyle stack, no big text -
 * so it stays a single small row in the shade, and a message that arrives twice
 * (reconnect replay, resync) never posts twice.
 */
class Notifier(private val context: Context) : MessageNotifier {

    private val manager = NotificationManagerCompat.from(context)

    /** Message ids recently posted per conversation, so replays are ignored. */
    private val shownIds = ConcurrentHashMap<String, ArrayDeque<String>>()

    override fun onMessage(
        conversationId: String,
        title: String,
        body: String,
        showPreview: Boolean,
        messageId: String?,
    ) {
        if (!Notifications.allowed(context)) return
        if (messageId != null && !isFirstDelivery(conversationId, messageId)) return

        val text = if (showPreview) body else context.getString(R.string.notification_hidden_message)

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openChat(context, conversationId))
            .addAction(Notifications.replyAction(context, conversationId))
            .build()

        runCatching { manager.notify(Notifications.idFor(conversationId), notification) }
    }

    override fun onFriendEvent(kind: String, name: String) {
        if (!Notifications.allowed(context)) return
        val (title, text) = when (kind) {
            "request" -> context.getString(R.string.friend_notification_request_title) to
                context.getString(R.string.friend_notification_request_text, name)
            "accepted" -> context.getString(R.string.friend_notification_accepted_title) to
                context.getString(R.string.friend_notification_accepted_text, name)
            "removed" -> return
            else -> context.getString(R.string.app_name) to name
        }
        runCatching { manager.notify(Notifications.FRIENDS_ID, Notifications.friend(context, title, text)) }
    }

    override fun cancelForConversation(conversationId: String) {
        shownIds.remove(conversationId)
        runCatching { manager.cancel(Notifications.idFor(conversationId)) }
    }

    override fun cancelAll() {
        shownIds.clear()
        runCatching { manager.cancelAll() }
    }

    /** False when this id was already posted for this conversation. */
    private fun isFirstDelivery(conversationId: String, messageId: String): Boolean {
        val ids = shownIds.getOrPut(conversationId) { ArrayDeque() }
        synchronized(ids) {
            if (ids.contains(messageId)) return false
            ids.addLast(messageId)
            while (ids.size > MAX_RECENT_IDS) ids.removeFirst()
            return true
        }
    }

    private companion object {
        const val MAX_RECENT_IDS = 24
    }
}
