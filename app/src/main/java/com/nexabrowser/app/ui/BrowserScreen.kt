package com.nexabrowser.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.nexabrowser.app.ui.components.DockAction
import com.nexabrowser.app.ui.components.LiquidGlassDock
import com.nexabrowser.app.ui.components.WebViewHost
import com.nexabrowser.app.ui.screens.StartPage
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.ui.theme.backdropBlur
import com.nexabrowser.app.ui.theme.EdgeFade

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
    // Which dock slot the glass capsule rests on; it glides when this changes.
    var dockIndex by remember { mutableStateOf(0) }

    val activeWebView = viewModel.tabManager.activeTab()?.webView

    // Chrome height. The page runs full-bleed *under* the glass bars; it is only
    // inset while the page sits at its very top with the bars fully open, so a
    // site's own header is never hidden behind the address bar on arrival.
    val systemInsets = WindowInsets.systemBars.asPaddingValues()
    val chromeTop = systemInsets.calculateTopPadding() + 68.dp
    val chromeBottom = systemInsets.calculateBottomPadding() + 82.dp

    val density = LocalDensity.current
    val collapseThreshold = with(density) { 28.dp.roundToPx() }
    val expandThreshold = with(density) { 14.dp.roundToPx() }

    // Safari-style chrome: bars shrink away while reading, return on scroll up.
    var collapsed by remember { mutableStateOf(false) }
    var atTop by remember { mutableStateOf(true) }
    var scrollAccum by remember { mutableStateOf(0) }

    LaunchedEffect(activeWebView) {
        collapsed = false
        scrollAccum = 0
        atTop = (activeWebView?.scrollY ?: 0) <= 0
    }
    LaunchedEffect(activeTab?.url) {
        collapsed = false
        scrollAccum = 0
        atTop = true
    }

    val showingPage = activeTab != null && !activeTab.showStartPage
    val chromeCollapsed = collapsed && showingPage && !uiState.findActive

    val collapse by animateFloatAsState(
        targetValue = if (chromeCollapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "chromeCollapse"
    )
    val insetOn = !chromeCollapsed && atTop
    val topInset by animateDpAsState(
        targetValue = if (insetOn) chromeTop else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
        label = "topInset"
    )
    val bottomInset by animateDpAsState(
        targetValue = if (insetOn) chromeBottom else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
        label = "bottomInset"
    )

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
                        .padding(top = topInset, bottom = bottomInset),
                    onScrolled = { y, dy ->
                        atTop = y <= 0
                        if (y <= 0) {
                            collapsed = false
                            scrollAccum = 0
                        } else {
                            if ((dy > 0 && scrollAccum < 0) || (dy < 0 && scrollAccum > 0)) scrollAccum = 0
                            scrollAccum += dy
                            if (scrollAccum > collapseThreshold) {
                                collapsed = true
                                scrollAccum = 0
                            } else if (scrollAccum < -expandThreshold) {
                                collapsed = false
                                scrollAccum = 0
                            }
                        }
                    }
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

        // ---- Edge fades: keep the glass legible over busy pages ------------
        if (showingPage) {
            EdgeFade(
                top = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(chromeTop + 14.dp)
                    .graphicsLayer { alpha = 1f - 0.5f * collapse }
            )
            EdgeFade(
                top = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(chromeBottom + 14.dp)
                    .graphicsLayer { alpha = 1f - collapse }
            )
        }

        // ---- Top: address bar and find bar --------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    val s = 1f - 0.16f * collapse
                    scaleX = s
                    scaleY = s
                    translationY = -10.dp.toPx() * collapse
                    alpha = 1f - 0.12f * collapse
                }
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

        // While collapsed, a tap on the small top pill brings the bars back.
        if (chromeCollapsed) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(chromeTop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { collapsed = false }
            )
        }

        // ---- Bottom: toolbar ---------------------------------------------
        LiquidGlassDock(
            activeIndex = dockIndex,
            style = settings.addressBarStyle,
            actions = listOf(
                DockAction(
                    label = "Back",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    enabled = activeTab?.canGoBack == true
                ) { dockIndex = 0; viewModel.goBack() },
                DockAction(
                    label = "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = activeTab?.canGoForward == true
                ) { dockIndex = 1; viewModel.goForward() },
                DockAction(label = "Share", icon = Icons.Filled.Share) {
                    dockIndex = 2; onShare()
                },
                DockAction(
                    label = if (isBookmarked) "Saved" else "Save",
                    icon = if (isBookmarked) Icons.Filled.Star else NexaIcons.StarBorder
                ) { dockIndex = 3; viewModel.toggleBookmark() },
                DockAction(label = "Tabs", icon = NexaIcons.Tab) {
                    dockIndex = 4; viewModel.setScreen(NexaScreen.TABS)
                },
                DockAction(label = "More", icon = Icons.Filled.Menu) {
                    dockIndex = 5; viewModel.openMenu()
                }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val s = 1f - 0.10f * collapse
                    scaleX = s
                    scaleY = s
                    translationY = 150.dp.toPx() * collapse
                    alpha = 1f - collapse
                }
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
