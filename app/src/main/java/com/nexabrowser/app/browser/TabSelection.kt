package com.nexabrowser.app.browser

/**
 * A tab's identity and mode, with no Android types attached, so the selection
 * rules below can be unit tested on the JVM.
 */
data class TabRef(val id: String, val isPrivate: Boolean)

/**
 * Decides which tab is active after a tab closes, and in which browsing mode.
 *
 * Keeping this out of [BrowserTabManager] is deliberate: the rule has two
 * answers (a tab *and* a mode) that must agree, and getting only the first one
 * right is exactly the bug this replaces — closing the last private tab used to
 * leave the app in private mode while showing a regular tab.
 */
object TabSelection {

    /**
     * The tab that should become active once a tab of mode [closedIsPrivate] has
     * been removed, given the tabs that remain in their open order.
     *
     * The last remaining tab of the same mode wins, so closing a tab keeps the
     * user inside the mode they were browsing in; otherwise the last tab of the
     * other mode takes over. Returns null when nothing remains, which tells the
     * caller to open a fresh tab.
     */
    fun activeAfterClose(remaining: List<TabRef>, closedIsPrivate: Boolean): TabRef? {
        if (remaining.isEmpty()) return null
        return remaining.lastOrNull { it.isPrivate == closedIsPrivate } ?: remaining.last()
    }
}
