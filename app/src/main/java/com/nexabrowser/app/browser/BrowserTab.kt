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
 */
class BrowserTab(
    val id: String,
    val isPrivate: Boolean,
    val webView: WebView
) {
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

    internal fun destroy() {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }
}
