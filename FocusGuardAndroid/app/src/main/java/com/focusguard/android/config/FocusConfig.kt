package com.focusguard.android.config

import android.content.Context

/** Small, local-only configuration store. No screen content or usage history is persisted. */
object FocusConfig {
    private const val PREFS = "focus_guard_settings"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_DISCLOSURE = "disclosure_accepted"
    private const val KEY_BLOCKED = "blocked_packages"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER, false)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply()
    }

    fun hasAcceptedDisclosure(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISCLOSURE, false)

    fun setDisclosureAccepted(context: Context, accepted: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISCLOSURE, accepted).apply()
    }

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED, packages.toSet()).apply()
    }
}
