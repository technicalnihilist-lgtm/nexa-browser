package com.nexabrowser.app.ui.screens

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
import androidx.compose.material.icons.filled.Refresh
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.DownloadItem
import com.nexabrowser.app.domain.model.DownloadStatus
import com.nexabrowser.app.ui.components.EmptyState
import java.util.Locale

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onOpen: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        if (downloads.isEmpty()) {
            EmptyState("No downloads yet", "Files you download will be listed here.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${downloads.size} item" + if (downloads.size == 1) "" else "s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (downloads.any { it.status.isFinished }) {
                    TextButton(onClick = onClearFinished) { Text("Clear finished") }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadRow(item, onOpen, onCancel, onRetry, onRemove)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    statusLine(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (item.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            when (item.status) {
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = { onOpen(item) }) {
                        Icon(NexaIcons.OpenInNew, contentDescription = "Open")
                    }
                    IconButton(onClick = { onRemove(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = { onRetry(item) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = { onRemove(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                    }
                }
                else -> {
                    IconButton(onClick = { onCancel(item) }) {
                        Icon(NexaIcons.Cancel, contentDescription = "Cancel")
                    }
                }
            }
        }

        val progress = item.progress
        if (item.status.isActive && progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        } else if (item.status.isActive) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

private fun statusLine(item: DownloadItem): String {
    val size = when {
        item.totalBytes > 0 -> "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
        item.downloadedBytes > 0 -> formatBytes(item.downloadedBytes)
        else -> "Size unknown"
    }
    return when (item.status) {
        DownloadStatus.PENDING -> "Waiting — $size"
        DownloadStatus.RUNNING -> size
        DownloadStatus.PAUSED -> "Paused — $size"
        DownloadStatus.COMPLETED -> "Completed — ${formatBytes(if (item.totalBytes > 0) item.totalBytes else item.downloadedBytes)}"
        DownloadStatus.FAILED -> item.reason ?: "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[index])
}
