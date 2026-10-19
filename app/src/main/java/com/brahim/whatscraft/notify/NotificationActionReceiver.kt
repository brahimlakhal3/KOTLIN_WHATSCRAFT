package com.brahim.whatscraft.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.brahim.whatscraft.WhatscraftApp
import kotlinx.coroutines.launch

/**
 * Handles the notification buttons. The reply goes through the repository, so a
 * message typed from the lock screen is queued exactly like one typed in the
 * app.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? WhatscraftApp ?: return
        val container = app.container
        val conversationId = intent.getStringExtra(Notifications.KEY_CONVERSATION_ID) ?: return

        when (intent.action) {
            Notifications.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifications.KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                container.notifier.cancelForConversation(conversationId)
                if (text.isEmpty()) return
                container.scope.launch { container.repository.sendText(conversationId, text) }
            }
        }
    }
}
