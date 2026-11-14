package com.focusguard.android.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Conservative, heuristic Shorts detector.
 *
 * YouTube has no public API that exposes a stable "this is a Shorts player" flag.
 * This matcher therefore requires both a Shorts destination hint and multiple player
 * action labels. Validate/tune it against accessibility trees from the target YouTube
 * versions and device language before relying on it. It deliberately avoids treating
 * the mere presence of the word "Shorts" as sufficient evidence.
 */
internal object ShortsDetector {
    private const val MAX_NODES = 350
    private const val MAX_DEPTH = 18

    private val playerActionMarkers = listOf(
        "comments",
        "share",
        "remix",
        "use this sound",
        "subscribe"
    )

    fun isConfirmedShorts(root: AccessibilityNodeInfo): Boolean {
        var visited = 0
        var shortsDestinationHint = false
        val matchedActions = mutableSetOf<String>()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES || depth > MAX_DEPTH) return
            visited++

            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty()
            val combined = normalize("$text $description $viewId")

            val mentionsShorts = combined.contains("shorts")
            val selectedShortsLabel = mentionsShorts && node.isSelected
            val shortsViewId = normalize(viewId).contains("shorts")
            if (selectedShortsLabel || shortsViewId) {
                shortsDestinationHint = true
            }

            playerActionMarkers.forEach { marker ->
                if (combined.contains(marker)) matchedActions.add(marker)
            }

            for (index in 0 until node.childCount) {
                if (visited >= MAX_NODES) break
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        visit(root, 0)
        return shortsDestinationHint && matchedActions.size >= 2
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')
}
