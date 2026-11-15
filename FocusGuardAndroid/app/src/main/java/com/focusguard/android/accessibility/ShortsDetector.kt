package com.focusguard.android.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Event-driven, best-effort detector for the dedicated YouTube Shorts viewer.
 * YouTube exposes no public stable Shorts-state API. This detector combines selected
 * Shorts navigation state, Shorts-specific view IDs, and the vertical-player controls.
 * It deliberately does not block a Shorts shelf or thumbnail in the normal YouTube feed.
 */
internal object ShortsDetector {
    private const val MAX_NODES = 650
    private const val MAX_DEPTH = 22

    private val actionWords = listOf(
        "like", "dislike", "comment", "comments", "share", "remix",
        "subscribe", "sound", "audio", "use this sound", "use audio"
    )
    // Compile once: the detector may be evaluated repeatedly while a Shorts feed scrolls.
    private val actionPatterns = actionWords.associateWith { word ->
        Regex("(^|[^a-z0-9])${Regex.escape(word)}([^a-z0-9]|$)")
    }
    private val whitespacePattern = Regex("\\s+")

    private val excludedShortsIds = listOf(
        "shorts_shelf", "shorts_thumbnail", "shorts_item", "shorts_recommendation",
        "shorts_rail", "shorts_entry"
    )
    private val knownPlayerIdFragments = listOf(
        "shorts_container", "shorts_player", "shorts_viewer", "shorts_progress_bar",
        "reel_player_page", "reel_player", "reel_recycler_view", "reel_progress_bar"
    )

    fun isConfirmedShorts(root: AccessibilityNodeInfo): Boolean {
        var visited = 0
        var selectedShortsDestination = false
        var shortsViewerTitle = false
        var strongShortsPlayerId = false
        var videoSurfacePresent = false
        val matchedActions = mutableSetOf<String>()
        val reelPlayerControls = mutableSetOf<String>()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES || depth > MAX_DEPTH) return
            visited++

            val text = normalize(node.text?.toString().orEmpty())
            val description = normalize(node.contentDescription?.toString().orEmpty())
            val viewId = normalize(node.viewIdResourceName.orEmpty())
            val combined = "$text $description"
            val excluded = excludedShortsIds.any { viewId.contains(it) }

            val labelMentionsShorts = combined == "shorts" ||
                combined.startsWith("shorts ") || combined.endsWith(" shorts")
            if (!excluded && (node.isSelected || node.isChecked) &&
                (labelMentionsShorts || viewId.contains("shorts"))) {
                selectedShortsDestination = true
            }
            if (!excluded && labelMentionsShorts && (viewId.contains("title") || viewId.contains("header"))) {
                shortsViewerTitle = true
            }

            val playerId = knownPlayerIdFragments.any { viewId.contains(it) } ||
                (viewId.contains("shorts") && (viewId.contains("player") || viewId.contains("viewer"))) ||
                (viewId.contains("reel") && (viewId.contains("player") || viewId.contains("viewer")))
            if (!excluded && playerId) strongShortsPlayerId = true

            val className = node.className?.toString().orEmpty()
            if (className.contains("SurfaceView", true) || className.contains("TextureView", true) ||
                viewId.contains("video_surface") || viewId.contains("player_surface")) {
                videoSurfacePresent = true
            }

            actionPatterns.forEach { (word, pattern) ->
                if (pattern.containsMatchIn(combined) || viewId.contains(word.replace(' ', '_'))) {
                    matchedActions.add(word)
                }
            }

            if (viewId.contains("reel_like") || viewId.contains("shorts_like")) {
                matchedActions.add("like")
                if (viewId.contains("reel_")) reelPlayerControls.add("like")
            }
            if (viewId.contains("reel_comment") || viewId.contains("shorts_comment")) {
                matchedActions.add("comments")
                if (viewId.contains("reel_")) reelPlayerControls.add("comments")
            }
            if (viewId.contains("reel_share") || viewId.contains("shorts_share")) {
                matchedActions.add("share")
                if (viewId.contains("reel_")) reelPlayerControls.add("share")
            }

            for (index in 0 until node.childCount) {
                if (visited >= MAX_NODES) break
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }

        visit(root, 0)

        return strongShortsPlayerId ||
            (selectedShortsDestination && (matchedActions.isNotEmpty() || videoSurfacePresent)) ||
            (shortsViewerTitle && matchedActions.size >= 3 && videoSurfacePresent) ||
            (reelPlayerControls.size >= 2 && videoSurfacePresent)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ').let {
            whitespacePattern.replace(it, " ").trim()
        }
}
