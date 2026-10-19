package com.brahim.whatscraft.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.brahim.whatscraft.WhatscraftApp
import kotlinx.coroutines.launch

/**
 * Keeps the relay websocket open while the app is in the background, so
 * messages and notifications arrive without a push provider.
 *
 * It is deliberately tiny: the connection, retries and message handling all
 * live in [com.brahim.whatscraft.data.ChatRepository]; this service only exists
 * to tell Android the process is doing something useful and to show the
 * (silent, minimum-importance) "listening" notification.
 */
class RelayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as? WhatscraftApp)?.container ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!container.sessions.isSignedIn) {
            stopSelf()
            return START_NOT_STICKY
        }

        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.RELAY_NOTIFICATION_ID,
                Notifications.relay(this),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure {
            // Without the notification permission (or on a restricted device) the
            // service cannot run in the foreground; the app still works while it
            // is open.
            stopSelf()
            return START_NOT_STICKY
        }

        container.repository.start()
        container.scope.launch { container.repository.publishDevice() }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun start(context: android.content.Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, RelayService::class.java),
                )
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, RelayService::class.java))
        }
    }
}
