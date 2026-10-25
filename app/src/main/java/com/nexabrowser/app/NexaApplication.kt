package com.nexabrowser.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Application entry point. Keeps global WebView setup in one place and performs
 * a cheap warm-up so the first page load is faster.
 */
class NexaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Warm up the WebView provider off the critical path of the first tab.
        runCatching {
            WebView(this).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                loadUrl("about:blank")
                destroy()
            }
        }
    }
}
