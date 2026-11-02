package com.nexabrowser.app.ui.components

/**
 * Turns a page's raw scroll deltas into the single "the toolbar should be
 * hidden" decision the browser chrome needs.
 *
 * This used to live inline in [com.nexabrowser.app.ui.BrowserScreen] as a
 * `mutableStateOf(Int)` accumulator. That made it snapshot state, so every
 * scroll event — one per frame while the user drags or flings, i.e. 60–120 of
 * them a second — wrote to a Compose state object that the browser surface
 * reads, and invalidated the whole screen: the WebView host, the address bar,
 * the navigation bar and the start page were all recomposed for a delta that
 * almost never changes what is drawn. The accumulator is now a plain object, so
 * a scroll event costs one addition and no composition work at all, and
 * [onScroll] reports a change only when the toolbar actually has to move.
 *
 * The thresholds are in pixels; the decision is unchanged from the original
 * inline version, including the reversal rule (scrolling back the other way
 * starts counting from zero).
 */
class ScrollAccumulator(
    private val hideThresholdPx: Int,
    private val showThresholdPx: Int
) {

    /** Whether the chrome should currently be slid away. */
    var hidden: Boolean = false
        private set

    /** Distance travelled in the current direction, in pixels. */
    private var travelled: Int = 0

    /**
     * Feeds one scroll event.
     *
     * @param scrollY the view's current vertical scroll offset.
     * @param delta how far this event scrolled, positive downwards.
     * @return true only when [hidden] changed, so the caller can skip the state
     *   write — and the recomposition that follows it — for the events that do
     *   not cross a threshold. That is the overwhelming majority of them.
     */
    fun onScroll(scrollY: Int, delta: Int): Boolean {
        // Back at the top of the page the chrome is always shown.
        if (scrollY <= 0) {
            travelled = 0
            return setHidden(false)
        }
        if ((delta > 0 && travelled < 0) || (delta < 0 && travelled > 0)) travelled = 0
        travelled += delta
        return when {
            travelled > hideThresholdPx -> {
                travelled = 0
                setHidden(true)
            }
            travelled < -showThresholdPx -> {
                travelled = 0
                setHidden(false)
            }
            else -> false
        }
    }

    /** Forgets the current gesture — used when the page changes. */
    fun reset() {
        travelled = 0
        hidden = false
    }

    private fun setHidden(value: Boolean): Boolean {
        if (hidden == value) return false
        hidden = value
        return true
    }
}
