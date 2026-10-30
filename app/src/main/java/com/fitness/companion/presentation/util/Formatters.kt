package com.fitness.companion.presentation.util

import java.util.Locale

/**
 * The interface is written in Vietnamese, so numbers and dates use Vietnamese conventions on
 * every device instead of following the system language.
 */
val APP_LOCALE: Locale = Locale.forLanguageTag("vi-VN")

fun formatCount(value: Int): String = String.format(APP_LOCALE, "%,d", value)

/** Count with its unit, e.g. "3 lần". Vietnamese nouns have no plural form. */
fun formatQuantity(count: Int, unit: String): String = "${formatCount(count)} $unit"

fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        String.format(APP_LOCALE, "%.0f m", meters)
    } else {
        String.format(APP_LOCALE, "%.2f km", meters / 1000.0)
    }

/** Formats a duration as `mm:ss`, or `h:mm:ss` from one hour up. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(APP_LOCALE, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(APP_LOCALE, "%02d:%02d", minutes, seconds)
    }
}
