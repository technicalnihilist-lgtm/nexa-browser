package com.nexabrowser.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.ui.theme.GlassSurface

/** The translucent bottom bar: history navigation, share, bookmark, tabs, menu. */
@Composable
fun BottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        shape = RoundedCornerShape(26.dp),
        elevation = 12.dp,
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else NexaIcons.StarBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onTabs) {
                    Icon(NexaIcons.Tab, contentDescription = "Tabs")
                }
                if (tabCount > 1) {
                    CountBadge(count = tabCount, modifier = Modifier.padding(top = 4.dp, end = 2.dp))
                }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        }
    }
}
