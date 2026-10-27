package com.nexabrowser.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.ui.theme.LocalNexaColors

/** The browser menu, presented as a bottom sheet like Safari's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    visible: Boolean,
    desktopSiteEnabled: Boolean,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onReadingList: () -> Unit,
    onExtensions: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onSettings: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extras = LocalNexaColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet is a pane of liquid glass over the blurred page.
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.22f),
        dragHandle = null
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 32.dp),
            tint = extras.glassTint.copy(alpha = if (extras.isDark) 0.78f else 0.80f),
            elevation = 16.dp
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            MenuRow(Icons.Filled.Add, "New Tab", onNewTab)
            MenuRow(NexaIcons.VisibilityOff, "New Private Tab", onNewPrivateTab)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuRow(NexaIcons.Bookmark, "Bookmarks", onBookmarks)
            MenuRow(NexaIcons.History, "History", onHistory)
            MenuRow(NexaIcons.Download, "Downloads", onDownloads)
            MenuRow(Icons.Filled.List, "Reading List", onReadingList)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuRow(NexaIcons.Extension, "Extensions", onExtensions)
            MenuRow(Icons.Filled.Search, "Find on Page", onFindInPage)
            MenuRow(Icons.Filled.Share, "Share", onShare)
            MenuRow(NexaIcons.Link, "Copy Link", onCopyLink)
            MenuToggleRow(NexaIcons.Computer, "Request Desktop Site", desktopSiteEnabled, onToggleDesktopSite)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuRow(Icons.Filled.Settings, "Settings", onSettings)
        }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MenuToggleRow(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
