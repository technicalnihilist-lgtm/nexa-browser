package com.nexabrowser.app.browser

import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.ValueCallback

/**
 * A stable [BrowserActivityHost] that the browser engine can hold forever while
 * the real host (the Activity) comes and goes. The engine never keeps an
 * Activity reference, so it cannot leak one.
 */
class DelegatingActivityHost : BrowserActivityHost {

    @Volatile
    var delegate: BrowserActivityHost? = null

    override fun onShowFileChooser(
        tabId: String,
        fileChooserParams: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ): Boolean = delegate?.onShowFileChooser(tabId, fileChooserParams, callback) ?: false

    override fun onPermissionRequest(tabId: String, origin: String, resources: Array<String>, callback: PermissionRequest) {
        delegate?.onPermissionRequest(tabId, origin, resources, callback) ?: callback.deny()
    }

    override fun onJsDialog(tabId: String, type: JsDialogType, message: String, defaultValue: String?, result: android.webkit.JsResult) {
        delegate?.onJsDialog(tabId, type, message, defaultValue, result) ?: result.cancel()
    }

    override fun onJsPrompt(tabId: String, message: String, defaultValue: String?, result: android.webkit.JsPromptResult) {
        delegate?.onJsPrompt(tabId, message, defaultValue, result) ?: result.cancel()
    }

    override fun onGeolocationPermissionRequest(origin: String, callback: GeolocationPermissions.Callback) {
        delegate?.onGeolocationPermissionRequest(origin, callback) ?: callback.invoke(origin, false, false)
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        delegate?.onEnterFullscreen(view, callback) ?: callback.onCustomViewHidden()
    }

    override fun onExitFullscreen() {
        delegate?.onExitFullscreen()
    }

    override fun onOpenExternal(url: String): Boolean = delegate?.onOpenExternal(url) ?: false

    override fun userAgent(): String? = delegate?.userAgent()

    override fun onNewWindowRequested(tabId: String, url: String?) {
        delegate?.onNewWindowRequested(tabId, url)
    }

    override fun onDownloadRequested(tabId: String, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        delegate?.onDownloadRequested(tabId, url, userAgent, contentDisposition, mimeType)
    }
}
