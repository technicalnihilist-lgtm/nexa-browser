package com.nexabrowser.app.browser

import android.webkit.WebView

/**
 * Thin, synchronous facade over the navigation verbs of one WebView. Keeping
 * these calls in one place makes the UI code readable and gives a single spot to
 * reason about WebView state.
 */
class BrowserNavigationController(private val tabProvider: () -> BrowserTab?) {

    private fun webView(): WebView? = tabProvider()?.webView

    fun canGoBack(): Boolean = webView()?.canGoBack() ?: false
    fun canGoForward(): Boolean = webView()?.canGoForward() ?: false

    fun goBack(): Boolean {
        val wv = webView() ?: return false
        if (wv.canGoBack()) {
            wv.goBack()
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        val wv = webView() ?: return false
        if (wv.canGoForward()) {
            wv.goForward()
            return true
        }
        return false
    }

    fun reload() = webView()?.reload()
    fun stopLoading() = webView()?.stopLoading()

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
