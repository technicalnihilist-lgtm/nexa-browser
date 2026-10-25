package com.nexabrowser.app.browser

import android.net.Uri
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback

/** The kind of JavaScript dialog a page is asking for. */
enum class JsDialogType { ALERT, CONFIRM, PROMPT, BEFORE_UNLOAD }

/**
 * Activity-dependent callbacks that a WebView cannot service on its own (they
 * need a real Activity: file pickers, runtime permissions, dialogs, fullscreen).
 * The browser layer holds only this interface, never an Activity reference, so
 * nothing in the engine can leak a destroyed Activity.
 */
interface BrowserActivityHost {

    /** Returns true when the chooser was launched. */
    fun onShowFileChooser(
        tabId: String,
        fileChooserParams: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ): Boolean = false

    fun onPermissionRequest(
        tabId: String,
        origin: String,
        resources: Array<String>,
        callback: PermissionRequest
    ) {
        // Deny by default: a page must not gain camera/mic/location silently.
        callback.deny()
    }

    fun onJsDialog(
        tabId: String,
        type: JsDialogType,
        message: String,
        defaultValue: String?,
        result: JsResult
    ) {
        result.cancel()
    }

    /** Prompt dialogs carry a JsPromptResult, which has its own confirm(String). */
    fun onJsPrompt(tabId: String, message: String, defaultValue: String?, result: JsPromptResult) {
        result.cancel()
    }

    /** Geolocation uses its own callback type, so it needs a dedicated hook. */
    fun onGeolocationPermissionRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        callback.onCustomViewHidden()
    }

    fun onExitFullscreen() {}

    /** True when the URL was handed to another app. */
    fun onOpenExternal(url: String): Boolean = false

    /** User agent reported to sites; the manager applies it to each WebView. */
    fun userAgent(): String? = null

    /** Called when a page wants to open a new window (target=_blank / window.open). */
    fun onNewWindowRequested(tabId: String, url: String?) {}

    /** Called when a WebView requests a URL to be handled by the download flow. */
    fun onDownloadRequested(tabId: String, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {}
}
