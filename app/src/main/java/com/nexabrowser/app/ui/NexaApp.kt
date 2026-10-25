package com.nexabrowser.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexabrowser.app.browser.JsDialogType
import com.nexabrowser.app.ui.components.BrowserMenuSheet
import com.nexabrowser.app.ui.components.ScreenTopBar
import com.nexabrowser.app.ui.screens.BookmarksScreen
import com.nexabrowser.app.ui.screens.DownloadsScreen
import com.nexabrowser.app.ui.screens.HistoryScreen
import com.nexabrowser.app.ui.screens.ReadingListScreen
import com.nexabrowser.app.ui.screens.SettingsScreen
import com.nexabrowser.app.ui.screens.TabOverview
import com.nexabrowser.app.ui.theme.NexaTheme

/**
 * Root composable. Chooses the visible destination, hosts the overlays (menu,
 * JavaScript dialogs, permission prompts, fullscreen video) and wires the
 * platform back gesture to the right behaviour.
 */
@Composable
fun NexaApp(
    viewModel: BrowserViewModel,
    onPermissionDecision: (allow: Boolean, remember: Boolean) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs by viewModel.tabManager.tabInfos.collectAsStateWithLifecycle()
    val activeId by viewModel.tabManager.activeTabId.collectAsStateWithLifecycle()
    val privateMode by viewModel.tabManager.privateMode.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val folders by viewModel.bookmarkFolders.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val readingList by viewModel.readingList.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val topSites by viewModel.topSites.collectAsStateWithLifecycle()
    val permissions by viewModel.sitePermissions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab = tabs.firstOrNull { it.id == activeId }
    val visibleTabs = tabs.filter { it.isPrivate == privateMode }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Back gesture: close overlays first, then page history, then the app.
    BackHandler {
        when {
            uiState.fullscreenView != null -> viewModel.exitFullscreen()
            uiState.menuOpen -> viewModel.closeMenu()
            uiState.findActive -> viewModel.hideFind()
            uiState.screen != NexaScreen.BROWSER -> viewModel.setScreen(NexaScreen.BROWSER)
            viewModel.goBack() -> Unit
            else -> (context as? Activity)?.finish()
        }
    }

    NexaTheme(themeMode = settings.themeMode, isPrivate = privateMode) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (uiState.screen) {
                NexaScreen.BROWSER -> BrowserScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    activeTab = activeTab,
                    tabCount = visibleTabs.size,
                    isBookmarked = isBookmarked,
                    settings = settings,
                    shortcuts = shortcuts,
                    topSites = topSites,
                    recent = history.take(6),
                    onOpenUrl = { url -> viewModel.submitAddress(url) },
                    onShare = {
                        activeTab?.url?.takeIf { it.isNotBlank() }?.let { shareText(context, it) }
                    }
                )

                NexaScreen.TABS -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(
                        title = if (privateMode) "Private Tabs" else "Tabs",
                        onBack = { viewModel.setScreen(NexaScreen.BROWSER) }
                    )
                    TabOverview(
                        tabs = visibleTabs,
                        activeId = activeId,
                        isPrivate = privateMode,
                        onSelect = { viewModel.selectTab(it); viewModel.setScreen(NexaScreen.BROWSER) },
                        onClose = viewModel::closeTab,
                        onDuplicate = viewModel::duplicateTab,
                        onNewTab = { viewModel.newTab(); viewModel.setScreen(NexaScreen.BROWSER) },
                        onCloseAll = { viewModel.closeAllTabs(privateOnly = privateMode) },
                        onTogglePrivate = { viewModel.setPrivateMode(!privateMode) },
                        modifier = Modifier.weight(1f)
                    )
                }

                NexaScreen.BOOKMARKS -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Bookmarks", onBack = { viewModel.setScreen(NexaScreen.BROWSER) })
                    BookmarksScreen(
                        bookmarks = bookmarks,
                        folders = folders,
                        onOpen = { url -> viewModel.submitAddress(url); viewModel.setScreen(NexaScreen.BROWSER) },
                        onDelete = viewModel::deleteBookmark,
                        onUpdate = { b, t, u -> viewModel.updateBookmark(b, t, u) },
                        onCreateFolder = viewModel::createFolder,
                        modifier = Modifier.weight(1f)
                    )
                }

                NexaScreen.HISTORY -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "History", onBack = { viewModel.setScreen(NexaScreen.BROWSER) })
                    HistoryScreen(
                        history = history,
                        onOpen = { url -> viewModel.submitAddress(url); viewModel.setScreen(NexaScreen.BROWSER) },
                        onDelete = viewModel::deleteHistoryEntry,
                        onClear = viewModel::clearHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                NexaScreen.DOWNLOADS -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Downloads", onBack = { viewModel.setScreen(NexaScreen.BROWSER) })
                    DownloadsScreen(
                        downloads = downloads,
                        onOpen = { item -> viewModel.openDownloadedFile(context, item) },
                        onCancel = viewModel::cancelDownload,
                        onRetry = viewModel::retryDownload,
                        onRemove = viewModel::removeDownload,
                        onClearFinished = viewModel::clearFinishedDownloads,
                        modifier = Modifier.weight(1f)
                    )
                }

                NexaScreen.READING_LIST -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Reading List", onBack = { viewModel.setScreen(NexaScreen.BROWSER) })
                    ReadingListScreen(
                        items = readingList,
                        onOpen = { item ->
                            val target = item.archivePath?.takeIf { item.hasOfflineCopy } ?: item.url
                            viewModel.submitAddress(target)
                            viewModel.setScreen(NexaScreen.BROWSER)
                        },
                        onRemove = viewModel::removeFromReadingList,
                        modifier = Modifier.weight(1f)
                    )
                }

                NexaScreen.SETTINGS -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Settings", onBack = { viewModel.setScreen(NexaScreen.BROWSER) })
                    SettingsScreen(
                        settings = settings,
                        permissions = permissions,
                        onSetSearchEngine = viewModel::setSearchEngine,
                        onSetCustomSearch = viewModel::setCustomSearchUrl,
                        onSetHomepage = viewModel::setHomepage,
                        onSetTheme = viewModel::setTheme,
                        onSetNewTab = viewModel::setNewTabBehavior,
                        onSetTextZoom = viewModel::setTextZoom,
                        onSetDesktopByDefault = viewModel::setDesktopByDefault,
                        onSetJavaScript = viewModel::setJavaScriptEnabled,
                        onSetPopups = viewModel::setPopupsEnabled,
                        onSetCookies = viewModel::setCookiesEnabled,
                        onSetSaveHistory = viewModel::setSaveHistory,
                        onSetDoNotTrack = viewModel::setDoNotTrack,
                        onSetSessionRestore = viewModel::setSessionRestore,
                        onSetShowClock = viewModel::setShowClock,
                        onSetBackground = viewModel::setStartBackground,
                        onSetWifiOnly = viewModel::setDownloadWifiOnly,
                        onSetNotifications = viewModel::setShowDownloadNotifications,
                        onClearHistory = { viewModel.clearHistory(HistoryRange.ALL) },
                        onClearCache = viewModel::clearCache,
                        onClearCookies = viewModel::clearCookies,
                        onClearWebsiteData = viewModel::clearWebsiteData,
                        onClearPermissions = viewModel::clearAllSitePermissions,
                        onRevokePermission = viewModel::revokeSitePermission,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp)
            )

            BrowserMenuSheet(
                visible = uiState.menuOpen,
                desktopSiteEnabled = uiState.desktopSiteActive || settings.desktopSiteByDefault,
                onDismiss = viewModel::closeMenu,
                onNewTab = { viewModel.newTab(); viewModel.closeMenu() },
                onNewPrivateTab = { viewModel.newPrivateTab(); viewModel.closeMenu() },
                onBookmarks = { viewModel.setScreen(NexaScreen.BOOKMARKS) },
                onHistory = { viewModel.setScreen(NexaScreen.HISTORY) },
                onDownloads = { viewModel.setScreen(NexaScreen.DOWNLOADS) },
                onReadingList = { viewModel.addCurrentToReadingList(); viewModel.setScreen(NexaScreen.READING_LIST) },
                onFindInPage = { viewModel.closeMenu(); viewModel.showFind() },
                onShare = {
                    viewModel.closeMenu()
                    activeTab?.url?.takeIf { it.isNotBlank() }?.let { shareText(context, it) }
                },
                onCopyLink = {
                    viewModel.closeMenu()
                    activeTab?.url?.takeIf { it.isNotBlank() }?.let { copyText(context, it) }
                },
                onToggleDesktopSite = { viewModel.toggleDesktopSite(); viewModel.closeMenu() },
                onSettings = { viewModel.setScreen(NexaScreen.SETTINGS) }
            )

            uiState.jsDialog?.let { dialog ->
                JsDialog(
                    type = dialog.type,
                    message = dialog.message,
                    defaultValue = dialog.defaultValue,
                    onResolve = { confirmed, value -> viewModel.resolveJsDialog(confirmed, value) }
                )
            }

            uiState.permissionPrompt?.let { prompt ->
                var remember by remember(prompt) { mutableStateOf(true) }
                AlertDialog(
                    onDismissRequest = { viewModel.resolvePermission(false, false) },
                    title = { Text("Allow access?") },
                    text = {
                        Column {
                            Text(
                                prompt.host + " wants to use " +
                                    prompt.permissions.joinToString(" and ") { it.name.lowercase() } + "."
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = remember, onCheckedChange = { remember = it })
                                Spacer(Modifier.width(8.dp))
                                Text("Remember for this site", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { onPermissionDecision(true, remember) }) { Text("Allow") } },
                    dismissButton = { TextButton(onClick = { viewModel.resolvePermission(false, remember) }) { Text("Don't Allow") } }
                )
            }

            uiState.fullscreenView?.let { view ->
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(AndroidColor.BLACK)
                                addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { viewModel.exitFullscreen() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun JsDialog(
    type: JsDialogType,
    message: String,
    defaultValue: String?,
    onResolve: (Boolean, String?) -> Unit
) {
    var text by remember(message) { mutableStateOf(defaultValue ?: "") }
    AlertDialog(
        onDismissRequest = { onResolve(false, null) },
        title = {
            Text(
                when (type) {
                    JsDialogType.ALERT -> "This page says"
                    JsDialogType.CONFIRM -> "Confirm"
                    JsDialogType.PROMPT -> "Enter a value"
                    JsDialogType.BEFORE_UNLOAD -> "Leave this page?"
                }
            )
        },
        text = {
            Column {
                Text(message)
                if (type == JsDialogType.PROMPT) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onResolve(true, text) }) {
                Text(if (type == JsDialogType.BEFORE_UNLOAD) "Leave" else "OK")
            }
        },
        dismissButton = if (type == JsDialogType.ALERT) null else {
            { TextButton(onClick = { onResolve(false, null) }) { Text("Cancel") } }
        }
    )
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nexa Browser", text))
}
