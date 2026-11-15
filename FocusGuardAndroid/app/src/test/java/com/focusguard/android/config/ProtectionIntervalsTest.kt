package com.focusguard.android.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionIntervalsTest {
    @Test fun oneUninterruptedIntervalMustCoverTheWholeDay() {
        assertTrue(ProtectionIntervals.coversWholeDay(setOf("100:300"), 100, 300))
        assertTrue(ProtectionIntervals.coversWholeDay(setOf("90:0"), 100, 300))
        assertFalse(ProtectionIntervals.coversWholeDay(setOf("101:400"), 100, 300))
        assertFalse(ProtectionIntervals.coversWholeDay(setOf("90:299"), 100, 300))
    }

    @Test fun emergencyPauseBreaksTheFullDayInterval() {
        val intervals = setOf("0:150", "200:0")
        assertFalse(ProtectionIntervals.coversWholeDay(intervals, 100, 300))
    }
}
