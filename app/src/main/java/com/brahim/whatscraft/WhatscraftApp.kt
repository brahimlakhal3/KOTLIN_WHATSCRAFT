package com.brahim.whatscraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.notify.Notifications

/**
 * Application entry point.
 *
 * Holds the single [Container] (manual dependency injection - no annotation
 * processor, no reflection) and creates the notification channels once.
 */
class WhatscraftApp : Application() {

    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        val messages = NotificationChannel(
            Notifications.CHANNEL_MESSAGES,
            getString(R.string.channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_messages_description)
            enableVibration(true)
            enableLights(false)
        }

        val relay = NotificationChannel(
            Notifications.CHANNEL_RELAY,
            getString(R.string.channel_relay_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.channel_relay_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(messages, relay))
    }
}
