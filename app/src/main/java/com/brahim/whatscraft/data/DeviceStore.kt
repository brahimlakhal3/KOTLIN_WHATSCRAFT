package com.brahim.whatscraft.data

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * This install's identity to the relay: a stable device id and a friendly
 * name. No key pairs: the session token is the only credential, and chats
 * live in the cloud.
 */
class DeviceStore(context: Context) {

    data class Device(val id: String, val name: String)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cached: Device? = null

    /** Loads the device row, creating it on first launch. */
    fun load(): Device {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val id = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_ID, it).apply()
            }
            val name = prefs.getString(KEY_NAME, null) ?: defaultDeviceName()
            val device = Device(id = id, name = name)
            cached = device
            return device
        }
    }

    fun rename(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        cached = cached?.copy(name = name)
    }

    /** Forgets this device row (delete account). Signing out keeps it. */
    fun secureWipe() {
        synchronized(this) {
            prefs.edit().clear().apply()
            cached = null
        }
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android"
        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isBlank() -> model
            else -> "$manufacturer $model"
        }
    }

    private companion object {
        const val PREFS = "whatscraft-device"
        const val KEY_ID = "device.id"
        const val KEY_NAME = "device.name"
    }
}
