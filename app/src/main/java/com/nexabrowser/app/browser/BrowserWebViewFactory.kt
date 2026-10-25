package com.nexabrowser.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.util.UrlUtils

/** Events the WebView clients push back to the tab manager. */
interface TabEventSink {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String)
    fun onProgress(tabId: String, progress: Int)
    fun onTitle(tabId: String, title: String)
    fun onFavicon(tabId: String, favicon: Bitmap?)
    fun onUrlChanged(tabId: String, url: String)
    fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onError(tabId: String, error: PageError)
}

/**
 * Creates and configures WebViews. Security-relevant choices are deliberate:
 * file access from file URLs and universal access from file URLs are off, mixed
 * content is never allowed, and no JavaScript bridge is ever installed.
 */
object BrowserWebViewFactory {

    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    /** Runs the JS shim that exposes a Do Not Track signal to page scripts. */
    private const val DNT_SHIM =
        "(function(){try{Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1'},configurable:true});" +
            "Object.defineProperty(navigator,'msDoNotTrack',{get:function(){return '1'},configurable:true});}catch(e){}})();"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        tabId: String,
        settings: BrowserSettings,
        sink: TabEventSink,
        host: BrowserActivityHost
    ): WebView {
        val webView = WebView(context)
        webView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.isScrollbarFadingEnabled = true

        applySettings(webView, settings)

        webView.webViewClient = NexaWebViewClient(tabId, sink, host)
        webView.webChromeClient = NexaChromeClient(tabId, sink, host)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.onDownloadRequested(tabId, url, userAgent, contentDisposition, mimeType)
        }
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(webView: WebView, settings: BrowserSettings) {
        val s = webView.settings
        s.javaScriptEnabled = settings.javaScriptEnabled
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadsImagesAutomatically = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.javaScriptCanOpenWindowsAutomatically = settings.popupsEnabled
        s.setSupportMultipleWindows(true)
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = settings.textZoom.coerceIn(50, 200)
        s.allowFileAccess = true                 // needed for saved offline reading-list pages
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = false    // a file:// page cannot read other files
        s.allowUniversalAccessFromFileURLs = false
        s.setGeolocationEnabled(false)           // gated behind an explicit prompt
        s.safeBrowsingEnabled = true
        s.userAgentString = if (settings.desktopSiteByDefault) DESKTOP_UA else null

        runCatching {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, settings.cookiesEnabled)
        }
    }

    fun injectDoNotTrack(webView: WebView, enabled: Boolean) {
        if (enabled) webView.evaluateJavascript(DNT_SHIM, null)
    }
}

/** Handles navigation policy, errors and certificate failures. */
private class NexaWebViewClient(
    private val tabId: String,
    private val sink: TabEventSink,
    private val host: BrowserActivityHost
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: ""
        val current = view.url ?: ""

        // A remote page must never be able to pull the WebView onto local files.
        if (scheme == "file" && !current.startsWith("file:")) {
            Log.w("NexaWebView", "Blocked remote navigation to a file URL")
            return true
        }
        // Never allow a page to run javascript: URLs via a link.
        if (scheme == "javascript") return true

        return when (scheme) {
            "http", "https", "about", "data", "blob", "file", "content" -> false
            else -> {
                if (UrlUtils.isSafeExternalScheme(scheme)) {
                    host.onOpenExternal(url)
                } else {
                    Log.w("NexaWebView", "Refused to open unsupported scheme: $scheme")
                }
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        sink.onPageStarted(tabId, url)
        sink.onFavicon(tabId, favicon)
        sink.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String) {
        sink.onPageFinished(tabId, url)
        sink.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        sink.onUrlChanged(tabId, url)
        sink.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.errorCode else PageError.UNKNOWN
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.description?.toString().orEmpty() else ""
        sink.onError(
            tabId,
            PageError(code, description.ifBlank { "Could not load the page" }, request.url.toString(), PageError.isNetwork(code))
        )
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame) return
        val status = errorResponse.statusCode
        if (status >= 400) {
            sink.onError(
                tabId,
                PageError(status, "The server returned HTTP $status", request.url.toString(), false)
            )
        }
    }

    /**
     * Certificate errors are always cancelled. The app never calls
     * handler.proceed(), so a MITM certificate can never be accepted.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        Log.w("NexaWebView", "SSL error for ${error.url}: refusing to proceed")
        handler.cancel()
        sink.onError(
            tabId,
            PageError(
                PageError.FAILED_SSL_HANDSHAKE,
                "The secure connection could not be verified, so loading was stopped.",
                error.url ?: "",
                true
            )
        )
    }

    override fun onReceivedClientCertRequest(view: WebView, request: android.webkit.ClientCertRequest) {
        request.cancel()
    }
}

/** Handles chrome: progress, title, favicon, dialogs, permissions, fullscreen, popups. */
private class NexaChromeClient(
    private val tabId: String,
    private val sink: TabEventSink,
    private val host: BrowserActivityHost
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        sink.onProgress(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        sink.onTitle(tabId, title.orEmpty())
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        sink.onFavicon(tabId, icon)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return host.onShowFileChooser(tabId, fileChooserParams, filePathCallback)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val origin = request.origin?.toString().orEmpty()
        host.onPermissionRequest(tabId, origin, request.resources ?: emptyArray(), request)
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        if (origin == null) {
            callback.invoke("", false, false)
            return
        }
        host.onGeolocationPermissionRequest(origin, callback)
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        host.onJsDialog(tabId, JsDialogType.ALERT, message, null, result)
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        host.onJsDialog(tabId, JsDialogType.CONFIRM, message, null, result)
        return true
    }

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
        host.onJsPrompt(tabId, message, defaultValue, result)
        return true
    }

    override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
        host.onJsDialog(tabId, JsDialogType.BEFORE_UNLOAD, message, null, result)
        return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        host.onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        host.onExitFullscreen()
    }

    /**
     * target="_blank" / window.open. We spin up a throwaway WebView purely to
     * learn the destination URL, then hand it to the tab manager, which opens a
     * real tab. The throwaway is destroyed immediately so it cannot leak.
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val probe = WebView(view.context)
        probe.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                v.destroy()
                host.onNewWindowRequested(tabId, target)
                return true
            }
        }
        transport.webView = probe
        resultMsg.sendToTarget()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        Log.d("NexaConsole", "${consoleMessage.message()} @${consoleMessage.lineNumber()}")
        return true
    }
}
