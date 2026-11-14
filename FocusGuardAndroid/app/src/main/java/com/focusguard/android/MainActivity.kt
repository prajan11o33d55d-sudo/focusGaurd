package com.focusguard.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.focusguard.android.accessibility.FocusGuardService
import com.focusguard.android.config.FocusConfig
import com.focusguard.android.schedule.WeeklyPolicy
import java.time.LocalDate
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var masterCheck: CheckBox
    private lateinit var appList: LinearLayout
    private val appRows = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        renderAppChoices()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) updateStatus()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(text("FOCUS GUARD", 26f, true))
        content.addView(text("Shorts and streaming distraction blocker", 15f, false).also {
            it.setPadding(0, dp(4), 0, dp(18))
        })

        statusText = text("Checking status…", 14f, false)
        statusText.setPadding(dp(12), dp(12), dp(12), dp(12))
        statusText.setBackgroundColor(0xFFEFF2FA.toInt())
        content.addView(statusText)

        masterCheck = CheckBox(this).apply {
            text = "Enable restrictions (Mon–Sat)"
            textSize = 16f
            isChecked = FocusConfig.isMasterEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                if (checked && !FocusConfig.hasAcceptedDisclosure(this@MainActivity)) {
                    isChecked = false
                    showDisclosure()
                } else {
                    FocusConfig.setMasterEnabled(this@MainActivity, checked)
                    updateStatus()
                }
            }
        }
        content.addView(masterCheck)

        val accessibilityButton = button("Open Android Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        content.addView(accessibilityButton)
        content.addView(text(
            "After enabling Focus Guard in Android Accessibility settings, return here. " +
                "Android requires you to enable or disable the service yourself.",
            13f, false
        ).apply { setPadding(0, dp(6), 0, dp(16)) })

        content.addView(sectionTitle("Weekly schedule"))
        content.addView(text(
            "Monday–Saturday: restrictions active.\nSunday: restrictions automatically paused.\n" +
                "Uses the phone's current local date and timezone.", 14f, false
        ).apply { setPadding(0, dp(4), 0, dp(18)) })

        content.addView(sectionTitle("Apps to block"))
        content.addView(text(
            "Select apps that should immediately send the phone to Home when opened. " +
                "YouTube is handled separately so regular videos remain allowed.",
            13f, false
        ).apply { setPadding(0, dp(4), 0, dp(8)) })

        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(appList)

        content.addView(button("Save selected apps") {
            val selected = appRows.filter { it.isChecked }
                .mapNotNull { it.tag as? String }.toSet()
            FocusConfig.setBlockedPackages(this, selected)
            Toast.makeText(this, "Saved ${selected.size} blocked apps", Toast.LENGTH_SHORT).show()
            updateStatus()
        }.apply { setPadding(0, dp(10), 0, dp(10)) })

        content.addView(text(
            "Privacy: settings stay on this device. The service reads foreground package names " +
                "and, only in YouTube, visible accessibility labels. It does not take screenshots " +
                "or upload screen content. You can turn off the switch or disable the service " +
                "in Android Settings at any time.", 12f, false
        ).apply { setPadding(0, dp(18), 0, 0) })
    }

    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility use and consent")
            .setMessage(
                "Focus Guard uses Android's AccessibilityService to identify the foreground " +
                    "app and inspect YouTube's visible accessibility labels for a Shorts-player " +
                    "signal. When a selected app is opened or Shorts is positively detected, " +
                    "it sends the device to Home. This data is processed locally; the app does " +
                    "not capture screenshots or send screen content to a server. Detection can " +
                    "be affected by YouTube updates. You can disable the service or uninstall " +
                    "the app at any time. Continue only if you understand and agree."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand and agree") { _, _ ->
                FocusConfig.setDisclosureAccepted(this, true)
                FocusConfig.setMasterEnabled(this, true)
                masterCheck.isChecked = true
                updateStatus()
            }
            .show()
    }

    private fun renderAppChoices() {
        appList.removeAllViews()
        appRows.clear()
        val saved = FocusConfig.blockedPackages(this)
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val activities = packageManager.queryIntentActivities(launchIntent, 0)

        val apps = activities.mapNotNull { resolve ->
            val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (info.packageName == packageName ||
                info.packageName == FocusGuardService.YOUTUBE_PACKAGE) return@mapNotNull null
            val label = resolve.loadLabel(packageManager)?.toString() ?: info.packageName
            Triple(label, info.packageName, info)
        }.distinctBy { it.second }
            .sortedBy { it.first.lowercase(Locale.ROOT) }

        if (apps.isEmpty()) {
            appList.addView(text("No launchable apps found.", 13f, false))
            return
        }

        apps.forEach { (label, packageName, _) ->
            val row = CheckBox(this).apply {
                text = "$label\n$packageName"
                textSize = 14f
                isChecked = packageName in saved
                tag = packageName
                setPadding(0, dp(5), 0, dp(5))
            }
            appRows.add(row)
            appList.addView(row)
        }
    }

    private fun updateStatus() {
        val master = FocusConfig.isMasterEnabled(this)
        val active = WeeklyPolicy.restrictionsActive(master)
        val serviceEnabled = isAccessibilityServiceEnabled()
        val day = LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val policy = when {
            !master -> "Master switch is OFF"
            !WeeklyPolicy.isScheduledDay() -> "Sunday exemption: restrictions OFF"
            else -> "Restrictions ACTIVE today"
        }
        statusText.text = "$day · $policy\nAccessibility service: " +
            if (serviceEnabled) "Enabled" else "Not enabled in Android Settings"
        statusText.setTextColor(if (active && serviceEnabled) 0xFF176B35.toInt() else 0xFF7A4A00.toInt())
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, FocusGuardService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    private fun sectionTitle(value: String) = text(value, 18f, true).apply {
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun text(value: String, size: Float, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(0xFF172033.toInt())
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
