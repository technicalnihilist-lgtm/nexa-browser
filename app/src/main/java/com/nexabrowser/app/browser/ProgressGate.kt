package com.nexabrowser.app.browser

import kotlin.math.abs

/**
 * Decides whether a page's progress tick is worth publishing, and whether a tab
 * the user has already stopped can be pushed back into "loading" by a late one.
 *
 * Two jobs, both of which used to be done inline in
 * [BrowserTabManager.onProgress] and neither of which was quite right:
 *
 *  * **Ticks are throttled.** WebView reports progress dozens of times per page,
 *    and publishing every tick re-rendered the 3dp loading bar and the tab strip
 *    for no visible gain. Only a first tick, a completion, or a jump of at least
 *    [MEANINGFUL_STEP] percent is published.
 *  * **A stopped load stays stopped.** [BrowserTabManager.onLoadStopped] set the
 *    last published value to 100 in the belief that this made a late tick
 *    harmless. It does not: the rule above treats any jump of 5 or more as
 *    meaningful, so a tick of 50 % arriving from the cancelled load after the
 *    stop is a 50-point jump — it was published, and the tab went back to
 *    `isLoading = true` with the progress bar half full. The toolbar offered
 *    "stop" again for a page the user had already stopped, and the tab strip
 *    redrew. Only a new document may start a load again, so the stop is recorded
 *    separately here and outranks the throttle rule.
 *
 * Kept free of Android types so both rules are unit tested on the JVM rather
 * than inferred from a device log.
 */
object ProgressGate {

    /**
     * Smallest change, in percent, that is worth publishing.
     *
     * The bar is 3dp tall and a page makes ~20 of these calls; anything finer is
     * a republish the user cannot see.
     */
    const val MEANINGFUL_STEP = 5

    /**
     * True when [progress] should be published for a tab whose last published
     * value was [previous] (null when this document has published none).
     *
     * @param stopped the user stopped this load and no new document has started
     *   since, so nothing this load reports can make it look active again.
     */
    fun isMeaningful(previous: Int?, stopped: Boolean, progress: Int): Boolean {
        // A stop outranks everything: the load it cancelled is over, and a tick
        // that arrives afterwards belongs to it, however large the jump.
        if (stopped) return false
        val last = previous ?: -1
        return progress == 0 || progress >= 100 || abs(progress - last) >= MEANINGFUL_STEP
    }
}
