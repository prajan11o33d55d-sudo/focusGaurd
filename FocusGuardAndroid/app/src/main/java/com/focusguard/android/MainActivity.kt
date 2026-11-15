package com.focusguard.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.android.accessibility.FocusGuardService
import com.focusguard.android.config.FocusConfig
import com.focusguard.android.config.GemRules
import com.focusguard.android.usage.UsageAccess
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var page: LinearLayout
    private var usageRows: LinearLayout? = null

    private val bg = Color.rgb(10, 15, 29)
    private val cardColor = Color.rgb(20, 29, 48)
    private val cardAlt = Color.rgb(27, 39, 62)
    private val white = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(158, 171, 194)
    private val teal = Color.rgb(71, 223, 190)
    private val violet = Color.rgb(143, 124, 255)
    private val amber = Color.rgb(255, 194, 98)
    private val red = Color.rgb(255, 117, 129)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        FocusConfig.ensureRewardCursor(this)
        scheduleGemReconciliation()
        buildShell()
        renderDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) {
            scheduleGemReconciliation()
            renderDashboard()
        }
    }

    private fun buildShell() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
            isFillViewport = true
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(34))
        }
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun renderDashboard() {
        page.removeAllViews()
        usageRows = null

        val serviceEnabled = FocusConfig.isAccessibilityServiceEnabled(this)
        val passActive = FocusConfig.isDayPassActive(this)
        val protectionActive = FocusConfig.restrictionsActive(this)
        val consented = FocusConfig.hasAcceptedDisclosure(this)
        val gems = FocusConfig.gemBalance(this)
        val blocked = FocusConfig.blockedPackages(this)
        val tracked = FocusConfig.trackedPackages(this)

        // Brand row
        val brand = row(page)
        val mark = text("◈", 28f, violet, true)
        brand.addView(mark, weightParams())
        val brandCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandCopy.addView(text("FOCUS GUARD", 19f, white, true))
        brandCopy.addView(text("YOUR DAILY FOCUS SYSTEM", 10f, muted, true))
        brand.addView(brandCopy, LinearLayout.LayoutParams(0, -2, 4f))
        val stateLabel = when {
            passActive -> "DAY PASS"
            serviceEnabled && protectionActive -> "PROTECTED"
            !consented -> "SETUP"
            else -> "ACTION NEEDED"
        }
        brand.addView(chip(stateLabel, when {
            passActive -> amber
            serviceEnabled && protectionActive -> teal
            else -> red
        }))
        addGap(page, 18)

        // Hero status card
        val hero = card(page, intArrayOf(Color.rgb(45, 48, 105), Color.rgb(29, 42, 75)))
        hero.addView(text("FOCUS FIRST. SCROLL LESS.", 11f, 0xFFD3D0FF.toInt(), true))
        addGap(hero, 10)
        val heroTitle = when {
            passActive -> "Your day pass is active"
            !consented -> "Set up your protection"
            !serviceEnabled -> "One step left to protect your focus"
            protectionActive -> "Your restrictions are active"
            else -> "Protection is paused"
        }
        hero.addView(text(heroTitle, 24f, white, true))
        addGap(hero, 7)
        val heroDetail = when {
            passActive -> "All blockers are paused until ${formatDateTime(FocusConfig.dayPassUntil(this))}. They will switch back on automatically."
            !consented -> "Activate Focus Guard, then enable its accessibility service in Android Settings."
            !serviceEnabled -> "Enable Focus Guard in Accessibility settings. Restrictions apply every day of the week."
            else -> "Shorts and the apps you choose are blocked every day. Standard YouTube videos stay available."
        }
        hero.addView(text(heroDetail, 14f, 0xFFD7DDF0.toInt(), false))
        addGap(hero, 15)
        hero.addView(text(
            if (serviceEnabled) "● ACCESSIBILITY SERVICE ENABLED" else "○ ACCESSIBILITY SERVICE NOT ENABLED",
            11f, if (serviceEnabled) teal else amber, true
        ))
        if (!serviceEnabled) {
            addGap(hero, 12)
            actionButton(hero, if (consented) "Open accessibility settings" else "Activate protection", true, violet) {
                if (!consented) showDisclosureAndContinue() else openAccessibilitySettings()
            }
        }

        // Gem wallet
        val wallet = card(page, intArrayOf(Color.rgb(43, 32, 82), Color.rgb(27, 31, 58)))
        val walletTop = row(wallet)
        walletTop.addView(text("GEM WALLET", 11f, 0xFFD7CFFF.toInt(), true), weightParams())
        walletTop.addView(chip("30 ◈ = 24 HOURS", violet))
        addGap(wallet, 8)
        val gemRow = row(wallet)
        gemRow.addView(text("◆", 35f, 0xFFFFD36E.toInt(), true))
        val gemCount = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        gemCount.addView(text(gems.toString(), 34f, white, true))
        gemCount.addView(text("AVAILABLE GEMS", 10f, 0xFFC6C1E8.toInt(), true))
        gemRow.addView(gemCount, LinearLayout.LayoutParams(0, -2, 1f))
        addGap(wallet, 12)
        actionButton(
            wallet,
            if (passActive) "Add 24 hours · 30 gems" else "Unlock restrictions for 24 hours · 30 gems",
            gems >= GemRules.DAY_PASS_COST,
            if (gems >= GemRules.DAY_PASS_COST) violet else Color.rgb(83, 79, 108)
        ) { confirmDayPass() }
        addGap(wallet, 7)
        wallet.addView(text(
            "A pass lasts 24 hours from redemption. More passes extend an active pass. Restrictions resume automatically when time expires.",
            11f, 0xFFC6C1E8.toInt(), false
        ))

        addSectionLabel(page, "YOUR FOCUS RULES", "These rules apply Monday through Sunday. There is no weekly free day.")
        val rules = card(page)
        addRuleRow(rules, "▶", "YouTube Shorts", "Exit YouTube when the Shorts player is identified", teal)
        addDivider(rules)
        addRuleRow(rules, "▣", "Selected streaming apps", "Send the phone Home as soon as a blocked app opens", teal)
        addDivider(rules)
        addRuleRow(rules, "24h", "One-day pass", "Spend 30 gems to pause every restriction for 24 hours", amber)

        // Blocked apps
        addSectionLabel(page, "APPS TO BLOCK", "Choose streaming and entertainment apps. YouTube is handled separately so normal videos remain usable.")
        val blockedCard = card(page)
        val blockedHead = row(blockedCard)
        blockedHead.addView(text("${blocked.size} apps selected", 16f, white, true), weightParams())
        blockedHead.addView(chip("BLOCK LIST", red))
        addGap(blockedCard, 8)
        blockedCard.addView(text(
            if (blocked.isEmpty()) "No additional apps selected yet." else blocked.sorted().take(5).joinToString(" · ") { appLabel(it) } + if (blocked.size > 5) " · +${blocked.size - 5} more" else "",
            13f, muted, false
        ))
        addGap(blockedCard, 12)
        actionButton(blockedCard, "Choose apps to block", true, cardAlt) { showAppPicker(blocking = true) }

        // Usage rewards
        addSectionLabel(page, "EARN GEMS WITH DAILY LIMITS", "Track a separate set of apps. These apps cannot also be on the block list.")
        val rewards = card(page)
        val trackerHead = row(rewards)
        trackerHead.addView(text("Usage reward apps", 16f, white, true), weightParams())
        trackerHead.addView(chip("${tracked.size} TRACKED", teal))
        addGap(rewards, 8)
        rewards.addView(text("For each selected app, each completed day: under 10 min earns 5 gems; 10–30 min earns 3; over 30 min earns 0.", 12f, muted, false))
        addGap(rewards, 10)
        val thresholds = row(rewards)
        thresholds.addView(rewardTile("< 10 min", "+5 ◈", teal), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        thresholds.addView(rewardTile("10–30 min", "+3 ◈", amber), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        thresholds.addView(rewardTile("> 30 min", "+0 ◈", muted), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        addGap(rewards, 12)
        rewards.addView(text(
            if (tracked.isEmpty()) "Select apps you want to keep under 30 minutes per day. Usage rewards are calculated separately for each app." else tracked.sorted().take(5).joinToString(" · ") { appLabel(it) } + if (tracked.size > 5) " · +${tracked.size - 5} more" else "",
            12f, muted, false
        ))
        addGap(rewards, 12)
        actionButton(rewards, "Select usage-reward apps", true, cardAlt) { showAppPicker(blocking = false) }
        addGap(rewards, 8)
        val permissionOk = UsageAccess.hasPermission(this)
        actionButton(
            rewards,
            if (permissionOk) "Usage access enabled ✓" else "Enable Usage Access to calculate gems",
            true,
            if (permissionOk) Color.rgb(31, 82, 75) else Color.rgb(112, 75, 33)
        ) { openUsageAccessSettings() }
        addGap(rewards, 10)
        rewards.addView(text(
            "Usage data stays on this phone. Android requires you to grant Usage Access in Settings. Completed-day rewards are checked after 06:00 the next day.",
            11f, muted, false
        ))

        val today = card(page)
        today.addView(text("TODAY'S APP USAGE", 11f, muted, true))
        addGap(today, 8)
        usageRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        today.addView(usageRows)
        populateTodayUsage(usageRows!!, tracked, permissionOk)

        // Full-day reward details
        val summary = FocusConfig.lastRewardSummary(this)
        val history = card(page)
        history.addView(text("LATEST GEM CHECK", 11f, muted, true))
        addGap(history, 7)
        history.addView(text(
            if (summary.first.isBlank()) "No completed day has been processed yet." else "${summary.first}: ${summary.second}",
            13f, white, false
        ))
        addGap(history, 8)
        history.addView(text("You also earn +1 gem when the restrictions stay active for the entire calendar day and the accessibility service remains connected throughout that day.", 11f, muted, false))

        addSectionLabel(page, "SETUP & RELIABILITY", "Both permissions are controlled by Android. Focus Guard cannot silently grant them.")
        val setup = card(page)
        addSetupRow(setup, "Accessibility service", if (serviceEnabled) "Enabled" else "Not enabled", serviceEnabled)
        addGap(setup, 9)
        actionButton(setup, "Open Accessibility settings", true, cardAlt) { openAccessibilitySettings() }
        addDivider(setup)
        addSetupRow(setup, "Usage Access", if (permissionOk) "Enabled" else "Not enabled", permissionOk)
        addGap(setup, 9)
        actionButton(setup, "Open Usage Access settings", true, cardAlt) { openUsageAccessSettings() }

        addGap(page, 12)
        page.addView(text(
            "Privacy: foreground app names and selected-app usage totals are processed locally. Focus Guard does not take screenshots, record keystrokes, or upload usage history. You can disable the Android accessibility service or uninstall the app at any time.",
            11f, muted, false
        ).apply { setPadding(dp(4), dp(5), dp(4), dp(12)) })
    }

    private fun populateTodayUsage(target: LinearLayout, tracked: Set<String>, permissionOk: Boolean) {
        if (!permissionOk) {
            target.addView(text("Enable Usage Access to see today's app totals.", 12f, amber, false))
            return
        }
        if (tracked.isEmpty()) {
            target.addView(text("Choose usage-reward apps to see their daily totals here.", 12f, muted, false))
            return
        }
        target.addView(text("Loading today's usage…", 12f, muted, false))
        thread(name = "focus-usage-summary") {
            val result = tracked.sorted().map { pkg ->
                val millis = UsageAccess.todayUsage(this, pkg)
                appLabel(pkg) to millis
            }
            runOnUiThread {
                if (isFinishing || target !== usageRows) return@runOnUiThread
                target.removeAllViews()
                result.forEachIndexed { index, (label, millis) ->
                    val line = row(target)
                    line.addView(text(label, 13f, white, false), weightParams())
                    line.addView(text(if (millis < 0) "Unavailable" else formatMinutes(millis), 12f, teal, true))
                    if (index != result.lastIndex) addDivider(target)
                }
            }
        }
    }

    private fun showAppPicker(blocking: Boolean) {
        val protectedPackages = FocusConfig.protectedPackages(this)
        val apps = installedApps().filter { app ->
            if (blocking) app.packageName !in protectedPackages
            else app.packageName !in FocusConfig.blockedPackages(this)
        }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No eligible apps found", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = if (blocking) FocusConfig.blockedPackages(this).toMutableSet()
            else FocusConfig.trackedPackages(this).toMutableSet()
        val labels = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].packageName in selected }
        AlertDialog.Builder(this)
            .setTitle(if (blocking) "Choose apps to block" else "Choose usage-reward apps")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                if (blocking) FocusConfig.setBlockedPackages(this, emptySet())
                else FocusConfig.setTrackedPackages(this, emptySet())
                renderDashboard()
            }
            .setPositiveButton("Save selection") { _, _ ->
                if (blocking) FocusConfig.setBlockedPackages(this, selected)
                else FocusConfig.setTrackedPackages(this, selected)
                renderDashboard()
            }
            .show()
    }

    private data class AppChoice(val label: String, val packageName: String)

    private fun installedApps(): List<AppChoice> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = packageManager.queryIntentActivities(launchIntent, 0)
        return activities.mapNotNull { resolve ->
            val info = resolve.activityInfo ?: return@mapNotNull null
            val pkg = info.packageName
            if (pkg == packageName) return@mapNotNull null
            val label = resolve.loadLabel(packageManager)?.toString() ?: pkg
            AppChoice(label, pkg)
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun appLabel(pkg: String): String = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    }.getOrDefault(pkg)

    private fun confirmDayPass() {
        val balance = FocusConfig.gemBalance(this)
        if (balance < GemRules.DAY_PASS_COST) {
            Toast.makeText(this, "You need 30 gems to unlock a day", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Use 30 gems?")
            .setMessage("This spends 30 gems and pauses Shorts and all selected app restrictions for 24 hours. Restrictions will resume automatically. Your pass can be extended with another 30 gems.")
            .setNegativeButton("Keep restrictions on", null)
            .setPositiveButton("Redeem 30 gems") { _, _ ->
                if (FocusConfig.redeemDayPass(this)) {
                    Toast.makeText(this, "Day pass activated for 24 hours", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Not enough gems", Toast.LENGTH_SHORT).show()
                }
                renderDashboard()
            }
            .show()
    }

    private fun showDisclosureAndContinue() {
        AlertDialog.Builder(this)
            .setTitle("How Focus Guard works")
            .setMessage(
                "Focus Guard uses Android Accessibility to detect foreground app changes and inspect only YouTube's visible accessibility interface for Shorts-player signals. When a selected app opens or Shorts is identified, it sends the phone to Home. It does not capture screenshots or read typed text. YouTube changes can affect Shorts detection. Separately, Android Usage Access is used only to read daily foreground-time totals for the apps you select for gem rewards. Both types of data stay on this device. You can turn off either permission in Android Settings or uninstall the app. Continue only if you understand and agree."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand and agree") { _, _ ->
                FocusConfig.setDisclosureAccepted(this, true)
                FocusConfig.activateProtection(this)
                openAccessibilitySettings()
            }
            .show()
    }

    private fun openAccessibilitySettings() {
        if (!FocusConfig.hasAcceptedDisclosure(this)) {
            showDisclosureAndContinue()
            return
        }
        FocusConfig.activateProtection(this)
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Open Settings → Special app access → Usage access", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleGemReconciliation() {
        runCatching {
            val request = OneTimeWorkRequestBuilder<DailyGemWorker>().build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                "focus-guard-gem-reconcile-now",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    private fun card(parent: LinearLayout, gradient: IntArray? = null): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = rounded(gradient ?: intArrayOf(cardColor), 22)
            clipToPadding = false
            elevation = dp(2).toFloat()
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return box
    }

    private fun row(parent: LinearLayout): LinearLayout {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(line, LinearLayout.LayoutParams(-1, -2))
        return line
    }

    private fun rewardTile(label: String, reward: String, color: Int): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(12), dp(5), dp(12))
            background = rounded(intArrayOf(cardAlt), 14)
        }
        tile.addView(text(reward, 17f, color, true))
        addGap(tile, 4)
        tile.addView(text(label, 10f, muted, true))
        return tile
    }

    private fun addRuleRow(parent: LinearLayout, icon: String, title: String, detail: String, tint: Int) {
        val r = row(parent)
        r.addView(text(icon, 21f, tint, true), LinearLayout.LayoutParams(dp(42), -2))
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 14f, white, true))
        addGap(copy, 3)
        copy.addView(text(detail, 11f, muted, false))
        r.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(4)))
    }

    private fun addSetupRow(parent: LinearLayout, name: String, state: String, ok: Boolean) {
        val r = row(parent)
        r.addView(text(name, 13f, white, true), weightParams())
        r.addView(text("${if (ok) "●" else "○"} $state", 12f, if (ok) teal else amber, true))
    }

    private fun addSectionLabel(parent: LinearLayout, title: String, subtitle: String) {
        addGap(parent, 5)
        parent.addView(text(title, 12f, 0xFFBCC8E2.toInt(), true))
        addGap(parent, 4)
        parent.addView(text(subtitle, 11f, muted, false))
        addGap(parent, 10)
    }

    private fun actionButton(parent: LinearLayout, label: String, enabled: Boolean, color: Int, action: () -> Unit) {
        val button = TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (enabled) white else 0xFFB4B5C2.toInt())
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(intArrayOf(color), 15)
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.55f
            if (enabled) setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(0xFF2C3953.toInt()) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(11); bottomMargin = dp(11) })
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun chip(label: String, color: Int): TextView = TextView(this).apply {
        text = label
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        setPadding(dp(9), dp(7), dp(9), dp(7))
        background = rounded(intArrayOf(Color.argb(32, Color.red(color), Color.green(color), Color.blue(color))), 30)
        gravity = Gravity.CENTER
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun rounded(colors: IntArray, radiusDp: Int): GradientDrawable =
        GradientDrawable(if (colors.size > 1) GradientDrawable.Orientation.TL_BR else GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun weightParams() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatMinutes(ms: Long): String = when {
        ms < 0 -> "Unavailable"
        ms < 60_000 -> "<1 min"
        else -> "${ms / 60_000} min"
    }

    private fun formatDateTime(epochMillis: Long): String =
        java.text.SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(java.util.Date(epochMillis))
}
