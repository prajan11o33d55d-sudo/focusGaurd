package com.focusguard.android.schedule

import java.time.DayOfWeek
import java.time.LocalDate

/** Fixed weekly policy: enabled Monday-Saturday, exempt Sunday. */
object WeeklyPolicy {
    fun isScheduledDay(date: LocalDate = LocalDate.now()): Boolean =
        date.dayOfWeek != DayOfWeek.SUNDAY

    fun restrictionsActive(masterEnabled: Boolean, date: LocalDate = LocalDate.now()): Boolean =
        masterEnabled && isScheduledDay(date)
}
