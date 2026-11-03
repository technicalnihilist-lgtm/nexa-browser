package com.nexabrowser.app.browser

/**
 * Decides whether a tab whose renderer process died should be reloaded
 * automatically, or left showing the error overlay instead.
 *
 * Android kills a WebView's renderer whenever it needs the memory back — which
 * on a phone with a few heavy pages open is routine — and the app is told about
 * it through [android.webkit.WebViewClient.onRenderProcessGone]. Reloading the
 * page once is almost always the right answer: the user keeps their tab and the
 * page comes back. Reloading it forever is not, because a page that kills its
 * own renderer (a script with runaway memory) would turn every recovery into
 * the next crash. A small budget inside a time window separates the two.
 *
 * Kept free of Android types so the rule is unit tested rather than assumed.
 */
internal object RendererRecovery {

    /** Automatic reloads allowed for one tab before it is left on the error overlay. */
    const val MAX_AUTOMATIC_RELOADS = 2

    /** The window over which that budget is counted. */
    const val RETRY_WINDOW_MS = 60_000L

    /** How many times one tab has lost its renderer, and when the run started. */
    data class LossRecord(val firstLossAt: Long, val count: Int)

    /**
     * Folds one renderer loss into the previous record.
     *
     * A loss inside [RETRY_WINDOW_MS] of the first one continues the run; a loss
     * after it starts a fresh budget, so a tab that crashes once an hour is
     * always recovered and never exhausted.
     */
    fun record(previous: LossRecord?, now: Long): LossRecord {
        if (previous == null) return LossRecord(now, 1)
        val withinWindow = now - previous.firstLossAt in 0..RETRY_WINDOW_MS
        return if (withinWindow) LossRecord(previous.firstLossAt, previous.count + 1)
        else LossRecord(now, 1)
    }

    /** True while the tab still has automatic reloads left in this run. */
    fun shouldReload(record: LossRecord): Boolean = record.count <= MAX_AUTOMATIC_RELOADS
}
