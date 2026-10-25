package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.Bookmark
import com.nexabrowser.app.domain.model.BookmarkFolder
import com.nexabrowser.app.ui.components.CachedFavicon
import com.nexabrowser.app.ui.components.EmptyState
import com.nexabrowser.app.util.UrlUtils

@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    folders: List<BookmarkFolder>,
    onOpen: (String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onUpdate: (Bookmark, String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var newFolderDialog by remember { mutableStateOf(false) }

    if (bookmarks.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            EmptyState("No bookmarks yet", "Tap the star in the toolbar to save a page.", Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { newFolderDialog = true }) { Text("New Folder") }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${bookmarks.size} bookmark" + if (bookmarks.size == 1) "" else "s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = { newFolderDialog = true }) {
                    Icon(NexaIcons.CreateNewFolder, contentDescription = "New folder")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(bookmark.url) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CachedFavicon(bookmark.faviconUrl, bookmark.url, size = 30.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bookmark.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                UrlUtils.displayUrl(bookmark.url),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editing = bookmark }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit bookmark", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    editing?.let { bookmark ->
        var title by remember(bookmark.id) { mutableStateOf(bookmark.title) }
        var url by remember(bookmark.id) { mutableStateOf(bookmark.url) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit Bookmark") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(url, { url = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdate(bookmark, title, url); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("New Folder") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onCreateFolder(name); newFolderDialog = false }, enabled = name.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Cancel") } }
        )
    }
}
