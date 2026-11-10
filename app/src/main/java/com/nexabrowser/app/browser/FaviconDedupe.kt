package com.nexabrowser.app.browser

/**
 * Decides whether the icon a WebView just handed back is one the tab has
 * already processed.
 *
 * A page reports its icon more than once per load: `onPageStarted` passes the
 * document's current favicon, `onReceivedIcon` follows with the same bitmap
 * once the icon has been decoded, and a site that swaps its icon on a timer
 * (unread counters, live badges) re-fires the callback with the very same
 * instance on every tick. Each of those used to be taken at face value:
 *
 *  * an icon larger than [com.nexabrowser.app.util.FaviconSizing.MAX_ICON_EDGE]
 *    was rescaled again — a fresh bitmap allocated and the old one left to the
 *    collector — for a picture the tab strip was already drawing, and
 *  * every accepted icon published a new `TabInfo`, which rebuilt the published
 *    tab list and recomposed the tab strip and the tab overview, on the main
 *    thread, while the page was still painting.
 *
 * The key is the *identity* of the bitmap instance rather than its pixels: two
 * reports of the same `Bitmap` object are the same icon by construction, while a
 * genuinely new icon is a new object (or the same object recycled and filled
 * again, which carries the size as well). Comparing pixels would mean reading
 * the bitmap — the work this exists to avoid.
 *
 * Kept free of Android types so the rule is unit tested on the JVM rather than
 * inferred from a device log.
 */
object FaviconDedupe {

    /**
     * A packed key for one reported icon: the bitmap's identity hash, its width
     * and its height.
     *
     * Width and height are packed as 16-bit fields. An icon wider or taller than
     * 65535px cannot be produced by a WebView, and truncating one could only ever
     * turn two different icons into a false match, never the reverse — which is
     * why the dimensions are folded in at all: they make a recycled-then-refilled
     * bitmap (same object, new picture) distinguishable from the report before it.
     */
    fun signature(identityHashCode: Int, width: Int, height: Int): Long {
        val w = (width.coerceIn(0, 0xFFFF)).toLong()
        val h = (height.coerceIn(0, 0xFFFF)).toLong()
        return (identityHashCode.toLong() shl 32) or (w shl 16) or h
    }

    /**
     * True when [signature] is the icon that was already accepted for this tab,
     * i.e. there is nothing new to scale or publish.
     */
    fun isRepeat(previous: Long?, signature: Long): Boolean = previous == signature
}
