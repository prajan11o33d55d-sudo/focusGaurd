package com.focusguard.android.schedule

import java.time.LocalDate

/** Restrictions have no weekday exemption. A day pass is handled by FocusConfig. */
object WeeklyPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun restrictionsActive(masterEnabled: Boolean, date: LocalDate = LocalDate.now()): Boolean = masterEnabled
}
