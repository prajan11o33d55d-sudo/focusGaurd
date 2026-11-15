package com.focusguard.android

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.android.config.FocusConfig
import java.util.concurrent.TimeUnit

class FocusGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashDiagnostics.install(this)
        // Background gem reconciliation is helpful but must never prevent the launcher
        // Activity from opening if WorkManager is unavailable or misconfigured on a device.
        try {
            FocusConfig.ensureRewardCursor(this)
            val request = PeriodicWorkRequestBuilder<DailyGemWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "focus-guard-daily-gem-reconciliation",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (error: Throwable) {
            // Background scheduling must never prevent the launcher screen from opening.
            CrashDiagnostics.record(this, "Application background setup", error)
        }
    }
}
