package com.notecraft.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

fun formatNoteDate(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        dateTime.toLocalDate() == today -> "Today, ${dateTime.format(timeFormatter)}"
        dateTime.toLocalDate() == today.minusDays(1) -> "Yesterday"
        dateTime.year == today.year -> dateTime.format(shortDateFormatter)
        else -> dateTime.format(dateFormatter)
    }
}
