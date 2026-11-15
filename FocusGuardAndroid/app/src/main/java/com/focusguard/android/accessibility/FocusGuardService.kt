package com.focusguard.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.android.config.FocusConfig

class FocusGuardService : AccessibilityService() {
    private var currentForegroundPackage: String? = null
    private var blockedAppHomeSentForEntry = false
    private var shortsCurrentlyDetected = false
    private var lastShortsTreeScanAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusConfig.markServiceConnected(this)
        currentForegroundPackage = null
        blockedAppHomeSentForEntry = false
        shortsCurrentlyDetected = false
        lastShortsTreeScanAt = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!FocusConfig.restrictionsActive(this)) {
            // Clear the entry latch while a 24-hour pass is active. If the pass expires
            // while an app remains foregrounded, the next event must be enforced again.
            currentForegroundPackage = null
            blockedAppHomeSentForEntry = false
            shortsCurrentlyDetected = false
            lastShortsTreeScanAt = 0L
            return
        }

        val eventType = event.eventType
        val eventPackage = event.packageName?.toString()
        val candidatePackage = when {
            !eventPackage.isNullOrBlank() && eventPackage != "android" -> eventPackage
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED -> activeWindowPackage()
            else -> null
        } ?: return

        if (candidatePackage == packageName) return

        if (candidatePackage != currentForegroundPackage) {
            currentForegroundPackage = candidatePackage
            blockedAppHomeSentForEntry = false
            shortsCurrentlyDetected = false
            lastShortsTreeScanAt = 0L
        }

        // Selected streaming/OTT apps are interrupted on their first foreground window event.
        if (candidatePackage in FocusConfig.blockedPackages(this)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                exitBlockedApp()
            }
            return
        }

        // YouTube itself remains usable. Only inspect its own active accessibility tree.
        if (candidatePackage != YOUTUBE_PACKAGE) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val now = SystemClock.uptimeMillis()
        // Content, scroll and click events can arrive in bursts. Keep scans event-driven and bounded.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            now - lastShortsTreeScanAt < SHORTS_SCAN_INTERVAL_MS) return
        lastShortsTreeScanAt = now

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != YOUTUBE_PACKAGE) return
            val shortsNow = ShortsDetector.isConfirmedShorts(root)
            if (shortsNow && !shortsCurrentlyDetected) {
                shortsCurrentlyDetected = true
                if (!performGlobalAction(GLOBAL_ACTION_HOME)) shortsCurrentlyDetected = false
            } else if (!shortsNow) {
                // Reset when the user returns to a standard YouTube screen so a later
                // Shorts entry in the same YouTube session is blocked as well.
                shortsCurrentlyDetected = false
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun activeWindowPackage(): String? {
        val root = rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun exitBlockedApp() {
        if (blockedAppHomeSentForEntry) return
        blockedAppHomeSentForEntry = true
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) blockedAppHomeSentForEntry = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        FocusConfig.markServiceDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        // No periodic work, wake locks, screen capture, or background polling are used.
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val SHORTS_SCAN_INTERVAL_MS = 120L
    }
}
