package com.nexabrowser.app.browser

/**
 * The part of a [TabInfo] the browser chrome reads.
 *
 * A page load publishes a new [TabInfo] on every progress tick — twenty or so
 * per load — and the root composable collects the whole tab list, so every one
 * of those ticks recomposed the entire tree: the theme, the animated screen
 * container, the start page, the address bar and the toolbars. Only the 3dp
 * loading bar actually depends on [TabInfo.progress]; everything else in the
 * chrome depends on title, url, loading state, navigation flags and the error.
 *
 * [of] therefore returns a copy with the progress field neutralised, so two
 * snapshots taken across a progress tick are structurally equal and Compose
 * does not invalidate the readers of the snapshot. The real progress is read
 * separately, by the one composable that draws it, so a tick costs a single
 * tiny recomposition instead of a full-tree one.
 *
 * Nothing user-visible changes: the same fields reach the same composables.
 */
object TabChromeState {

    /**
     * The progress value used for the chrome snapshot. Any constant works as
     * long as it is not one the loading bar draws (1..99); 0 is the value a tab
     * carries when it is not loading.
     */
    const val IDLE_PROGRESS = 0

    /** Chrome snapshot of one tab, or null when there is no such tab. */
    fun of(info: TabInfo?): TabInfo? = when {
        info == null -> info
        info.progress == IDLE_PROGRESS -> info
        else -> info.copy(progress = IDLE_PROGRESS)
    }

    /** Chrome snapshot of a tab list; the order and the entries are preserved. */
    fun of(infos: List<TabInfo>): List<TabInfo> {
        if (infos.isEmpty()) return infos
        var changed = false
        for (info in infos) {
            if (info.progress != IDLE_PROGRESS) {
                changed = true
                break
            }
        }
        // The common case is a list that already carries no progress at all
        // (background tabs are published only when something else changed), so
        // it is returned as-is rather than copied.
        if (!changed) return infos
        val out = ArrayList<TabInfo>(infos.size)
        for (info in infos) {
            out.add(if (info.progress == IDLE_PROGRESS) info else info.copy(progress = IDLE_PROGRESS))
        }
        return out
    }
}
