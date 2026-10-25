package com.nexabrowser.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.data.repository.BrowserRepository
import com.nexabrowser.app.domain.model.NewTabBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A tab that was closed and can be restored. */
data class ClosedTab(val url: String, val title: String, val isPrivate: Boolean)

/**
 * Owns every live [BrowserTab] and its WebViews, and is the single source of
 * truth for the tab list, the active tab, and private/regular separation.
 *
 * Lifecycle rules that keep this leak-free:
 *   * Exactly one WebView is created per tab, with the Activity context when one
 *     is alive so that native controls work.
 *   * Every tab is destroyed in [closeTab], [closeAllTabs] and [shutdown].
 *   * WebViews are never referenced from a long-lived singleton; the manager
 *     itself is owned by the ViewModel and released in onCleared().
 */
class BrowserTabManager(
    private val appContext: Context,
    private val repository: BrowserRepository,
    private val settingsProvider: () -> BrowserSettings,
    private val webViewContext: () -> Context,
    private val host: BrowserActivityHost,
    private val privateManager: PrivateBrowsingManager
) : TabEventSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    private val _tabInfos = MutableStateFlow<List<TabInfo>>(emptyList())
    val tabInfos: StateFlow<List<TabInfo>> = _tabInfos.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _privateMode = MutableStateFlow(false)
    val privateMode: StateFlow<Boolean> = _privateMode.asStateFlow()

    private val _closedTabs = MutableStateFlow<List<ClosedTab>>(emptyList())
    val closedTabs: StateFlow<List<ClosedTab>> = _closedTabs.asStateFlow()

    private val stateJobs = mutableMapOf<String, Job>()
    private var idCounter = 0

    /** Emits once per completed page load so callers can record history etc. */
    private val _pageFinished = MutableSharedFlow<TabInfo>(extraBufferCapacity = 16)
    val pageFinished: SharedFlow<TabInfo> = _pageFinished.asSharedFlow()

    fun visibleTabs(): List<TabInfo> = _tabInfos.value.filter { it.isPrivate == _privateMode.value }

    fun activeTab(): BrowserTab? = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun tabById(id: String): BrowserTab? = _tabs.value.firstOrNull { it.id == id }

    fun activeInfo(): TabInfo? = activeTab()?.info

    // ------------------------------------------------------------------ tab ops

    fun newTab(url: String? = null, isPrivate: Boolean = _privateMode.value, switchTo: Boolean = true): BrowserTab {
        val settings = settingsProvider()
        val target = when {
            url != null -> url
            isPrivate -> ""
            settings.newTabBehavior == NewTabBehavior.HOMEPAGE && settings.homepageUrl.isNotBlank() -> settings.homepageUrl
            else -> ""
        }
        val tab = createTabInternal(target, isPrivate)
        _tabs.value = _tabs.value + tab
        if (isPrivate && !_privateMode.value) _privateMode.value = true
        if (switchTo) _activeTabId.value = tab.id
        publishInfos()
        syncPrivateCount()
        return tab
    }

    private fun createTabInternal(url: String?, isPrivate: Boolean): BrowserTab {
        val id = "tab-${++idCounter}-${System.currentTimeMillis()}"
        val webView = BrowserWebViewFactory.create(
            context = webViewContext(),
            tabId = id,
            settings = settingsProvider(),
            sink = this,
            host = host,
            settingsProvider = settingsProvider
        )
        val tab = BrowserTab(id, isPrivate, webView)
        observe(tab)
        if (url.isNullOrBlank()) {
            webView.loadUrl("about:blank")
        } else {
            runCatching { webView.loadUrl(url) }
                .onFailure { Log.w(TAG, "Rejected start URL: $url"); webView.loadUrl("about:blank") }
        }
        return tab
    }

    fun selectTab(id: String) {
        val tab = tabById(id) ?: return
        if (tab.isPrivate && !_privateMode.value) _privateMode.value = true
        if (!tab.isPrivate && _privateMode.value) _privateMode.value = false
        _activeTabId.value = id
        publishInfos()
    }

    fun closeTab(id: String) {
        val tab = tabById(id) ?: return
        val wasActive = _activeTabId.value == id
        detach(tab)
        _tabs.value = _tabs.value.filterNot { it.id == id }
        rememberClosed(tab)
        if (wasActive) {
            val sameMode = _tabs.value.filter { it.isPrivate == tab.isPrivate }
            _activeTabId.value = (sameMode.lastOrNull() ?: _tabs.value.lastOrNull())?.id
        }
        if (_tabs.value.isEmpty()) {
            _privateMode.value = false
            newTab(url = "", isPrivate = false)
        }
        publishInfos()
        syncPrivateCount()
    }

    fun closeAllTabs(onlyPrivate: Boolean = false) {
        val targets = _tabs.value.filter { !onlyPrivate || it.isPrivate }
        if (targets.isEmpty()) return
        targets.forEach { tab ->
            detach(tab)
            rememberClosed(tab)
        }
        _tabs.value = _tabs.value.filterNot { t -> targets.any { it.id == t.id } }
        if (_tabs.value.isEmpty()) {
            _privateMode.value = false
            newTab(url = "", isPrivate = false)
        } else {
            if (onlyPrivate) _privateMode.value = false
            _activeTabId.value = _tabs.value.last().id
        }
        publishInfos()
        syncPrivateCount()
    }

    fun duplicateTab(id: String) {
        val tab = tabById(id) ?: return
        val url = tab.info.url.takeIf { it.isNotBlank() && it != "about:blank" }
        val copy = createTabInternal(url, tab.isPrivate)
        _tabs.value = _tabs.value + copy
        _activeTabId.value = copy.id
        publishInfos()
        syncPrivateCount()
    }

    fun setPrivateMode(enabled: Boolean) {
        if (_privateMode.value == enabled) return
        _privateMode.value = enabled
        val candidate = _tabs.value.lastOrNull { it.isPrivate == enabled }
        if (candidate != null) {
            _activeTabId.value = candidate.id
        } else {
            newTab(url = "", isPrivate = enabled)
        }
        publishInfos()
    }

    /** Re-opens the most recently closed tab. Returns true when one was restored. */
    fun reopenLastClosed(): Boolean {
        val closed = _closedTabs.value.lastOrNull() ?: return false
        _closedTabs.value = _closedTabs.value.dropLast(1)
        newTab(url = closed.url, isPrivate = closed.isPrivate, switchTo = true)
        return true
    }

    // ---------------------------------------------------------------- settings

    /** Re-applies WebView settings after a preference change. */
    fun applySettings(settings: BrowserSettings) {
        _tabs.value.forEach { tab ->
            BrowserWebViewFactory.applySettings(tab.webView, settings)
            if (settings.doNotTrackEnabled && tab.info.url.isNotBlank()) {
                BrowserWebViewFactory.injectDoNotTrack(tab.webView, true)
            }
            // Re-apply page extensions so toggling one takes effect immediately.
            if (tab.info.url.isNotBlank()) {
                runCatching {
                    tab.webView.evaluateJavascript(
                        PageInjections.applyScript(PageInjections.cssFor(settings)),
                        null
                    )
                }
            }
        }
    }

    /** Toggles the desktop user agent on one tab and reloads it. */
    fun setDesktopSite(tabId: String, enabled: Boolean) {
        val tab = tabById(tabId) ?: return
        tab.webView.settings.userAgentString = if (enabled) BrowserWebViewFactory.DESKTOP_UA else null
        tab.webView.reload()
    }

    // ----------------------------------------------------------------- session

    suspend fun saveSession() {
        val regular = _tabs.value.filter { !it.isPrivate }
            .mapIndexedNotNull { index, tab ->
                val url = tab.info.url
                if (url.isBlank() || url == "about:blank") null
                else Triple(url, tab.info.title, index)
            }
        repository.saveSessionTabs(regular)
    }

    // ------------------------------------------------------------ event sink

    override fun onPageStarted(tabId: String, url: String) {
        val blank = url == "about:blank"
        updateTab(tabId) {
            it.copy(
                url = if (blank) it.url else url,
                isLoading = !blank,
                progress = if (blank) it.progress else 5,
                error = null
            )
        }
        if (!blank && tabById(tabId)?.isPrivate == true) privateManager.recordPrivateVisit(url)
    }

    override fun onPageFinished(tabId: String, url: String) {
        val blank = url == "about:blank"
        updateTab(tabId) {
            it.copy(
                isLoading = false,
                progress = 100,
                url = if (blank) it.url else url,
                error = null
            )
        }
        if (!blank) tabById(tabId)?.let { _pageFinished.tryEmit(it.info) }
    }

    override fun onProgress(tabId: String, progress: Int) {
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitle(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title) }
    }

    override fun onFavicon(tabId: String, favicon: Bitmap?) {
        if (favicon != null) updateTab(tabId) { it.copy(favicon = favicon) }
    }

    override fun onUrlChanged(tabId: String, url: String) {
        if (url == "about:blank") return
        updateTab(tabId) { it.copy(url = url) }
        val tab = tabById(tabId) ?: return
        if (tab.isPrivate) privateManager.recordPrivateVisit(url)
    }

    override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onError(tabId: String, error: PageError) {
        updateTab(tabId) { it.copy(error = error, isLoading = false, progress = 100) }
    }

    // ----------------------------------------------------------------- private

    private fun observe(tab: BrowserTab) {
        stateJobs[tab.id] = scope.launch {
            tab.state.collect { publishInfos() }
        }
    }

    private fun detach(tab: BrowserTab) {
        stateJobs.remove(tab.id)?.cancel()
        tab.destroy()
    }

    private fun publishInfos() {
        _tabInfos.value = _tabs.value.map { it.info }
    }

    private fun updateTab(id: String, transform: (TabInfo) -> TabInfo) {
        _tabs.value.firstOrNull { it.id == id }?.update(transform)
    }

    private fun rememberClosed(tab: BrowserTab) {
        val info = tab.info
        if (info.url.isBlank() || info.url == "about:blank") return
        val list = _closedTabs.value + ClosedTab(info.url, info.displayTitle, tab.isPrivate)
        _closedTabs.value = if (list.size > MAX_CLOSED_TABS) list.takeLast(MAX_CLOSED_TABS) else list
    }

    private fun syncPrivateCount() {
        val count = _tabs.value.count { it.isPrivate }
        scope.launch { privateManager.syncOpenPrivateTabs(count, settingsProvider().cookiesEnabled) }
    }

    /** Destroys every WebView and stops all observation. */
    fun shutdown() {
        stateJobs.values.forEach { it.cancel() }
        stateJobs.clear()
        _tabs.value.forEach { runCatching { it.destroy() } }
        _tabs.value = emptyList()
        _tabInfos.value = emptyList()
        _activeTabId.value = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "NexaTabs"
        private const val MAX_CLOSED_TABS = 20
    }
}
