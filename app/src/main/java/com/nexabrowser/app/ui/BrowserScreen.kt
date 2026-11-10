package com.nexabrowser.app.ui

import com.nexabrowser.app.ui.components.LiquidGlassNav
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.State
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
import com.nexabrowser.app.ui.components.ScrollAccumulator
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
    progress: State<Int>,
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

    val systemInsets = WindowInsets.systemBars.asPaddingValues()
    val statusBarHeight = systemInsets.calculateTopPadding()

    val density = LocalDensity.current
    val hideThreshold = with(density) { 26.dp.roundToPx() }
    val showThreshold = with(density) { 12.dp.roundToPx() }

    // The toolbar slides away while reading and returns on an upward scroll,
    // as the reference does.
    var toolbarHidden by remember { mutableStateOf(false) }
    // Deliberately not snapshot state: see ScrollAccumulator. The accumulator
    // holds the per-gesture distance, which changes on every scroll frame but
    // never affects what is drawn, so keeping it in a `mutableStateOf` made the
    // whole browser surface recompose once per frame while scrolling.
    val scroller = remember(hideThreshold, showThreshold) {
        ScrollAccumulator(hideThreshold, showThreshold)
    }

    LaunchedEffect(activeTab?.url) {
        scroller.reset()
        toolbarHidden = false
    }

    val footerShift by animateDpAsState(
        targetValue = if (toolbarHidden) 200.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "footerShift"
    )

    Box(modifier.fillMaxSize()) {
        // ---- Page ------------------------------------------------------
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
                        .padding(top = statusBarHeight),
                    // One state write only when the toolbar actually has to
                    // move; every other scroll event costs an addition.
                    onScrolled = { y, dy ->
                        if (scroller.onScroll(y, dy)) toolbarHidden = scroller.hidden
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

        // ---- 3dp loading bar at the very top ---------------------------
        LoadingBar(progress)

        // ---- Find on page ---------------------------------------------
        if (uiState.findActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
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

        // ---- "Show toolbar" pill while hidden -------------------------
        AnimatedVisibility(
            visible = toolbarHidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            GlassSurface(shape = RoundedCornerShape(14.dp), elevation = 8.dp) {
                Text(
                    text = "Show toolbar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scroller.reset()
                            toolbarHidden = false
                        }
                        .padding(horizontal = 26.dp, vertical = 8.dp)
                )
            }
        }

        // ---- Bottom: address bar over navigation ----------------------
        //
        // The slide away and back is a draw-phase translation, not a layout
        // offset. `Modifier.offset` moves the node's layout position, so every
        // frame of the spring re-measured and re-laid out this whole column —
        // the glass address bar, the navigation dock, their shadows and every
        // icon inside them — at exactly the moment the user is scrolling the
        // page and the frame budget is tightest. A graphicsLayer translation
        // moves the already-laid-out pixels, so the animation now costs one
        // draw per frame instead of a measure, a layout and a draw. The
        // translation is part of the node's transform, so touch input follows
        // the bars exactly as before and nothing drawn changes.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = footerShift.toPx() }
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AddressBar(
                tab = activeTab,
                style = settings.addressBarStyle,
                isLoading = activeTab?.isLoading == true,
                onNavigate = viewModel::submitAddress,
                onReload = { viewModel.reload() },
                onStop = { viewModel.stopLoading() },
                onSiteSettings = viewModel::openMenu
            )
            LiquidGlassNav(
                canGoBack = activeTab?.canGoBack == true,
                canGoForward = activeTab?.canGoForward == true,
                isBookmarked = isBookmarked,
                tabCount = tabCount,
                style = settings.addressBarStyle,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() },
                onShare = onShare,
                onBookmarks = { viewModel.setScreen(NexaScreen.BOOKMARKS) },
                onTabs = { viewModel.setScreen(NexaScreen.TABS) }
            )
        }
    }
}

/**
 * The 3dp page-load bar.
 *
 * The progress value is read *here*, inside this composable's own scope, so a
 * progress tick invalidates this Box alone. Reading it in [BrowserScreen]'s body
 * — which is what happened before — made every tick recompose the page host,
 * the address bar, the navigation dock and the error overlay with it.
 */
@Composable
private fun BoxScope.LoadingBar(progress: State<Int>) {
    val value = progress.value.coerceIn(0, 100)
    if (value in 1..99) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth(value / 100f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary)
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
