package com.ao3reader.ui.components

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** "today", "3d ago", "5w ago", "4mo ago", "2y ago" for a "yyyy-MM-dd…" date; null if it can't be read. */
fun agoLabel(date: String, today: LocalDate = LocalDate.now()): String? {
    val d = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(d, today).coerceAtLeast(0)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 14 -> "${days}d ago"
        days < 60 -> "${days / 7}w ago"
        days < 730 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
