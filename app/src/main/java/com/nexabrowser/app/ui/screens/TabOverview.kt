package com.nexabrowser.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.browser.TabInfo
import com.nexabrowser.app.ui.components.BitmapFavicon
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.util.UrlUtils

/**
 * The tab switcher. Each tab is a card showing its live title and favicon.
 *
 * A note on previews: cards do not contain a rasterised screenshot of the page.
 * Drawing a detached WebView is unreliable across OEM WebView builds and costs a
 * lot of memory per tab, so the card shows the tab's own title, favicon and
 * origin instead. The live page is restored instantly when the tab is selected
 * because the WebView itself is kept alive.
 */
@Composable
fun TabOverview(
    tabs: List<TabInfo>,
    activeId: String?,
    isPrivate: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onTogglePrivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmCloseAll by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { NewTabCard(onNewTab) }
            items(tabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    selected = tab.id == activeId,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                    onDuplicate = { onDuplicate(tab.id) }
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSurface(shape = RoundedCornerShape(24.dp), elevation = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    IconButton(onClick = onTogglePrivate) {
                        Icon(
                            if (isPrivate) NexaIcons.Visibility else NexaIcons.VisibilityOff,
                            contentDescription = if (isPrivate) "Show regular tabs" else "Show private tabs",
                            tint = if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        if (isPrivate) "Private" else "${tabs.size} Tab" + if (tabs.size == 1) "" else "s",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            GlassSurface(shape = RoundedCornerShape(24.dp), elevation = 10.dp) {
                TextButton(onClick = { confirmCloseAll = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Close All")
                }
            }
        }
    }

    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text("Close all tabs?") },
            text = {
                Text(
                    if (isPrivate) "This closes every private tab and clears the private session's website data."
                    else "This closes every regular tab."
                )
            },
            confirmButton = {
                TextButton(onClick = { onCloseAll(); confirmCloseAll = false }) { Text("Close All") }
            },
            dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Card motion: springs in from slightly small and transparent when it first
 * appears, and presses down a touch under the finger.
 */
@Composable
private fun Modifier.cardMotion(pressed: Boolean): Modifier {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "cardPress"
    )
    return this.graphicsLayer {
        val s = (0.86f + 0.14f * appear.value) * press
        scaleX = s
        scaleY = s
        alpha = appear.value.coerceIn(0f, 1f)
    }
}

@Composable
private fun NewTabCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        elevation = 6.dp,
        modifier = Modifier
            .cardMotion(pressed)
            .fillMaxWidth()
            .height(170.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "New tab", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TabCard(
    tab: TabInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        elevation = if (selected) 14.dp else 6.dp,
        modifier = Modifier
            .cardMotion(pressed)
            .fillMaxWidth()
            .height(170.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(NexaIcons.ContentCopy, contentDescription = "Duplicate tab", modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(15.dp))
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    BitmapFavicon(tab.favicon, tab.url, size = 26.dp)
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    tab.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tab.showStartPage) "Start Page" else UrlUtils.displayUrl(tab.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        tab.error != null -> "Couldn't load"
                        tab.isLoading -> "Loading…"
                        else -> "Loaded"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tab.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    maxLines = 1
                )
            }
        }
    }
}
