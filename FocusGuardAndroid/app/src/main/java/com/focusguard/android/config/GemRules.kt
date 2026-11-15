package com.focusguard.android.config

/** Pure reward policy, kept separate so thresholds can be unit-tested without Android APIs. */
object GemRules {
    const val DAY_PASS_COST = 30
    const val DAY_PASS_DURATION_MS = 24L * 60L * 60L * 1000L
    const val TEN_MINUTES_MS = 10L * 60L * 1000L
    const val THIRTY_MINUTES_MS = 30L * 60L * 1000L

    /** Per selected app, per completed day: <10m = 5; 10m through 30m = 3; >30m = 0. */
    fun usageReward(usageMillis: Long): Int = when {
        usageMillis < 0L -> 0 // Unknown/unavailable usage is not a reward-eligible zero.
        usageMillis < TEN_MINUTES_MS -> 5
        usageMillis <= THIRTY_MINUTES_MS -> 3
        else -> 0
    }
}
