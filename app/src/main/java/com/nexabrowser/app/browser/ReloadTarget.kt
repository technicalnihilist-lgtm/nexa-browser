package com.nexabrowser.app.browser

/**
 * What a "reload" should actually do for one tab.
 *
 * A tab can be left sitting on a blank document while still knowing the url it
 * belongs to: that is exactly the state [BrowserTabManager.rebuildTab] leaves a
 * tab in when its renderer process died and the reload budget says it must not
 * be reloaded straight away. `WebView.reload()` on a blank document does
 * nothing at all, so the **Try Again** button on that tab's error overlay was a
 * no-op — the page could not be recovered without retyping the address, and the
 * overlay (which is the only thing on screen) stayed put for good.
 *
 * The rule is small enough to be worth pinning down in a test: reload the
 * document when there is one, otherwise load the url the tab is holding, and
 * otherwise do nothing in particular (a genuinely empty tab has nothing to
 * reload and must not be given an invented url).
 *
 * Kept free of Android types so it can be unit tested on the JVM.
 */
object ReloadTarget {

    /** True when [webViewUrl] names a document that `reload()` can act on. */
    fun hasDocument(webViewUrl: String?): Boolean =
        !webViewUrl.isNullOrBlank() && webViewUrl != "about:blank"

    /**
     * The url to load instead of reloading, or null when the WebView itself
     * should be reloaded (or when the tab has nothing to load at all).
     */
    fun urlFor(webViewUrl: String?, tabUrl: String): String? {
        if (hasDocument(webViewUrl)) return null
        if (tabUrl.isBlank() || tabUrl == "about:blank") return null
        return tabUrl
    }
}
