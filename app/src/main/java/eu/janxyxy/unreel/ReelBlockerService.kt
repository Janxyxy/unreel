package eu.janxyxy.unreel

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Watches Instagram's UI tree and backs out of Reels.
 *
 * Instagram changes its internal view IDs from time to time. All IDs live in the
 * lists below so they're easy to update. Run `adb logcat -s Unreel` with a debug
 * build to see what the service detects.
 */
class ReelBlockerService : AccessibilityService() {

    private enum class Origin { DM, OTHER }
    private enum class Reason { TAB, VIEWER, DM_REEL, DM_SWIPE }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var checkPending = false
    private var lastBlockAt = 0L

    /** Where the user was before the reel viewer opened. */
    private var origin = Origin.OTHER
    /** Origin of the currently open viewer, or null when no viewer is open. */
    private var viewerOrigin: Origin? = null
    private var viewerOpenedAt = 0L
    private var dmReelIndex = -1

    private val checkRunnable = Runnable {
        checkPending = false
        check()
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != IG) return
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!prefs.isActive) {
            viewerOrigin = null
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScrolled(event)
        scheduleCheck()
    }

    override fun onInterrupt() {}

    private fun scheduleCheck() {
        if (checkPending) return
        checkPending = true
        handler.postDelayed(checkRunnable, CHECK_DELAY_MS)
    }

    private fun check() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != IG) return

        if (isReelsTabSelected(root)) {
            viewerOrigin = null
            if (prefs.blockTab) block(Reason.TAB, root)
            return
        }

        if (isReelViewer(root)) {
            if (viewerOrigin == null) {
                viewerOrigin = origin
                viewerOpenedAt = SystemClock.uptimeMillis()
                dmReelIndex = -1
                Log.d(TAG, "viewer opened, origin=$origin")
            }
            if (viewerOrigin == Origin.DM) {
                if (!prefs.allowDmReels) block(Reason.DM_REEL, root)
            } else if (prefs.blockViewer) {
                block(Reason.VIEWER, root)
            }
            return
        }

        viewerOrigin = null
        origin = if (isDmThread(root)) Origin.DM else Origin.OTHER
    }

    /** A reel from DMs is allowed, but swiping to the next one isn't. */
    private fun onScrolled(event: AccessibilityEvent) {
        if (viewerOrigin != Origin.DM || !prefs.allowDmReels) return
        if (!isPagerEvent(event)) return

        val index = event.fromIndex
        if (index >= 0) {
            if (dmReelIndex < 0) {
                dmReelIndex = index
                return
            }
            if (index != dmReelIndex) block(Reason.DM_SWIPE, null)
        } else if (SystemClock.uptimeMillis() - viewerOpenedAt > DM_GRACE_MS) {
            // No item index available: treat any pager scroll after the reel settled as a swipe.
            block(Reason.DM_SWIPE, null)
        }
    }

    private fun isPagerEvent(event: AccessibilityEvent): Boolean {
        val id = event.source?.viewIdResourceName
        if (id != null) return VIEWER_IDS.any { id == IG_ID + it }
        return event.className?.toString()?.contains("ViewPager") == true
    }

    private fun block(reason: Reason, root: AccessibilityNodeInfo?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return
        lastBlockAt = now

        val wentHome = reason == Reason.TAB && root != null && clickHomeTab(root)
        if (!wentHome) performGlobalAction(GLOBAL_ACTION_BACK)

        prefs.recordBlock()
        Log.d(TAG, "blocked: $reason (home=$wentHome)")
        Toast.makeText(this, R.string.toast_blocked, Toast.LENGTH_SHORT).show()
    }

    // --- detection -------------------------------------------------------------

    private fun isReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        val tabs = findVisible(root, REELS_TAB_IDS)
        if (tabs.isNotEmpty()) return tabs.any { it.isSelected || it.hasSelectedChild() }

        // Fallback if the tab ID changed: a selected node described as "Reels" near the bottom.
        val screenH = resources.displayMetrics.heightPixels
        return findNode(root) { n ->
            n.isSelected && n.contentDescription?.toString()?.equals("Reels", true) == true &&
                n.boundsTop() > screenH * 0.8
        } != null
    }

    private fun isReelViewer(root: AccessibilityNodeInfo) =
        findVisible(root, VIEWER_IDS).isNotEmpty()

    private fun isDmThread(root: AccessibilityNodeInfo) =
        findVisible(root, DM_THREAD_IDS).isNotEmpty()

    private fun clickHomeTab(root: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = findVisible(root, HOME_TAB_IDS).firstOrNull()
        while (node != null && !node.isClickable) node = node.parent
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findVisible(root: AccessibilityNodeInfo, ids: List<String>): List<AccessibilityNodeInfo> =
        ids.flatMap { root.findAccessibilityNodeInfosByViewId(IG_ID + it) }
            .filter { it.isVisibleToUser }

    private fun findNode(
        root: AccessibilityNodeInfo,
        match: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < MAX_NODES) {
            val n = queue.removeFirst()
            if (match(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun AccessibilityNodeInfo.hasSelectedChild(): Boolean =
        (0 until childCount).any { getChild(it)?.isSelected == true }

    private fun AccessibilityNodeInfo.boundsTop(): Int {
        val r = android.graphics.Rect()
        getBoundsInScreen(r)
        return r.top
    }

    companion object {
        private const val TAG = "Unreel"
        private const val IG = "com.instagram.android"
        private const val IG_ID = "$IG:id/"

        private const val CHECK_DELAY_MS = 80L
        private const val BLOCK_COOLDOWN_MS = 800L
        private const val DM_GRACE_MS = 1500L
        private const val MAX_NODES = 800

        private val REELS_TAB_IDS = listOf("clips_tab")
        private val HOME_TAB_IDS = listOf("feed_tab")
        private val VIEWER_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_pager",
            "clips_viewer_container",
        )
        private val DM_THREAD_IDS = listOf(
            "row_thread_composer_edittext",
            "direct_thread_container",
            "thread_fragment_container",
        )
    }
}
