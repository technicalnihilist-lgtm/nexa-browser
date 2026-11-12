package com.nexabrowser.app.data.repository

/**
 * How a visit that lands on top of another visit to the same page is merged into
 * the row already stored for it.
 *
 * A browser records a visit on every completed page load, and a reload, a
 * back-navigation and a site's own redirect all complete a load. Writing a new
 * row for each of those filled the history with a page listed several times in
 * the same minute. A visit therefore only becomes a new row when the stored row
 * for that url is older than [WINDOW_MS]; anything newer is folded into it by
 * moving its timestamp forward.
 *
 * Folding is not just a timestamp update, and that is what this file fixes. The
 * row keeps the *title* and *icon* of the visit that is being folded in, because
 * the first load of a page is exactly the one that has neither yet: WebView
 * reports the finished page before the site's icon has been delivered, so the
 * row that is inserted for a first visit carries `faviconUrl = null`. The icon
 * is written to disk a moment later, and the next load of that page — almost
 * always within the window — is folded into the very row that is missing it.
 * The icon path was computed on that path and then thrown away, so history,
 * bookmarks-from-history and the start page drew a letter avatar for a site
 * whose icon had been on disk the whole time. The row only ever gained the icon
 * if the user came back more than a minute later, which starts a new row and
 * leaves the old one blank for good.
 *
 * Kept free of Android and Room types so the rule is unit tested.
 */
object HistoryDedupe {

    /**
     * How long after a stored visit the same url is folded into that row instead
     * of starting a new one.
     */
    const val WINDOW_MS = 30_000L

    /**
     * True when a visit to a page whose last stored visit was [latestVisitedAt]
     * should be folded into that row rather than inserted as a new one.
     *
     * A clock that has moved backwards (a time-zone change, an NTP correction)
     * yields a negative difference, which is still inside the window: the visit
     * is folded in rather than inserted with a timestamp in the past, which
     * would place it out of order in a list sorted by `visitedAt`.
     */
    fun isRepeat(latestVisitedAt: Long, now: Long, window: Long = WINDOW_MS): Boolean =
        now - latestVisitedAt < window

    /**
     * The title the merged row should carry.
     *
     * A load that finished before the page had a `<title>` reports an empty
     * string, and that must not erase the title the first visit stored.
     */
    fun titleFor(existing: String, incoming: String): String =
        incoming.ifBlank { existing }

    /**
     * The icon the merged row should carry.
     *
     * A load that finished before the site delivered its icon has none, and that
     * must not erase an icon the row already points at; a load that does have
     * one supersedes whatever was there, so a site that changed its icon keeps
     * showing the new one.
     */
    fun iconFor(existing: String?, incoming: String?): String? = incoming ?: existing
}
