package com.nexabrowser.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexabrowser.app.browser.BrowserNavigationController
import com.nexabrowser.app.browser.BrowserTabManager
import com.nexabrowser.app.browser.DelegatingActivityHost
import com.nexabrowser.app.browser.JsDialogType
import com.nexabrowser.app.browser.PrivateBrowsingManager
import com.nexabrowser.app.data.local.NexaDatabase
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.data.prefs.DataStoreManager
import com.nexabrowser.app.data.repository.BrowserRepository
import com.nexabrowser.app.data.repository.DownloadRepository
import com.nexabrowser.app.data.repository.DownloadRequest
import com.nexabrowser.app.domain.model.Bookmark
import com.nexabrowser.app.domain.model.DownloadItem
import com.nexabrowser.app.domain.model.HistoryEntry
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.domain.model.PermissionDecision
import com.nexabrowser.app.domain.model.ReadingListItem
import com.nexabrowser.app.domain.model.SearchEngine
import com.nexabrowser.app.domain.model.Shortcut
import com.nexabrowser.app.domain.model.SitePermission
import com.nexabrowser.app.domain.model.SitePermissionRecord
import com.nexabrowser.app.domain.model.ThemeMode
import com.nexabrowser.app.domain.model.TopSite
import com.nexabrowser.app.util.FaviconStore
import com.nexabrowser.app.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level destinations that are drawn over the browser. */
enum class NexaScreen { BROWSER, BOOKMARKS, HISTORY, DOWNLOADS, READING_LIST, SETTINGS, TABS }

data class JsDialogState(
    val type: JsDialogType,
    val message: String,
    val defaultValue: String?
)

data class PermissionPromptState(
    val origin: String,
    val host: String,
    val permissions: List<SitePermission>,
    val geolocation: Boolean
)

data class BrowserUiState(
    val screen: NexaScreen = NexaScreen.BROWSER,
    val menuOpen: Boolean = false,
    val findActive: Boolean = false,
    val findQuery: String = "",
    val findCurrent: Int = 0,
    val findTotal: Int = 0,
    val jsDialog: JsDialogState? = null,
    val permissionPrompt: PermissionPromptState? = null,
    val fullscreenView: View? = null,
    val message: String? = null,
    val desktopSiteActive: Boolean = false
)

