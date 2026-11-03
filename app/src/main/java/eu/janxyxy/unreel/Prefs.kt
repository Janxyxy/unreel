package eu.janxyxy.unreel

import android.content.Context
import java.time.LocalDate

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("unreel", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Block the Reels tab in the bottom bar. */
    var blockTab: Boolean
        get() = sp.getBoolean(KEY_BLOCK_TAB, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_TAB, v).apply()

    /** Block the full-screen reel viewer opened from feed, Explore, profiles, search. */
    var blockViewer: Boolean
        get() = sp.getBoolean(KEY_BLOCK_VIEWER, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_VIEWER, v).apply()

    /** Let a reel someone sent you in DMs play; swiping on to more reels is still blocked. */
    var allowDmReels: Boolean
        get() = sp.getBoolean(KEY_ALLOW_DM, false)
        set(v) = sp.edit().putBoolean(KEY_ALLOW_DM, v).apply()

    /** Wall-clock millis until which blocking is paused. */
    var pausedUntil: Long
        get() = sp.getLong(KEY_PAUSED_UNTIL, 0L)
        set(v) = sp.edit().putLong(KEY_PAUSED_UNTIL, v).apply()

    val isPaused: Boolean get() = System.currentTimeMillis() < pausedUntil
    val isActive: Boolean get() = enabled && !isPaused

    val blockedTotal: Int get() = sp.getInt(KEY_TOTAL, 0)
    val blockedToday: Int
        get() = if (sp.getString(KEY_DAY, null) == today()) sp.getInt(KEY_TODAY, 0) else 0

    fun recordBlock() {
        val day = today()
        sp.edit()
            .putString(KEY_DAY, day)
            .putInt(KEY_TODAY, blockedToday + 1)
            .putInt(KEY_TOTAL, blockedTotal + 1)
            .apply()
    }

    private fun today() = LocalDate.now().toString()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_TAB = "block_tab"
        private const val KEY_BLOCK_VIEWER = "block_viewer"
        private const val KEY_ALLOW_DM = "allow_dm_reels"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_TOTAL = "blocked_total"
        private const val KEY_TODAY = "blocked_today"
        private const val KEY_DAY = "blocked_day"
    }
}
