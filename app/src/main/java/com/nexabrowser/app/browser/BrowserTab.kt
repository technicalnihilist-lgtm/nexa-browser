package com.nexabrowser.app.browser

import android.graphics.Bitmap
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable, observable metadata for one tab. Never contains the WebView. */
data class TabInfo(
    val id: String,
    val title: String,
    val url: String,
    val favicon: Bitmap?,
    val isLoading: Boolean,
    val progress: Int,
    val isPrivate: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val error: PageError? = null
) {
    /** A blank url means "show the start page" rather than a loaded document. */
    val showStartPage: Boolean get() = url.isBlank() || url == "about:blank"
    val displayTitle: String get() = title.ifBlank { url.ifBlank { "New Tab" } }
}

/**
 * One browsing tab: an observable [TabInfo] plus the [WebView] that renders it.
 * The WebView is owned here and must be destroyed exactly once, by
 * [BrowserTabManager] when the tab is closed or the manager is shut down.
 *
 * The WebView is built on first use rather than in the constructor. Restoring a
 * ten-tab session used to construct ten WebViews — each one a native view, a
 * WebSettings object and a provider binding, tens of milliseconds on the main
 * thread — before the first frame could be drawn, for nine pages the user was
 * not even looking at. A restored background tab now costs one [TabInfo] until
 * the user selects it; [BrowserTabManager] creates the view at that moment, so
 * the tab is complete by the time anything can draw it.
 *
 * Access must stay on the main thread (the same thread that owns the tab list),
 * which is why the lazy uses [LazyThreadSafetyMode.NONE].
 */
class BrowserTab(
    val id: String,
    val isPrivate: Boolean,
    webViewProvider: () -> WebView
) {

    private val webViewLazy = lazy(LazyThreadSafetyMode.NONE) { webViewProvider() }

    /**
     * The WebView behind this tab, constructed on first access.
     *
     * Every caller that reaches this property wants to touch the live page, so
     * creating it here is the correct behaviour; code that only wants to know
     * whether the view exists yet must use [hasWebView] so it never forces one
     * into existence.
     */
    val webView: WebView get() = webViewLazy.value

    /** True once the WebView has actually been constructed. */
    val hasWebView: Boolean get() = webViewLazy.isInitialized()
    private val _state = MutableStateFlow(
        TabInfo(
            id = id,
            title = "",
            url = "",
            favicon = null,
            isLoading = false,
            progress = 0,
            isPrivate = isPrivate,
            canGoBack = false,
            canGoForward = false,
            error = null
        )
    )
    val state: StateFlow<TabInfo> = _state.asStateFlow()
    val info: TabInfo get() = _state.value

    internal fun update(transform: (TabInfo) -> TabInfo) {
        _state.value = transform(_state.value)
    }

    /**
     * Replaces the whole published state. Used when the WebView behind this tab
     * had to be rebuilt and the tab must keep its title, url and mode.
     */
    internal fun restore(info: TabInfo) {
        _state.value = info
    }

    internal fun destroy() {
        // A tab that was restored but never looked at has no WebView to tear
        // down: nothing was created, so nothing can leak.
        if (!webViewLazy.isInitialized()) return
        val webView = webViewLazy.value
        runCatching {
            webView.stopLoading()
            // A WebView destroyed while still attached to a view hierarchy is a
            // classic native crash on several OEM builds, so detach first. The
            // extra about:blank load is skipped: the instance is going away and
            // a load started here can deliver callbacks after destroy().
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
    }
}
