package com.nexabrowser.app.browser

/**
 * What the app should give back when the system reports memory pressure.
 *
 * Android delivers [android.content.ComponentCallbacks2.onTrimMemory] levels
 * from the moment free memory starts to run out up to the point where the
 * process is about to be killed. The app used to ignore them completely: it held
 * a host→decision table for the ad blocker, a stat() memo for stored favicons,
 * and one renderer process per open tab, and released none of it until the
 * system had to kill the whole process — losing every open tab.
 *
 * Two kinds of memory are handled differently:
 *  * Caches that are cheap to rebuild (ad-blocker decisions, favicon existence)
 *    are dropped on every level that reports real memory pressure. Rebuilding
 *    them costs a few hundred hash writes and a handful of stat() calls — which
 *    is why they are not also thrown away on the routine [UI_HIDDEN] and
 *    [BACKGROUND] levels, where the cost would land on the frame the user
 *    comes back to.
 *  * Renderer memory for background tabs is freed only on real pressure. A
 *    plain "app went to the background" ([UI_HIDDEN]) is routine and must not
 *    cost the user a repaint of their page when they come straight back.
 *
 * Kept free of Android types so the policy is unit tested rather than inferred
 * from a device log.
 */
object MemoryTrim {

    // Mirrors android.content.ComponentCallbacks2, so this file needs no
    // Android dependency.
    const val RUNNING_MODERATE = 5
    const val RUNNING_LOW = 10
    const val RUNNING_CRITICAL = 15
    const val UI_HIDDEN = 20
    const val BACKGROUND = 40
    const val MODERATE = 60
    const val COMPLETE = 80

    /**
     * True for every level that means the process is genuinely short of memory.
     *
     * This used to be `level >= RUNNING_MODERATE`, which is not a severity
     * order: [UI_HIDDEN] (20) and [BACKGROUND] (40) arrive on every ordinary trip
     * to the background and are lifecycle notifications, not pressure signals,
     * yet they sorted above the three `RUNNING_*` levels and so dropped the
     * rebuildable caches every time the user switched apps. Coming back then cost
     * a stat() for every stored favicon the visible lists draw (up to 512 of them,
     * on the main thread, on the frame the user returned to) and a full rebuild of
     * the ad blocker's host table, to reclaim a few hundred booleans and paths
     * that were never the reason the system was trimming.
     *
     * The two lifecycle levels are excluded here for the same reason
     * [freeRendererMemory] already excludes them: the caches are kept, and the
     * return to the app is as cheap as it was the moment the user left.
     */
    fun dropCaches(level: Int): Boolean = when (level) {
        RUNNING_MODERATE, RUNNING_LOW, RUNNING_CRITICAL, MODERATE, COMPLETE -> true
        else -> false
    }

    /**
     * True only when the process is genuinely short of memory — not on a routine
     * trip to the background, where [UI_HIDDEN] and [BACKGROUND] arrive.
     */
    fun freeRendererMemory(level: Int): Boolean = when (level) {
        RUNNING_LOW, RUNNING_CRITICAL, MODERATE, COMPLETE -> true
        else -> false
    }
}
