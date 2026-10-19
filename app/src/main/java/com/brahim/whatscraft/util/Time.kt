package com.brahim.whatscraft.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The relay speaks ISO-8601 UTC (`2026-10-18T21:04:11.231Z`). Everything inside
 * the app uses epoch millis, and formatting happens here so it stays consistent.
 */
object IsoTime {

    private val withFraction = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val withoutFraction = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val cleaned = value.trim().removeSuffix("Z")
        val pattern = if (cleaned.contains('.')) withFraction else withoutFraction
        return synchronized(pattern) {
            runCatching { pattern.parse(cleaned)?.time ?: 0L }.getOrDefault(0L)
        }
    }

    fun now(): Long = System.currentTimeMillis()

    /** Formats epoch millis the way the relay writes timestamps. */
    fun format(millis: Long): String = synchronized(withFraction) {
        withFraction.format(Date(millis)) + "Z"
    }
}

object TimeFormat {

    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekday = SimpleDateFormat("EEEE", Locale.getDefault())
    private val dayAndMonth = SimpleDateFormat("d MMM", Locale.getDefault())
    private val shortDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    /** "09:41" for today, "Yesterday", "Mon", "12/03/25". */
    fun chatList(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return ""
        val days = daysBetween(millis, now)
        return when {
            days == 0L -> clock.format(Date(millis))
            days == 1L -> "Yesterday"
            days < 7L -> weekday.format(Date(millis))
            else -> shortDate.format(Date(millis))
        }
    }

    fun clock(millis: Long): String = if (millis <= 0) "" else clock.format(Date(millis))

    fun dayHeader(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return ""
        return when (daysBetween(millis, now)) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..6L -> weekday.format(Date(millis))
            else -> dayAndMonth.format(Date(millis))
        }
    }

    fun daysBetween(millis: Long, now: Long): Long {
        val then = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (today - then) / 86_400_000L
    }
}
