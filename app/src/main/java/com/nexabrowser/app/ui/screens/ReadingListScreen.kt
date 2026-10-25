package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.ReadingListItem
import com.nexabrowser.app.ui.components.CachedFavicon
import com.nexabrowser.app.ui.components.EmptyState
import com.nexabrowser.app.util.UrlUtils

@Composable
fun ReadingListScreen(
    items: List<ReadingListItem>,
    onOpen: (ReadingListItem) -> Unit,
    onRemove: (ReadingListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyState("Your reading list is empty", "Use the menu to save a page for later.", modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "${items.size} saved page" + if (items.size == 1) "" else "s",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CachedFavicon(item.faviconUrl, item.url, size = 30.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.size(2.dp))
                        Text(
                            UrlUtils.displayUrl(item.url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (item.hasOfflineCopy) NexaIcons.CloudDone else NexaIcons.Language,
                        contentDescription = if (item.hasOfflineCopy) "Available offline" else "Online only",
                        tint = if (item.hasOfflineCopy) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    IconButton(onClick = { onRemove(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
