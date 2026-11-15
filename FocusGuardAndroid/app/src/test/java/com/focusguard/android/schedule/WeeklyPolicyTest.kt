package com.focusguard.android.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyPolicyTest {
    @Test fun restrictionsApplyOnEveryDayIncludingSunday() {
        for (day in 1..7) {
            assertTrue(WeeklyPolicy.restrictionsActive(true, LocalDate.of(2026, 11, day)))
        }
    }

    @Test fun disabledProtectionRemainsInactiveOnEveryDay() {
        for (day in 1..7) {
            assertFalse(WeeklyPolicy.restrictionsActive(false, LocalDate.of(2026, 11, day)))
        }
    }
}
