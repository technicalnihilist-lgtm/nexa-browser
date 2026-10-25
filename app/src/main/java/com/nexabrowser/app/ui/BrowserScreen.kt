package com.nexabrowser.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.browser.PageError
import com.nexabrowser.app.browser.TabInfo
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.domain.model.HistoryEntry
import com.nexabrowser.app.domain.model.Shortcut
import com.nexabrowser.app.domain.model.TopSite
import com.nexabrowser.app.ui.components.AddressBar
import com.nexabrowser.app.ui.components.BottomToolbar
import com.nexabrowser.app.ui.components.WebViewHost
import com.nexabrowser.app.ui.screens.StartPage
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.ui.theme.backdropBlur
import com.nexabrowser.app.ui.theme.barChromeColor

/**
 * The browser surface: the live WebView (or the start page), the floating glass
 * address bar, the bottom toolbar, the find bar and the error overlay.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    uiState: BrowserUiState,
    activeTab: TabInfo?,
    tabCount: Int,
    isBookmarked: Boolean,
    settings: BrowserSettings,
    shortcuts: List<Shortcut>,
    topSites: List<TopSite>,
    recent: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeWebView = viewModel.tabManager.activeTab()?.webView

    // The page is inset by the chrome height so a site's own header can never
    // sit underneath the address bar (which reads as a second search bar).
    val systemInsets = WindowInsets.systemBars.asPaddingValues()
    val chromeTop = systemInsets.calculateTopPadding() + 68.dp
    val chromeBottom = systemInsets.calculateBottomPadding() + 72.dp

    Box(modifier.fillMaxSize()) {
        // ---- Page content -------------------------------------------------
        // The menu opens over this, so the page genuinely blurs behind the glass
        // sheet (RenderEffect, API 31+). Nothing else blocks navigation.
        Box(Modifier.fillMaxSize().backdropBlur(uiState.menuOpen, 22.dp)) {
            if (activeTab == null || activeTab.showStartPage) {
                StartPage(
                    shortcuts = shortcuts,
                    topSites = topSites,
                    recent = recent,
                    showClock = settings.showClockOnStartPage,
                    backgroundId = settings.startPageBackground,
                    onOpenUrl = onOpenUrl,
                    onAddShortcut = viewModel::addShortcut,
                    onDeleteShortcut = viewModel::deleteShortcut,
                    onReorderShortcuts = viewModel::reorderShortcuts
                )
            } else {
                WebViewHost(
                    activeWebView,
                    Modifier
                        .fillMaxSize()
                        .padding(top = chromeTop, bottom = chromeBottom)
                )
            }

            AnimatedVisibility(
                visible = activeTab?.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                activeTab?.error?.let { error ->
                    ErrorOverlay(error = error, onRetry = { viewModel.reload() })
                }
            }
        }

        // ---- Top: address bar and find bar --------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(barChromeColor(settings.addressBarStyle))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AddressBar(
                tab = activeTab,
                isLoading = activeTab?.isLoading == true,
                progress = activeTab?.progress ?: 0,
                style = settings.addressBarStyle,
                onNavigate = viewModel::submitAddress,
                onReload = viewModel::reload,
                onStop = viewModel::stopLoading
            )

            if (uiState.findActive) {
                FindBar(
                    query = uiState.findQuery,
                    current = uiState.findCurrent,
                    total = uiState.findTotal,
                    onQueryChange = viewModel::findInPage,
                    onNext = viewModel::findNext,
                    onPrevious = viewModel::findPrevious,
                    onClose = viewModel::hideFind
                )
            }
        }

        // ---- Bottom: toolbar ---------------------------------------------
        BottomToolbar(
            canGoBack = activeTab?.canGoBack == true,
            canGoForward = activeTab?.canGoForward == true,
            isBookmarked = isBookmarked,
            tabCount = tabCount,
            style = settings.addressBarStyle,
            onBack = { viewModel.goBack() },
            onForward = { viewModel.goForward() },
            onShare = onShare,
            onBookmark = viewModel::toggleBookmark,
            onTabs = { viewModel.setScreen(NexaScreen.TABS) },
            onMenu = viewModel::openMenu,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(barChromeColor(settings.addressBarStyle))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FindBar(
    query: String,
    current: Int,
    total: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    GlassSurface(shape = RoundedCornerShape(20.dp), elevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find on page") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (query.isBlank()) "" else if (total == 0) "No matches" else "$current of $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close find bar", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ErrorOverlay(error: PageError, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(shape = RoundedCornerShape(24.dp), elevation = 16.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (error.isNetworkError) NexaIcons.CloudOff else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("This page couldn't be loaded", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (error.isNetworkError) {
                        "You appear to be offline, or the site could not be reached. Check your connection and try again."
                    } else {
                        error.description
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (error.url.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Try Again") }
            }
        }
    }
}
