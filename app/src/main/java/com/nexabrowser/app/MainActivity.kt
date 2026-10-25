package com.nexabrowser.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.nexabrowser.app.browser.BrowserActivityHost
import com.nexabrowser.app.browser.JsDialogType
import com.nexabrowser.app.ui.BrowserViewModel
import com.nexabrowser.app.ui.NexaApp

/**
 * The single Activity. It implements [BrowserActivityHost] so the engine can
 * reach the platform for the things a WebView cannot do alone — file pickers,
 * runtime permissions, dialogs and fullscreen — without the engine ever holding
 * an Activity reference itself.
 */
class MainActivity : ComponentActivity(), BrowserActivityHost {

    private val viewModel: BrowserViewModel by viewModels()

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: (() -> Unit)? = null
    private var pendingPermissionRemember = true

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        fileChooserCallback = null
        if (result.resultCode == Activity.RESULT_OK) {
            callback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        } else {
            callback.onReceiveValue(null)
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val action = pendingDownload
        pendingDownload = null
        action?.invoke()
    }

    private val osPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.isNotEmpty() && result.values.all { it }
        viewModel.resolvePermission(allow = true, remember = pendingPermissionRemember, osGranted = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hand the engine an Activity context for WebView creation, and install
        // this Activity as the platform host.
        viewModel.hostContext = this
        viewModel.activityHost.delegate = this

        intent?.let { incomingUrl(it)?.let { url -> viewModel.initialUrl = url } }

        setContent {
            NexaApp(
                viewModel = viewModel,
                onPermissionDecision = ::handlePermissionDecision
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl(intent)?.let { url -> viewModel.openInNewTab(url) }
    }

    override fun onStop() {
        viewModel.saveSession()
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.activityHost.delegate = null
        viewModel.hostContext = null
        super.onDestroy()
    }

    private fun incomingUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data?.toString() ?: return null
        return data.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    // ------------------------------------------------- BrowserActivityHost

    override fun onShowFileChooser(
        tabId: String,
        fileChooserParams: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            fileChooserLauncher.launch(fileChooserParams.createIntent())
            true
        } catch (e: Exception) {
            fileChooserCallback = null
            Toast.makeText(this, "No file picker is available", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onPermissionRequest(tabId: String, origin: String, resources: Array<String>, callback: PermissionRequest) {
        viewModel.onWebPermissionRequest(tabId, origin, resources, callback)
    }

    override fun onJsDialog(tabId: String, type: JsDialogType, message: String, defaultValue: String?, result: JsResult) {
        viewModel.onJsDialog(type, message, defaultValue, result)
    }

    override fun onJsPrompt(tabId: String, message: String, defaultValue: String?, result: JsPromptResult) {
        viewModel.onJsPrompt(message, defaultValue, result)
    }

    override fun onGeolocationPermissionRequest(origin: String, callback: GeolocationPermissions.Callback) {
        viewModel.onGeolocationPermissionRequest(origin, callback)
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        viewModel.enterFullscreen(view, callback)
    }

    override fun onExitFullscreen() {
        viewModel.exitFullscreen()
    }

    override fun onNewWindowRequested(tabId: String, url: String?) {
        viewModel.openInNewTab(url.orEmpty())
    }

    override fun onDownloadRequested(tabId: String, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        val start = { viewModel.startDownload(url, userAgent, contentDisposition, mimeType) }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = start
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            maybeRequestNotificationPermission()
            start()
        }
    }

    override fun onOpenExternal(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        // Only a small allow-list of schemes is ever handed to the platform.
        val allowed = setOf("mailto", "tel", "sms", "smsto", "geo", "market", "intent", "whatsapp", "upi")
        if (scheme !in allowed) return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(this, "No app can handle this link", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun userAgent(): String? = null

    /** Called by the permission dialog when the user taps Allow. */
    private fun handlePermissionDecision(allow: Boolean, remember: Boolean) {
        if (!allow) {
            viewModel.resolvePermission(allow = false, remember = remember)
            return
        }
        val needed = viewModel.requiredOsPermissions()
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.resolvePermission(allow = true, remember = remember)
        } else {
            pendingPermissionRemember = remember
            osPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { osPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        }
    }
}
