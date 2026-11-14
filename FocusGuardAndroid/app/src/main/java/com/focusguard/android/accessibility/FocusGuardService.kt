package com.focusguard.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.android.config.FocusConfig
import com.focusguard.android.schedule.WeeklyPolicy

class FocusGuardService : AccessibilityService() {
    private var lastPackage: String? = null
    private var homeSentForCurrentEntry = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastPackage = null
        homeSentForCurrentEntry = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Reset the one-action latch when the foreground package changes.
        if (packageName != lastPackage) {
            lastPackage = packageName
            homeSentForCurrentEntry = false
        }

        if (packageName == this.packageName) return
        if (!WeeklyPolicy.restrictionsActive(FocusConfig.isMasterEnabled(this))) return
        if (homeSentForCurrentEntry) return

        // App-category blocking: one immediate Home action on entry to a selected app.
        if (packageName in FocusConfig.blockedPackages(this)) {
            exitToHome()
            return
        }

        // YouTube remains usable except when the Shorts player is positively identified.
        if (packageName != YOUTUBE_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        // Do not classify an overlay or another app's window as YouTube content.
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) return
        if (ShortsDetector.isConfirmedShorts(root)) {
            exitToHome()
        }
    }

    private fun exitToHome() {
        // Latch first so several content-change events cannot queue repeated actions.
        homeSentForCurrentEntry = true
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        // Nothing to release; this service keeps no wake locks or background workers.
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
