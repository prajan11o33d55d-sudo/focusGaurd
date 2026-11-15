package com.focusguard.android.usage

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.focusguard.android.config.FocusConfig
import com.focusguard.android.config.GemRules
import java.time.LocalDate
import java.time.ZoneId

object UsageAccess {
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageMillis(context: Context, packageName: String, start: Long, end: Long): Long {
        if (!hasPermission(context)) return -1L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val records: List<UsageStats> = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, start, end
        ) ?: return -1L
        return records.asSequence()
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
            .coerceAtLeast(0L)
    }

    fun todayUsage(context: Context, packageName: String, now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageMillis(context, packageName, start, now)
    }
}

object DailyGemEngine {
    /**
     * Processes full-day protection gems independently from Usage Access, then processes
     * per-app usage gems when Usage Access is granted. This way a missing usage permission
     * does not suppress the separate +1 full-day protection reward.
     */
    fun processMatureDays(context: Context) {
        FocusConfig.ensureRewardCursor(context)
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        if (now.hour < 6) return
        val yesterday = now.toLocalDate().minusDays(1)
        val oldestAvailable = now.toLocalDate().minusDays(29)

        var baseCursor = FocusConfig.lastBaseProcessedDate(context)?.plusDays(1) ?: return
        if (baseCursor.isBefore(oldestAvailable)) baseCursor = oldestAvailable
        while (!baseCursor.isAfter(yesterday)) {
            val dayStart = baseCursor.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = baseCursor.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val fullyProtected = FocusConfig.isMasterEnabled(context) &&
                FocusConfig.serviceCoveredWholeDay(context, dayStart, dayEnd) &&
                !FocusConfig.passOverlapsDay(context, dayStart, dayEnd)
            val earned = if (fullyProtected) 1 else 0
            val summary = if (fullyProtected) "Full-day protection +1" else "No full-day protection gem"
            FocusConfig.recordBaseRewardDay(context, baseCursor, earned, summary)
            baseCursor = baseCursor.plusDays(1)
        }

        // Usage-based gems are separate and require the user to grant Usage Access.
        if (!UsageAccess.hasPermission(context)) return
        var usageCursor = FocusConfig.lastUsageProcessedDate(context)?.plusDays(1) ?: return
        if (usageCursor.isBefore(oldestAvailable)) usageCursor = oldestAvailable
        val blocked = FocusConfig.blockedPackages(context)
        val tracked = FocusConfig.trackedPackages(context) - blocked
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        while (!usageCursor.isAfter(yesterday)) {
            val dayStart = usageCursor.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = usageCursor.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            var earned = 0
            val details = mutableListOf<String>()
            val records = if (tracked.isNotEmpty()) {
                manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd) ?: return
            } else emptyList()
            val usageByPackage = records.groupBy { it.packageName }
                .mapValues { (_, rows) -> rows.sumOf { it.totalTimeInForeground }.coerceAtLeast(0L) }

            for (pkg in tracked) {
                if (!FocusConfig.trackerSelectedBeforeDay(context, pkg, dayStart)) continue
                val installed = runCatching {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(pkg, 0)
                }.isSuccess
                if (!installed) continue

                // A selected app with no foreground events has 0 minutes and qualifies for +5.
                val usage = usageByPackage[pkg] ?: 0L
                val reward = GemRules.usageReward(usage)
                if (reward > 0) {
                    earned += reward
                    details += "$pkg +$reward"
                }
            }

            val summary = if (details.isEmpty()) "No usage gems earned" else details.joinToString(" · ")
            FocusConfig.recordUsageRewardDay(context, usageCursor, earned, summary)
            usageCursor = usageCursor.plusDays(1)
        }
    }
}
