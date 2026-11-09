package com.nexabrowser.app.browser

/**
 * Whether the session that is about to be written is the one already stored.
 *
 * The open tabs are saved from `onStop()` — i.e. every time the app loses the
 * foreground, including a pull of the notification shade or a trip through the
 * app switcher — and again from `onCleared()`, so a normal background/foreground
 * cycle wrote the same rows twice. Each write is a `DELETE` plus an `INSERT` of
 * every row inside one transaction, and a transaction ends in an fsync: a
 * session that had not changed since the last write still cost two of them, plus
 * the wake-up of the disk, for a table whose contents were already correct.
 *
 * The decision is kept here, free of Android types and of the database, so the
 * rule is unit tested rather than inferred from a log. A `null` [previous] means
 * nothing has been written by this process yet, and that write always happens:
 * an empty session has to be written too, or the tabs stored by an earlier run
 * would stay in the table and be restored as if they were still open.
 */
object SessionWrite {

    /**
     * True when writing [next] cannot change what is stored, because the same
     * session (same tabs, same order, same titles and positions) was written
     * already. Order matters: it is the tab strip order that gets restored.
     */
    fun isRedundant(
        previous: List<Triple<String, String, Int>>?,
        next: List<Triple<String, String, Int>>
    ): Boolean {
        if (previous == null) return false
        if (previous.size != next.size) return false
        for (i in previous.indices) {
            val a = previous[i]
            val b = next[i]
            if (a.first != b.first || a.second != b.second || a.third != b.third) return false
        }
        return true
    }
}
