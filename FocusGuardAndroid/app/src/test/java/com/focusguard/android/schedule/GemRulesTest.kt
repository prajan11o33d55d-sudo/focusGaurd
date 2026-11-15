package com.focusguard.android.schedule

import com.focusguard.android.config.GemRules
import org.junit.Assert.assertEquals
import org.junit.Test

class GemRulesTest {
    @Test fun dayPassCostsThirtyGemsAndLastsTwentyFourHours() {
        assertEquals(30, GemRules.DAY_PASS_COST)
        assertEquals(24L * 60L * 60L * 1000L, GemRules.DAY_PASS_DURATION_MS)
    }

    @Test fun underTenMinutesEarnsFiveGems() {
        assertEquals(5, GemRules.usageReward(9 * 60_000L + 59_999L))
    }

    @Test fun tenThroughThirtyMinutesEarnThreeGems() {
        assertEquals(3, GemRules.usageReward(10 * 60_000L))
        assertEquals(3, GemRules.usageReward(29 * 60_000L + 59_999L))
        assertEquals(3, GemRules.usageReward(30 * 60_000L))
    }

    @Test fun overThirtyMinutesAndUnknownUsageEarnNoGems() {
        assertEquals(0, GemRules.usageReward(30 * 60_000L + 1L))
        assertEquals(0, GemRules.usageReward(60 * 60_000L))
        assertEquals(0, GemRules.usageReward(-1L))
    }
}
