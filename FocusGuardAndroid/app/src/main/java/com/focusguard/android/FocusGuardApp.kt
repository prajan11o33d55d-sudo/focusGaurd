package com.focusguard.android

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.focusguard.android.config.FocusConfig

class FocusGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusConfig.ensureRewardCursor(this)
        val request = PeriodicWorkRequestBuilder<DailyGemWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "focus-guard-daily-gem-reconciliation",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
