package com.focusguard.android.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyPolicyTest {
    @Test fun mondayThroughSaturdayAreScheduled() {
        for (day in 2..7) {
            assertTrue(WeeklyPolicy.isScheduledDay(LocalDate.of(2026, 11, day)))
        }
    }

    @Test fun sundayIsExempt() {
        assertFalse(WeeklyPolicy.isScheduledDay(LocalDate.of(2026, 11, 8)))
    }

    @Test fun masterSwitchStillControlsSchedule() {
        assertFalse(WeeklyPolicy.restrictionsActive(false, LocalDate.of(2026, 11, 9)))
        assertTrue(WeeklyPolicy.restrictionsActive(true, LocalDate.of(2026, 11, 9)))
        assertFalse(WeeklyPolicy.restrictionsActive(true, LocalDate.of(2026, 11, 8)))
    }
}
