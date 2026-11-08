package com.nexabrowser.app.browser

/** A saved tab waiting to be restored: the url it was showing, and its title. */
data class RestoreEntry(val url: String, val title: String)

/**
 * Pure ordering rule for session restore, kept free of Android types so it can
 * be unit tested on the JVM.
 *
 * Restoring a session used to load every saved tab at once. A ten-tab session
 * therefore started ten page loads — ten HTML parses, ten sets of subresource
 * fetches and ten scripts — in the same few hundred milliseconds in which the
 * tab the user is actually looking at was trying to fetch and paint its first
 * frame. Only one of those loads can ever be seen.
 *
 * The plan keeps every restorable tab, in strip order, and marks the single tab
 * that has to load straight away: the first one, which is the tab the manager
 * makes active. The rest are created empty and fetch their page when the user
 * first selects them, so a restored background tab costs nothing until it is
 * looked at.
 */
object TabRestorePlan {

    /**
     * [entries] are the tabs worth restoring, in strip order; [deferred] is how
     * many of them wait for their first selection. The first entry is the one
     * loaded at restore, so it is never counted as deferred.
     */
    data class Plan(val entries: List<RestoreEntry>, val deferred: Int)

    /**
     * Builds the plan from the saved session rows (`url`, `title`, `position`).
     * Unloadable urls are dropped exactly as before, and the rest keep the
     * position they were saved with.
     */
    fun plan(
        saved: List<Triple<String, String, Int>>,
        isLoadable: (String) -> Boolean
    ): Plan {
        val entries = saved
            .filter { isLoadable(it.first) }
            .sortedBy { it.third }
            .map { RestoreEntry(it.first, it.second) }
        val deferred = if (entries.isEmpty()) 0 else entries.size - 1
        return Plan(entries, deferred)
    }
}
