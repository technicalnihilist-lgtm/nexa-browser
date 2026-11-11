package com.nexabrowser.app.browser

import android.webkit.WebView

/**
 * Thin, synchronous facade over the navigation verbs of one WebView. Keeping
 * these calls in one place makes the UI code readable and gives a single spot to
 * reason about WebView state.
 *
 * A tab's WebView is built on first use (see [BrowserTab]), and building one is
 * not free: it initialises the Chromium provider and starts a renderer process,
 * on the main thread. Every method here that only *asks* a question — can we go
 * back, is anything loading — therefore goes through [liveWebView], which never
 * creates one. A tab that has no WebView has no history and nothing in flight,
 * so the answers are false either way; the difference is that answering them no
 * longer costs a renderer. The verbs that actually drive a page keep using
 * [webView], because they are meaningless without one.
 */
class BrowserNavigationController(private val tabProvider: () -> BrowserTab?) {

    /** The WebView of this tab, constructed if it does not exist yet. */
    private fun webView(): WebView? = tabProvider()?.webView

    /**
     * The WebView of this tab only when it has already been built.
     *
     * Never constructs one: a question about a tab that has never been shown is
     * answered without waking a renderer.
     */
    private fun liveWebView(): WebView? {
        val tab = tabProvider() ?: return null
        return if (tab.hasWebView) tab.webView else null
    }

    fun canGoBack(): Boolean = liveWebView()?.canGoBack() ?: false

    fun canGoForward(): Boolean = liveWebView()?.canGoForward() ?: false

    fun goBack(): Boolean {
        val wv = liveWebView() ?: return false
        if (wv.canGoBack()) {
            wv.goBack()
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        val wv = liveWebView() ?: return false
        if (wv.canGoForward()) {
            wv.goForward()
            return true
        }
        return false
    }

    /**
     * Reloads the page in the active tab.
     *
     * A tab can be parked on a blank document while still holding the url it
     * belongs to — see [ReloadTarget] — and `WebView.reload()` on a blank
     * document does nothing at all, which made the error overlay's **Try Again**
     * button a no-op on exactly the tab that needed it. The tab's own url is
     * loaded in that case instead.
     */
    fun reload() {
        val tab = tabProvider() ?: return
        val wv = tab.webView
        val target = ReloadTarget.urlFor(wv.url, tab.info.url)
        if (target != null) {
            runCatching { wv.loadUrl(target) }.onFailure { wv.loadUrl("about:blank") }
            return
        }
        wv.reload()
    }

    fun stopLoading() {
        liveWebView()?.stopLoading()
    }

    fun load(url: String) {
        val wv = webView() ?: return
        if (url.isBlank()) wv.loadUrl("about:blank") else wv.loadUrl(url)
    }

    /** Restores a session entry, guarding against stale or malformed URLs. */
    fun restore(url: String) {
        val wv = webView() ?: return
        if (url.isBlank()) {
            wv.loadUrl("about:blank")
            return
        }
        runCatching { wv.loadUrl(url) }.onFailure { wv.loadUrl("about:blank") }
    }
}
