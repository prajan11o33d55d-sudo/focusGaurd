package com.focusguard.android

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.focusguard.android.usage.DailyGemEngine
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyGemWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            if (now.hour < 6) {
                val nextCheck = now.toLocalDate().atTime(6, 10).atZone(zone)
                val delay = Duration.between(now, nextCheck).toMillis().coerceAtLeast(60_000L)
                val request = OneTimeWorkRequestBuilder<DailyGemWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "focus-guard-gem-check-after-06",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Result.success()
            } else {
                DailyGemEngine.processMatureDays(applicationContext)
                Result.success()
            }
        } catch (_: SecurityException) {
            Result.success() // Usage access is user-controlled; a later run can retry after permission is granted.
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
