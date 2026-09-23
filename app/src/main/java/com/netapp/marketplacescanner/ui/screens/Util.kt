package com.netapp.marketplacescanner.ui.screens

import java.util.concurrent.TimeUnit

/** Compact relative time like "just now", "12m ago", "3h ago", "2d ago". */
fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "never"
    val delta = System.currentTimeMillis() - epochMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
