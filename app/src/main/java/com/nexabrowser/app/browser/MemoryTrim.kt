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
 *    are dropped on any trim at all. Rebuilding them costs a few hundred hash
 *    writes and a handful of stat() calls.
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
     * True for every level that means "the system wants memory back". The lowest
     * one is [RUNNING_MODERATE], so an unknown or zero level frees nothing.
     */
    fun dropCaches(level: Int): Boolean = level >= RUNNING_MODERATE

    /**
     * True only when the process is genuinely short of memory — not on a routine
     * trip to the background, where [UI_HIDDEN] and [BACKGROUND] arrive.
     */
    fun freeRendererMemory(level: Int): Boolean = when (level) {
        RUNNING_LOW, RUNNING_CRITICAL, MODERATE, COMPLETE -> true
        else -> false
    }
}
