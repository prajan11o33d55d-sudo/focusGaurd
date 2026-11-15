package com.focusguard.android.config

/** Pure interval rule used for full-day protection rewards. Each record is `start:end`,
 * where `0` means the interval is still open. Separate intervals never combine to cover a day. */
object ProtectionIntervals {
    fun coversWholeDay(sessions: Set<String>, dayStart: Long, dayEnd: Long): Boolean {
        return sessions.any { record ->
            val parts = record.split(':')
            val start = parts.getOrNull(0)?.toLongOrNull() ?: return@any false
            val end = parts.getOrNull(1)?.toLongOrNull() ?: return@any false
            start <= dayStart && (end == 0L || end >= dayEnd)
        }
    }
}
