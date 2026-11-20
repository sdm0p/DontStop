package com.dontstop.tracking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dontstop.storage.TrackingSettings

class DontStopAccessibilityService : AccessibilityService() {
    private val engine by lazy { TrackingEngine.get(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Enabling/rebinding while Instagram is already open need not produce a
        // window transition. Seed the engine from the current window instead.
        engine.onForegroundChanged(rootInActiveWindow?.packageName?.toString())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Global window transitions are required to stop audio and remove the overlay
                // immediately when the user leaves a tracked app. No window text is inspected.
                // Window-state events identify the app that just became active. Prefer
                // that package over rootInActiveWindow, which can briefly still point to
                // the previous tracked app during a transition.
                val eventPackage = event.packageName?.toString()
                val rootPackage = rootInActiveWindow?.packageName?.toString()
                val foregroundPackage = when (eventPackage) {
                    packageName, "com.android.systemui" -> rootPackage ?: eventPackage
                    else -> eventPackage ?: rootPackage
                }
                engine.onForegroundChanged(foregroundPackage)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val packageName = event.packageName?.toString() ?: return
                if (packageName !in TrackingSettings.SUPPORTED_PACKAGES) return
                // Recover foreground state after a service restart or a transient
                // system window, even when no app window transition was emitted.
                if (rootInActiveWindow?.packageName?.toString() == packageName) {
                    engine.onForegroundChanged(packageName)
                }
                val shortVideo = isShortVideo(event.source, packageName)
                if (com.dontstop.BuildConfig.DEBUG) android.util.Log.d("DontStopTracking", "Scroll package=$packageName shortVideo=$shortVideo")
                engine.onPotentialShortVideoScroll(packageName, shortVideo)
            }
        }
    }

    private fun isShortVideo(source: AccessibilityNodeInfo?, packageName: String): Boolean {
        val strongMarkers = when (packageName) {
            "com.instagram.android" -> listOf("clips_viewer", "clips_video", "reel_viewer")
            "com.google.android.youtube" -> listOf("reel_watch", "shorts_player", "shorts_pivot")
            "com.facebook.katana" -> listOf("reels_viewer", "reel_viewer", "fullscreen_reel")
            else -> return false
        }
        val navigationMarkers = when (packageName) {
            "com.instagram.android" -> listOf("clips_tab", "reels")
            "com.google.android.youtube" -> listOf("shorts")
            "com.facebook.katana" -> listOf("reels")
            else -> emptyList()
        }

        // Scroll events commonly originate from a generic RecyclerView. Check its local
        // subtree and ancestors first, then the active window for a selected Reels/Shorts tab.
        val local = ArrayDeque<AccessibilityNodeInfo>()
        source?.let {
            local.add(it)
            var parent = it.parent
            repeat(6) {
                parent ?: return@repeat
                local.add(parent)
                parent = parent.parent
            }
        }
        if (containsMarker(local, strongMarkers, navigationMarkers, allowSelectedNavigation = false, limit = 64)) return true

        val root = rootInActiveWindow ?: return false
        return containsMarker(ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }, strongMarkers,
            navigationMarkers, allowSelectedNavigation = true, limit = 192)
    }

    private fun containsMarker(
        queue: ArrayDeque<AccessibilityNodeInfo>,
        strongMarkers: List<String>,
        navigationMarkers: List<String>,
        allowSelectedNavigation: Boolean,
        limit: Int
    ): Boolean {
        var inspected = 0
        while (queue.isNotEmpty() && inspected++ < limit) {
            val node = queue.removeFirst()
            val text = "${node.viewIdResourceName.orEmpty()} ${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}".lowercase()
            if (strongMarkers.any { it in text }) return true
            if (allowSelectedNavigation && node.isSelected && navigationMarkers.any { it in text }) return true
            for (i in 0 until minOf(node.childCount, 12)) node.getChild(i)?.let(queue::addLast)
        }
        return false
    }

    override fun onInterrupt() {
        engine.stop()
    }
    override fun onDestroy() { engine.stop(); super.onDestroy() }

}
