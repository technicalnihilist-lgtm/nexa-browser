package com.nexabrowser.app.browser

/**
 * Decides how much of the renderer budget a tab is worth, from the three
 * independent reasons a WebView may or may not be doing visible work:
 *
 *  * the tab is the active one,
 *  * the browser surface is on screen (the user may be in Settings, Bookmarks
 *    or the tab switcher instead),
 *  * the host Activity is started (the app may be in the background).
 *
 * A renderer is only pinned at `RENDERER_PRIORITY_IMPORTANT` — the priority the
 * platform is told never to waive — when all three hold. Kept free of Android
 * types so the rule is unit tested rather than inferred.
 */
internal object RendererPolicy {

    /** True when [tabId]'s renderer should be held at full priority. */
    fun important(tabId: String, activeTabId: String?, surfaceVisible: Boolean, hostVisible: Boolean): Boolean =
        surfaceVisible && hostVisible && tabId == activeTabId

    /**
     * True when a tab should be running rather than paused. A hidden surface
     * only lowers priority; pausing is reserved for the Activity going away, so
     * audio and video keep playing while the user visits an in-app screen.
     */
    fun running(tabId: String, activeTabId: String?, hostVisible: Boolean): Boolean =
        hostVisible && tabId == activeTabId
}
