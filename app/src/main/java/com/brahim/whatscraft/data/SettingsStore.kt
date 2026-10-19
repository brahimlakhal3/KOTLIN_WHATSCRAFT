package com.brahim.whatscraft.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "whatscraft.settings")

enum class ThemeMode {
    System,
    Light,
    Dark;

    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        System -> systemInDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: System
    }
}

/**
 * The colour story of the app. [Monochrome] is Whatscraft's own black and
 * white, [WhatsApp] the familiar green-and-beige look, and the rest are single
 * accent tints that colour the conversation while the layout stays quiet.
 */
enum class Palette {
    Monochrome,
    WhatsApp,
    Ocean,
    Berry,
    Sunset,
    Forest,
    Rose,
    Lavender,
    Sand,
    Midnight;

    companion object {
        fun from(value: String?): Palette = entries.firstOrNull { it.name == value } ?: Monochrome
    }
}

/** How large the type scale is drawn. */
enum class TextSize(val scale: Float, val label: String) {
    Small(0.9f, "Small"),
    Default(1f, "Default"),
    Large(1.12f, "Large"),
    Largest(1.24f, "Largest");

    companion object {
        fun from(value: String?): TextSize = entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Corner language for the message bubbles. */
enum class BubbleStyle(val label: String) {
    Rounded("Rounded"),
    Soft("Soft"),
    Square("Square");

    companion object {
        fun from(value: String?): BubbleStyle = entries.firstOrNull { it.name == value } ?: Rounded
    }
}

data class AppSettings(
    /** Relay base URL, e.g. https://whatscraft-relay.fly.dev */
    val serverUrl: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    /** Monochrome (default) or the WhatsApp palette. */
    val palette: Palette = Palette.Monochrome,
    /** OLED true-black canvas in dark mode; off = graphite gray. Monochrome only. */
    val pureBlack: Boolean = false,
    /** Multiplier for the whole type scale. */
    val textSize: TextSize = TextSize.Default,
    /** Corner language for message bubbles. */
    val bubbleStyle: BubbleStyle = BubbleStyle.Rounded,
    /** Double-tapping a bubble toggles the heart reaction. */
    val doubleTapHeart: Boolean = true,
    /** Skips the entrance and pop animations. */
    val reduceMotion: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** Keep a foreground service connected so messages arrive with the app closed. */
    val backgroundRelay: Boolean = true,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val enterToSend: Boolean = true,
    /** Off by default: message text stays off the lock screen. */
    val showMessagePreview: Boolean = false,
    /** Media/haptics. */
    val haptics: Boolean = true,
    val lastSyncedAt: Long = 0,
    /**
     * Conversation id -> highest message seq the user cleared on this phone.
     * Old history that is cleared here must not come back from the relay.
     */
    val cleared: Map<String, Long> = emptyMap(),
) {
    companion object {
        val Default = AppSettings()
    }
}

/**
 * User preferences, backed by DataStore and mirrored into a [StateFlow] so the
 * theme can be read synchronously during the first composition.
 */
class SettingsStore(context: Context) {

    private val dataStore = context.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _settings.value = AppSettings(
                    serverUrl = prefs[KEY_SERVER_URL] ?: "",
                    themeMode = ThemeMode.from(prefs[KEY_THEME]),
                    palette = Palette.from(prefs[KEY_PALETTE]),
                    pureBlack = prefs[KEY_PURE_BLACK] ?: false,
                    textSize = TextSize.from(prefs[KEY_TEXT_SIZE]),
                    bubbleStyle = BubbleStyle.from(prefs[KEY_BUBBLE_STYLE]),
                    doubleTapHeart = prefs[KEY_DOUBLE_TAP] ?: true,
                    reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
                    notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                    backgroundRelay = prefs[KEY_BACKGROUND_RELAY] ?: true,
                    readReceipts = prefs[KEY_READ_RECEIPTS] ?: true,
                    typingIndicators = prefs[KEY_TYPING] ?: true,
                    enterToSend = prefs[KEY_ENTER_TO_SEND] ?: true,
                    showMessagePreview = prefs[KEY_PREVIEW] ?: false,
                    haptics = prefs[KEY_HAPTICS] ?: true,
                    lastSyncedAt = prefs[KEY_LAST_SYNC] ?: 0,
                    cleared = parseCleared(prefs[KEY_CLEARED]),
                )
            }
        }
    }

    val current: AppSettings get() = _settings.value

    suspend fun setServerUrl(url: String) = put(KEY_SERVER_URL, url.trim().trimEnd('/'))

    suspend fun setThemeMode(mode: ThemeMode) = put(KEY_THEME, mode.name)

    suspend fun setPalette(palette: Palette) = put(KEY_PALETTE, palette.name)

    suspend fun setPureBlack(enabled: Boolean) = put(KEY_PURE_BLACK, enabled)

    suspend fun setTextSize(size: TextSize) = put(KEY_TEXT_SIZE, size.name)

    suspend fun setBubbleStyle(style: BubbleStyle) = put(KEY_BUBBLE_STYLE, style.name)

    suspend fun setDoubleTapHeart(enabled: Boolean) = put(KEY_DOUBLE_TAP, enabled)

    suspend fun setReduceMotion(enabled: Boolean) = put(KEY_REDUCE_MOTION, enabled)

    suspend fun setNotifications(enabled: Boolean) = put(KEY_NOTIFICATIONS, enabled)

    suspend fun setBackgroundRelay(enabled: Boolean) = put(KEY_BACKGROUND_RELAY, enabled)

    suspend fun setReadReceipts(enabled: Boolean) = put(KEY_READ_RECEIPTS, enabled)

    suspend fun setTypingIndicators(enabled: Boolean) = put(KEY_TYPING, enabled)

    suspend fun setEnterToSend(enabled: Boolean) = put(KEY_ENTER_TO_SEND, enabled)

    suspend fun setShowMessagePreview(enabled: Boolean) = put(KEY_PREVIEW, enabled)

    suspend fun setHaptics(enabled: Boolean) = put(KEY_HAPTICS, enabled)

    suspend fun markSynced() = put(KEY_LAST_SYNC, System.currentTimeMillis())

    /** Remembers that everything up to [seq] was cleared in this conversation. */
    suspend fun markCleared(conversationId: String, seq: Long) {
        if (seq <= 0) return
        dataStore.edit { prefs ->
            val merged = parseCleared(prefs[KEY_CLEARED]).toMutableMap()
            merged[conversationId] = maxOf(merged[conversationId] ?: 0, seq)
            prefs[KEY_CLEARED] = merged.map { (id, upTo) -> "$id:$upTo" }.toSet()
        }
    }

    /** Remembers a clear-marker for many conversations at once (delete all). */
    suspend fun markAllCleared(markers: Map<String, Long>) {
        if (markers.isEmpty()) return
        dataStore.edit { prefs ->
            val merged = parseCleared(prefs[KEY_CLEARED]).toMutableMap()
            markers.forEach { (id, seq) -> merged[id] = maxOf(merged[id] ?: 0, seq) }
            prefs[KEY_CLEARED] = merged.map { (id, upTo) -> "$id:$upTo" }.toSet()
        }
    }

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
        val KEY_BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val KEY_DOUBLE_TAP = booleanPreferencesKey("double_tap_heart")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_BACKGROUND_RELAY = booleanPreferencesKey("background_relay")
        val KEY_READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        val KEY_TYPING = booleanPreferencesKey("typing_indicators")
        val KEY_ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
        val KEY_PREVIEW = booleanPreferencesKey("message_preview")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_LAST_SYNC = longPreferencesKey("last_synced_at")
        val KEY_CLEARED = stringSetPreferencesKey("cleared")
    }
}

/** "[conversationId]:[seq]" entries back into a map. */
private fun parseCleared(raw: Set<String>?): Map<String, Long> =
    raw.orEmpty().mapNotNull { entry ->
        val separator = entry.lastIndexOf(':')
        if (separator <= 0) return@mapNotNull null
        val upTo = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
        entry.substring(0, separator) to upTo
    }.toMap()
