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
 *  * **A load that is over stays over.** [BrowserTabManager.onLoadStopped] set
 *    the last published value to 100 in the belief that this made a late tick
 *    harmless. It does not: the rule above treats any jump of 5 or more as
 *    meaningful, so a tick of 50 % arriving from the cancelled load after the
 *    stop is a 50-point jump — it was published, and the tab went back to
 *    `isLoading = true` with the progress bar half full. The toolbar offered
 *    "stop" again for a page the user had already stopped, and the tab strip
 *    redrew. Only a new document may start a load again, so the stop is recorded
 *    separately here and outranks the throttle rule.
 *
 *    The same reasoning applies to a load that *finished*: `onPageFinished`
 *    publishes 100, and a late tick of, say, 80 from that document is a
 *    20-point jump the throttle accepts — it flipped the finished tab back to
 *    `isLoading = true`, so the toolbar offered "stop" and the strip showed a
 *    spinner for a page that was fully loaded. Both cases are the one flag
 *    below, because they mean the same thing: this document's load is over.
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
     * @param loadOver the load this tick belongs to is already over — the user
     *   stopped it, or the document finished — and no new document has started
     *   since, so nothing it reports can make the tab look active again.
     */
    fun isMeaningful(previous: Int?, loadOver: Boolean, progress: Int): Boolean {
        // A load that is over outranks everything: a tick that arrives afterwards
        // belongs to it, however large the jump.
        if (loadOver) return false
        // The first tick of a document always publishes. It used to be folded
        // into the throttle below as `previous ?: -1`, so a first tick of 1–4 %
        // was a jump of at most four points and was dropped — the loading bar
        // stayed empty until the page happened to reach 5 %.
        val last = previous ?: return true
        return progress == 0 || progress >= 100 || abs(progress - last) >= MEANINGFUL_STEP
    }
}
