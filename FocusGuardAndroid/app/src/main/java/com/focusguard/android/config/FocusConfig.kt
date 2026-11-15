package com.focusguard.android.config

import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.provider.Settings
import com.focusguard.android.accessibility.FocusGuardService
import com.focusguard.android.schedule.WeeklyPolicy
import java.time.LocalDate
import java.time.ZoneId

object FocusConfig {
    private const val PREFS = "focus_guard_settings_v2"
    private const val KEY_MASTER = "protection_enabled"
    private const val KEY_DISCLOSURE = "disclosure_accepted"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_TRACKED = "usage_reward_packages"
    private const val KEY_GEMS = "gem_balance"
    private const val KEY_UNLOCK_START = "day_pass_start"
    private const val KEY_UNLOCK_UNTIL = "day_pass_until"
    private const val KEY_PASS_HISTORY = "day_pass_history"
    private const val KEY_SERVICE_SESSIONS = "service_sessions"
    private const val KEY_LAST_BASE_PROCESSED = "last_base_reward_processed_date"
    private const val KEY_LAST_USAGE_PROCESSED = "last_usage_reward_processed_date"
    private const val KEY_LAST_SUMMARY = "last_reward_summary"
    private const val KEY_LAST_SUMMARY_DATE = "last_reward_summary_date"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER, false)

    fun activateProtection(context: Context) {
        prefs(context).edit().putBoolean(KEY_MASTER, true).commit()
    }

    fun hasAcceptedDisclosure(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISCLOSURE, false)

    fun setDisclosureAccepted(context: Context, accepted: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISCLOSURE, accepted).commit()
    }

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()

    /** System-critical apps, the default launcher and dialer cannot be put on the block list. */
    fun protectedPackages(context: Context): Set<String> {
        val protectedApps = mutableSetOf(
            context.packageName,
            FocusGuardService.YOUTUBE_PACKAGE,
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
        val pm = context.packageManager
        runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()?.let(protectedApps::add)
        runCatching {
            pm.resolveActivity(
                Intent(Settings.ACTION_SETTINGS),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()?.let(protectedApps::add)
        runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_DIAL),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()?.let(protectedApps::add)
        return protectedApps
    }

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        val p = prefs(context)
        val normalized = packages - protectedPackages(context)
        val trackers = p.getStringSet(KEY_TRACKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newlyBlocked = trackers.intersect(normalized)
        trackers.removeAll(newlyBlocked)
        val editor = p.edit()
            .putStringSet(KEY_BLOCKED, normalized.toSet())
            .putStringSet(KEY_TRACKED, trackers.toSet())
        newlyBlocked.forEach { editor.remove(trackedSinceKey(it)) }
        editor.commit()
    }

    fun trackedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_TRACKED, emptySet())?.toSet() ?: emptySet()

    /** Usage-reward apps and fully blocked apps are kept disjoint to prevent zero-use farming. */
    fun setTrackedPackages(context: Context, packages: Set<String>) {
        val p = prefs(context)
        val blocked = blockedPackages(context)
        val normalized = packages - blocked - context.packageName
        val old = trackedPackages(context)
        val editor = p.edit().putStringSet(KEY_TRACKED, normalized.toSet())
        (old - normalized).forEach { editor.remove(trackedSinceKey(it)) }
        (normalized - old).forEach { editor.putLong(trackedSinceKey(it), System.currentTimeMillis()) }
        editor.commit()
    }

    private fun trackedSinceKey(packageName: String) = "tracked_since_$packageName"

    fun trackerSelectedBeforeDay(context: Context, packageName: String, dayStart: Long): Boolean =
        prefs(context).getLong(trackedSinceKey(packageName), Long.MAX_VALUE) <= dayStart

    fun gemBalance(context: Context): Int = prefs(context).getInt(KEY_GEMS, 0).coerceAtLeast(0)

    fun isDayPassActive(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L) > now

    fun dayPassUntil(context: Context): Long = prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L)

    /** Spends 30 gems and adds exactly 24 hours to the current pass, if one is active. */
    @Synchronized
    fun redeemDayPass(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val balance = p.getInt(KEY_GEMS, 0)
        if (balance < GemRules.DAY_PASS_COST) return false

        val currentUntil = p.getLong(KEY_UNLOCK_UNTIL, 0L)
        val activeUntil = maxOf(now, currentUntil)
        val start = if (currentUntil > now) p.getLong(KEY_UNLOCK_START, now) else now
        val newUntil = activeUntil + GemRules.DAY_PASS_DURATION_MS
        val history = p.getStringSet(KEY_PASS_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val historyCutoff = now - 90L * 24L * 60L * 60L * 1000L
        history.removeAll { record ->
            val end = record.substringAfter(':', "").toLongOrNull() ?: 0L
            end <= historyCutoff
        }
        history.add("$start:$newUntil")

        return p.edit()
            .putInt(KEY_GEMS, balance - GemRules.DAY_PASS_COST)
            .putLong(KEY_UNLOCK_START, start)
            .putLong(KEY_UNLOCK_UNTIL, newUntil)
            .putStringSet(KEY_PASS_HISTORY, history)
            .commit()
    }

    fun passOverlapsDay(context: Context, dayStart: Long, dayEnd: Long): Boolean {
        val history = prefs(context).getStringSet(KEY_PASS_HISTORY, emptySet()) ?: emptySet()
        return history.any { record ->
            val parts = record.split(':')
            val start = parts.getOrNull(0)?.toLongOrNull() ?: return@any false
            val end = parts.getOrNull(1)?.toLongOrNull() ?: return@any false
            start < dayEnd && end > dayStart
        }
    }

    /** Store service connection intervals so a base gem is only awarded for a fully covered day. */
    @Synchronized
    fun markServiceConnected(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val sessions = p.getStringSet(KEY_SERVICE_SESSIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val cutoff = now - 90L * 24L * 60L * 60L * 1000L
        val updated = sessions.mapNotNull { record ->
            val parts = record.split(':')
            val start = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (start < cutoff) return@mapNotNull null
            if (parts.size == 2 && parts[1] == "0") "$start:$now" else record
        }.toMutableSet()
        updated.add("$now:0")
        p.edit().putStringSet(KEY_SERVICE_SESSIONS, updated).commit()
    }

    @Synchronized
    fun markServiceDisconnected(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val sessions = p.getStringSet(KEY_SERVICE_SESSIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val updated = sessions.map { record ->
            val parts = record.split(':')
            if (parts.size == 2 && parts[1] == "0") "${parts[0]}:$now" else record
        }.toMutableSet()
        p.edit().putStringSet(KEY_SERVICE_SESSIONS, updated).commit()
    }

    fun serviceCoveredWholeDay(context: Context, dayStart: Long, dayEnd: Long): Boolean {
        val sessions = prefs(context).getStringSet(KEY_SERVICE_SESSIONS, emptySet()) ?: emptySet()
        return sessions.any { record ->
            val parts = record.split(':')
            val start = parts.getOrNull(0)?.toLongOrNull() ?: return@any false
            val end = parts.getOrNull(1)?.toLongOrNull() ?: return@any false
            start <= dayStart && (end == 0L || end >= dayEnd)
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FocusGuardService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun restrictionsActive(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        WeeklyPolicy.restrictionsActive(isMasterEnabled(context)) && !isDayPassActive(context, now)

    fun ensureRewardCursor(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val editor = p.edit()
        if (!p.contains(KEY_LAST_BASE_PROCESSED)) editor.putString(KEY_LAST_BASE_PROCESSED, today)
        if (!p.contains(KEY_LAST_USAGE_PROCESSED)) editor.putString(KEY_LAST_USAGE_PROCESSED, today)
        editor.commit()
    }

    fun lastBaseProcessedDate(context: Context): LocalDate? =
        prefs(context).getString(KEY_LAST_BASE_PROCESSED, null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun lastUsageProcessedDate(context: Context): LocalDate? =
        prefs(context).getString(KEY_LAST_USAGE_PROCESSED, null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    @Synchronized
    fun recordBaseRewardDay(context: Context, date: LocalDate, gems: Int, summary: String) {
        val p = prefs(context)
        val last = lastBaseProcessedDate(context)
        if (last != null && !date.isAfter(last)) return
        recordGemLedgerEntry(p, date, gems, summary)
        p.edit().putString(KEY_LAST_BASE_PROCESSED, date.toString()).commit()
    }

    @Synchronized
    fun recordUsageRewardDay(context: Context, date: LocalDate, gems: Int, summary: String) {
        val p = prefs(context)
        val last = lastUsageProcessedDate(context)
        if (last != null && !date.isAfter(last)) return
        recordGemLedgerEntry(p, date, gems, summary)
        p.edit().putString(KEY_LAST_USAGE_PROCESSED, date.toString()).commit()
    }

    private fun recordGemLedgerEntry(
        p: android.content.SharedPreferences,
        date: LocalDate,
        gems: Int,
        summary: String
    ) {
        val previousDate = p.getString(KEY_LAST_SUMMARY_DATE, "")
        val previousSummary = p.getString(KEY_LAST_SUMMARY, "").orEmpty()
        val combined = if (previousDate == date.toString() && previousSummary.isNotBlank()) {
            "$previousSummary · $summary"
        } else summary
        p.edit()
            .putInt(KEY_GEMS, (p.getInt(KEY_GEMS, 0) + gems).coerceAtLeast(0))
            .putString(KEY_LAST_SUMMARY_DATE, date.toString())
            .putString(KEY_LAST_SUMMARY, combined)
            .commit()
    }

    fun lastRewardSummary(context: Context): Pair<String, String> =
        prefs(context).getString(KEY_LAST_SUMMARY_DATE, "") .orEmpty() to
            prefs(context).getString(KEY_LAST_SUMMARY, "No rewards processed yet").orEmpty()
}