/**
 * Holds all UI state and orchestrates the browser engine, the persistence layer
 * and the settings store. It owns the tab manager and clears it in onCleared(),
 * so no WebView outlives the screen that uses it.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NexaDatabase.get(application)
    private val repository = BrowserRepository(db)
    private val downloadRepository = DownloadRepository(application, db.downloadDao())
    private val dataStore = DataStoreManager(application)
    private val faviconStore = FaviconStore(application)
    private val privateManager = PrivateBrowsingManager(application)

    /** The engine talks to this; MainActivity installs itself as the delegate. */
    val activityHost = DelegatingActivityHost()

    /** Set by MainActivity so new WebViews get an Activity context when possible. */
    @Volatile
    var hostContext: Context? = null

    val settings: StateFlow<BrowserSettings> = dataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    val tabManager: BrowserTabManager = BrowserTabManager(
        appContext = application,
        repository = repository,
        settingsProvider = { settings.value },
        webViewContext = { hostContext ?: application },
        host = activityHost,
        privateManager = privateManager
    )

    val navigation = BrowserNavigationController { tabManager.activeTab() }

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> =
        repository.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarkFolders = repository.bookmarkFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<HistoryEntry>> =
        repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val readingList: StateFlow<List<ReadingListItem>> =
        repository.readingList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shortcuts: StateFlow<List<Shortcut>> =
        repository.shortcuts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloads: StateFlow<List<DownloadItem>> =
        downloadRepository.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topSites: StateFlow<List<TopSite>> =
        repository.topSites(12).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sitePermissions: StateFlow<List<SitePermissionRecord>> =
        repository.sitePermissions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var pendingJsResult: android.webkit.JsResult? = null
    private var pendingJsPromptResult: JsPromptResult? = null
    private var pendingJsType: JsDialogType? = null
    private var pendingFullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** A URL the app was launched with (ACTION_VIEW), consumed during restore. */
    @Volatile
    var initialUrl: String? = null

    init {
        downloadRepository.startTracking(viewModelScope)

        // Apply settings to live WebViews whenever they change.
        viewModelScope.launch {
            settings.collect { tabManager.applySettings(it) }
        }

        // Record history for non-private tabs, with a cached favicon.
        viewModelScope.launch {
            tabManager.pageFinished.collect { info ->
                if (info.isPrivate) return@collect
                if (!settings.value.saveHistoryEnabled) return@collect
                val host = UrlUtils.hostOf(info.url)
                val iconPath = info.favicon?.let { faviconStore.save(host, it) }
                    ?: faviconStore.pathForHost(host)
                repository.recordVisit(info.title, info.url, iconPath)
            }
        }

        // Keep the bookmark toggle in sync with the active tab.
        viewModelScope.launch {
            tabManager.tabInfos.collect { infos ->
                val active = infos.firstOrNull { it.id == tabManager.activeTabId.value }
                _isBookmarked.value = active?.url?.let { repository.isBookmarked(it) } ?: false
            }
        }

        restoreSession()
    }

    // ------------------------------------------------------------- session

    private fun restoreSession() {
        viewModelScope.launch {
            val prefs = dataStore.current()
            val saved = if (prefs.sessionRestoreEnabled) repository.loadSessionTabs() else emptyList()
            val launchUrl = initialUrl
            if (saved.isEmpty()) {
                tabManager.newTab(url = launchUrl ?: "", isPrivate = false)
            } else {
                saved.sortedBy { it.third }.forEach { (url, _, _) ->
                    if (UrlUtils.isLoadableUrl(url)) tabManager.newTab(url = url, isPrivate = false, switchTo = false)
                }
                if (tabManager.tabInfos.value.isEmpty()) tabManager.newTab(url = "", isPrivate = false)
                tabManager.tabInfos.value.firstOrNull()?.let { tabManager.selectTab(it.id) }
                if (!launchUrl.isNullOrBlank()) tabManager.newTab(url = launchUrl, isPrivate = false)
            }
        }
    }

    fun saveSession() {
        viewModelScope.launch { runCatching { tabManager.saveSession() } }
    }

    // ------------------------------------------------------------- UI state

    fun setScreen(screen: NexaScreen) = _uiState.update { it.copy(screen = screen, menuOpen = false) }
    fun openMenu() = _uiState.update { it.copy(menuOpen = true) }
    fun closeMenu() = _uiState.update { it.copy(menuOpen = false) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    private fun message(text: String) = _uiState.update { it.copy(message = text) }

    // ------------------------------------------------------------ navigation

    fun submitAddress(input: String) {
        val resolved = UrlUtils.resolveInput(input, settings.value.activeQueryTemplate)
        if (resolved.isBlank()) return
        if (!resolved.startsWith("http") && !resolved.startsWith("file") && !resolved.startsWith("about")) {
            if (UrlUtils.isSafeExternalScheme(UrlUtils.schemeOf(resolved))) {
                activityHost.onOpenExternal(resolved)
                return
            }
        }
        tabManager.activeTab()?.webView?.loadUrl(resolved)
    }

    fun goBack(): Boolean = navigation.goBack()
    fun goForward(): Boolean = navigation.goForward()
    fun reload() = navigation.reload()
    fun stopLoading() = navigation.stopLoading()

    fun goHome() {
        val tab = tabManager.activeTab() ?: return
        val home = settings.value.homepageUrl
        if (home.isNotBlank()) tab.webView.loadUrl(UrlUtils.resolveInput(home, settings.value.activeQueryTemplate))
        else tab.webView.loadUrl("about:blank")
    }

    // ------------------------------------------------------------------ tabs

    fun newTab() = tabManager.newTab()
    fun newPrivateTab() = tabManager.newTab(url = "", isPrivate = true)
    fun selectTab(id: String) = tabManager.selectTab(id)
    fun closeTab(id: String) = tabManager.closeTab(id)
    fun duplicateTab(id: String) = tabManager.duplicateTab(id)
    fun closeAllTabs(privateOnly: Boolean = false) = tabManager.closeAllTabs(privateOnly)
    fun reopenLastClosed() {
        if (!tabManager.reopenLastClosed()) message("No recently closed tabs")
    }
    fun setPrivateMode(enabled: Boolean) = tabManager.setPrivateMode(enabled)

    /** Opens a URL in a fresh tab (target=_blank, window.open, menu actions). */
    fun openInNewTab(url: String) {
        if (url.isBlank()) tabManager.newTab() else tabManager.newTab(url = url)
    }

    // ------------------------------------------------------------- bookmarks

    fun toggleBookmark() {
        val info = tabManager.activeInfo() ?: return
        if (info.url.isBlank() || info.url == "about:blank") return
        viewModelScope.launch {
            val added = repository.toggleBookmark(info.displayTitle, info.url, null)
            _isBookmarked.value = added
            message(if (added) "Bookmark added" else "Bookmark removed")
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch { repository.addBookmark(title, url, null); message("Bookmark added") }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun updateBookmark(bookmark: Bookmark, title: String, url: String) {
        viewModelScope.launch {
            repository.updateBookmark(bookmark.copy(title = title, url = url))
            message("Bookmark updated")
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createFolder(name.trim()); message("Folder created") }
    }

    // ------------------------------------------------------------ history

    fun deleteHistoryEntry(entry: HistoryEntry) {
        viewModelScope.launch { repository.deleteHistory(entry.id) }
    }

    fun clearHistory(range: HistoryRange) {
        viewModelScope.launch {
            when (range) {
                HistoryRange.ALL -> repository.clearHistory()
                HistoryRange.LAST_HOUR -> repository.clearHistoryRange(System.currentTimeMillis() - HOUR, Long.MAX_VALUE)
                HistoryRange.TODAY -> repository.clearHistoryRange(startOfToday(), Long.MAX_VALUE)
                HistoryRange.LAST_WEEK -> repository.clearHistoryRange(System.currentTimeMillis() - WEEK, Long.MAX_VALUE)
            }
            message("History cleared")
        }
    }

    // ------------------------------------------------------ reading list

    fun addCurrentToReadingList() {
        val info = tabManager.activeInfo() ?: return
        if (info.url.isBlank() || info.url == "about:blank") return
        viewModelScope.launch {
            val id = repository.addToReadingList(info.displayTitle, info.url, null)
            message("Added to Reading List")
            saveOfflineCopy(id)
        }
    }

    /** Saves a WebView web archive so the page is genuinely available offline. */
    private fun saveOfflineCopy(readingListId: Long) {
        val webView = tabManager.activeTab()?.webView ?: return
        val dir = java.io.File(getApplication<Application>().filesDir, "reading").apply { mkdirs() }
        val file = java.io.File(dir, "page-$readingListId.mht")
        webView.saveWebArchive(file.absolutePath, false) { path ->
            viewModelScope.launch {
                if (path != null && java.io.File(path).exists()) {
                    repository.setOfflineCopy(readingListId, path, true)
                } else {
                    repository.setOfflineCopy(readingListId, null, false)
                }
            }
        }
    }

    fun removeFromReadingList(item: ReadingListItem) {
        viewModelScope.launch {
            item.archivePath?.let { runCatching { java.io.File(it).delete() } }
            repository.removeFromReadingList(item.id)
        }
    }

    // ----------------------------------------------------------- shortcuts

    fun addShortcut(title: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            repository.addShortcut(title, UrlUtils.resolveInput(url, settings.value.activeQueryTemplate))
            message("Shortcut added")
        }
    }

    fun deleteShortcut(shortcut: Shortcut) {
        viewModelScope.launch { repository.deleteShortcut(shortcut.id) }
    }

    fun reorderShortcuts(ordered: List<Shortcut>) {
        viewModelScope.launch { repository.reorderShortcuts(ordered) }
    }

    // ----------------------------------------------------------- downloads

    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        viewModelScope.launch {
            runCatching {
                val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                val prefs = settings.value
                downloadRepository.enqueue(
                    DownloadRequest(
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimeType,
                        cookie = cookie,
                        wifiOnly = prefs.downloadOverWifiOnly,
                        showNotification = prefs.showDownloadNotifications
                    )
                )
                message("Download started")
            }.onFailure {
                Log.w("NexaDownloads", "Could not start download", it)
                message("Could not start the download")
            }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        viewModelScope.launch { downloadRepository.cancel(item.id) }
    }

    fun retryDownload(item: DownloadItem) {
        viewModelScope.launch {
            runCatching { downloadRepository.retry(item); message("Retrying download") }
                .onFailure { message("Could not retry the download") }
        }
    }

    fun removeDownload(item: DownloadItem) {
        viewModelScope.launch { downloadRepository.remove(item.id) }
    }

    fun clearFinishedDownloads() {
        viewModelScope.launch { downloadRepository.clearFinished() }
    }

    /** Hands a completed download to whatever app can display it. */
    fun openDownloadedFile(context: Context, item: DownloadItem) {
        val uri = downloadRepository.uriFor(item.id)
        if (uri == null) {
            message("The file is no longer available")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { message("No installed app can open this file") }
    }

    suspend fun downloadUri(id: Long): Uri? = downloadRepository.uriFor(id)

    // -------------------------------------------------------- find in page

    fun showFind() = _uiState.update { it.copy(findActive = true) }

    fun hideFind() {
        tabManager.activeTab()?.webView?.clearMatches()
        _uiState.update { it.copy(findActive = false, findQuery = "", findCurrent = 0, findTotal = 0) }
    }

    fun findInPage(query: String) {
        _uiState.update { it.copy(findQuery = query) }
        val webView = tabManager.activeTab()?.webView ?: return
        if (query.isBlank()) {
            webView.clearMatches()
            _uiState.update { it.copy(findCurrent = 0, findTotal = 0) }
            return
        }
        webView.setFindListener { current, total, _ ->
            _uiState.update { it.copy(findCurrent = current, findTotal = total) }
        }
        webView.findAllAsync(query)
    }

    fun findNext() = tabManager.activeTab()?.webView?.findNext(true)
    fun findPrevious() = tabManager.activeTab()?.webView?.findNext(false)

    // ------------------------------------------------------------ desktop

    fun setDesktopSite(enabled: Boolean) {
        val id = tabManager.activeTabId.value ?: return
        tabManager.setDesktopSite(id, enabled)
        _uiState.update { it.copy(desktopSiteActive = enabled) }
    }

    fun toggleDesktopSite() = setDesktopSite(!_uiState.value.desktopSiteActive)

    // ----------------------------------------------------------- settings

    fun setSearchEngine(engine: SearchEngine) = launchWrite { dataStore.setSearchEngine(engine.id) }
    fun setCustomSearchUrl(url: String) = launchWrite { dataStore.setCustomSearchUrl(url) }
    fun setHomepage(url: String) = launchWrite { dataStore.setHomepage(url) }
    fun setTheme(mode: ThemeMode) = launchWrite { dataStore.setTheme(mode) }
    fun setNewTabBehavior(behavior: NewTabBehavior) = launchWrite { dataStore.setNewTabBehavior(behavior) }
    fun setTextZoom(zoom: Int) = launchWrite { dataStore.setTextZoom(zoom) }
    fun setDesktopByDefault(enabled: Boolean) = launchWrite { dataStore.setDesktopByDefault(enabled) }
    fun setJavaScriptEnabled(enabled: Boolean) = launchWrite { dataStore.setJavaScriptEnabled(enabled) }
    fun setPopupsEnabled(enabled: Boolean) = launchWrite { dataStore.setPopupsEnabled(enabled) }
    fun setCookiesEnabled(enabled: Boolean) = launchWrite {
        dataStore.setCookiesEnabled(enabled)
        CookieManager.getInstance().setAcceptCookie(enabled)
    }
    fun setSaveHistory(enabled: Boolean) = launchWrite { dataStore.setSaveHistory(enabled) }
    fun setDoNotTrack(enabled: Boolean) = launchWrite { dataStore.setDoNotTrack(enabled) }
    fun setSessionRestore(enabled: Boolean) = launchWrite { dataStore.setSessionRestore(enabled) }
    fun setShowClock(enabled: Boolean) = launchWrite { dataStore.setShowClock(enabled) }
    fun setStartBackground(value: String) = launchWrite { dataStore.setStartBackground(value) }
    fun setDownloadWifiOnly(enabled: Boolean) = launchWrite { dataStore.setDownloadWifiOnly(enabled) }
    fun setShowDownloadNotifications(enabled: Boolean) = launchWrite { dataStore.setShowDownloadNotifications(enabled) }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    // ------------------------------------------------------- data clearing

    fun clearCache() {
        viewModelScope.launch(Dispatchers.Main) {
            runCatching {
                val wv = tabManager.activeTab()?.webView ?: WebView(getApplication())
                wv.clearCache(true)
            }
            message("Cache cleared")
        }
    }

    fun clearCookies() {
        viewModelScope.launch(Dispatchers.Main) {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
            }
            message("Cookies cleared")
        }
    }

    fun clearWebsiteData() {
        viewModelScope.launch(Dispatchers.Main) {
            runCatching { WebStorage.getInstance().deleteAllData() }
            runCatching { faviconStore.clear() }
            message("Website data cleared")
        }
    }

    fun clearAllSitePermissions() {
        viewModelScope.launch { repository.clearPermissions(); message("Site permissions reset") }
    }

    fun revokeSitePermission(record: SitePermissionRecord) {
        viewModelScope.launch { repository.forgetPermission(record.origin, record.permission) }
    }

    // ---------------------------------------------------- web permissions

    fun onWebPermissionRequest(tabId: String, origin: String, resources: Array<String>, callback: PermissionRequest) {
        val perms = mutableListOf<SitePermission>()
        if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) perms.add(SitePermission.CAMERA)
        if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) perms.add(SitePermission.MICROPHONE)
        if (perms.isEmpty()) {
            callback.deny()
            return
        }
        viewModelScope.launch {
            val host = UrlUtils.originOf(origin) ?: origin
            val decisions = perms.map { it to repository.permissionFor(host, it) }
            when {
                decisions.any { it.second == PermissionDecision.DENY } -> callback.deny()
                decisions.all { it.second == PermissionDecision.ALLOW } -> callback.grant(resources)
                else -> {
                    pendingPermissionRequest = callback
                    _uiState.update {
                        it.copy(
                            permissionPrompt = PermissionPromptState(
                                origin = origin,
                                host = host,
                                permissions = perms,
                                geolocation = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun onGeolocationPermissionRequest(origin: String, callback: GeolocationPermissions.Callback) {
        viewModelScope.launch {
            val key = UrlUtils.originOf(origin) ?: origin
            when (repository.permissionFor(key, SitePermission.LOCATION)) {
                PermissionDecision.ALLOW -> callback.invoke(origin, true, false)
                PermissionDecision.DENY -> callback.invoke(origin, false, false)
                null -> {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    _uiState.update {
                        it.copy(
                            permissionPrompt = PermissionPromptState(
                                origin = origin,
                                host = key,
                                permissions = listOf(SitePermission.LOCATION),
                                geolocation = true
                            )
                        )
                    }
                }
            }
        }
    }

    /** OS permission strings the Activity must hold before granting a web request. */
    fun requiredOsPermissions(): Array<String> {
        val prompt = _uiState.value.permissionPrompt ?: return emptyArray()
        val out = mutableListOf<String>()
        prompt.permissions.forEach { p ->
            when (p) {
                SitePermission.CAMERA -> out.add(android.Manifest.permission.CAMERA)
                SitePermission.MICROPHONE -> out.add(android.Manifest.permission.RECORD_AUDIO)
                SitePermission.LOCATION -> {
                    out.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    out.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
        return out.toTypedArray()
    }

    /** Resolves the visible permission prompt. [osGranted] is false when the OS denied. */
    fun resolvePermission(allow: Boolean, remember: Boolean, osGranted: Boolean = true) {
        val prompt = _uiState.value.permissionPrompt
        val granted = allow && osGranted
        viewModelScope.launch {
            if (prompt != null && remember) {
                val decision = if (granted) PermissionDecision.ALLOW else PermissionDecision.DENY
                prompt.permissions.forEach { repository.rememberPermission(prompt.host, it, decision) }
            }
            pendingPermissionRequest?.let { req ->
                if (granted) req.grant(req.resources ?: emptyArray()) else req.deny()
            }
            pendingGeoCallback?.let { cb ->
                cb.invoke(pendingGeoOrigin ?: prompt?.origin ?: "", granted, remember)
            }
            pendingPermissionRequest = null
            pendingGeoCallback = null
            pendingGeoOrigin = null
            _uiState.update { it.copy(permissionPrompt = null) }
        }
    }

    // --------------------------------------------------------- js dialogs

    fun onJsDialog(type: JsDialogType, message: String, defaultValue: String?, result: android.webkit.JsResult) {
        pendingJsResult = result
        pendingJsType = type
        _uiState.update { it.copy(jsDialog = JsDialogState(type, message, defaultValue)) }
    }

    fun onJsPrompt(message: String, defaultValue: String?, result: JsPromptResult) {
        pendingJsPromptResult = result
        pendingJsType = JsDialogType.PROMPT
        _uiState.update { it.copy(jsDialog = JsDialogState(JsDialogType.PROMPT, message, defaultValue)) }
    }

    fun resolveJsDialog(confirmed: Boolean, promptValue: String? = null) {
        val result = pendingJsResult
        val promptResult = pendingJsPromptResult
        val type = pendingJsType
        pendingJsResult = null
        pendingJsPromptResult = null
        pendingJsType = null
        _uiState.update { it.copy(jsDialog = null) }
        when (type) {
            JsDialogType.ALERT -> result?.confirm()
            JsDialogType.CONFIRM -> if (confirmed) result?.confirm() else result?.cancel()
            JsDialogType.PROMPT -> if (confirmed) promptResult?.confirm(promptValue ?: "") else promptResult?.cancel()
            JsDialogType.BEFORE_UNLOAD -> if (confirmed) result?.confirm() else result?.cancel()
            null -> Unit
        }
    }

    // ---------------------------------------------------------- fullscreen

    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        pendingFullscreenCallback = callback
        _uiState.update { it.copy(fullscreenView = view) }
    }

    fun exitFullscreen() {
        pendingFullscreenCallback?.onCustomViewHidden()
        pendingFullscreenCallback = null
        _uiState.update { it.copy(fullscreenView = null) }
    }

    // ------------------------------------------------------------ lifecycle

    override fun onCleared() {
        saveSession()
        runCatching { tabManager.shutdown() }
        super.onCleared()
    }

    companion object {
        private const val HOUR = 60L * 60L * 1000L
        private const val WEEK = 7L * 24L * HOUR

        fun startOfToday(): Long {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}

/** Time ranges offered when clearing history. */
enum class HistoryRange { LAST_HOUR, TODAY, LAST_WEEK, ALL }
