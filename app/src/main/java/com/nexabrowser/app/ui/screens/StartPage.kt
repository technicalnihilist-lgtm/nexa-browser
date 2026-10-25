package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.HistoryEntry
import com.nexabrowser.app.domain.model.Shortcut
import com.nexabrowser.app.domain.model.TopSite
import com.nexabrowser.app.ui.components.CachedFavicon
import com.nexabrowser.app.ui.theme.NexaIcons
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.ui.theme.LocalNexaColors
import com.nexabrowser.app.util.UrlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Background presets offered in Settings; the value is what gets persisted. */
object StartBackgrounds {
    val options = listOf("default", "aurora", "sunset", "mint", "graphite", "paper")

    fun brush(id: String, isDark: Boolean): Brush? = when (id) {
        "aurora" -> Brush.verticalGradient(listOf(Color(0xFF2F6BFF), Color(0xFF8B5CF6)))
        "sunset" -> Brush.verticalGradient(listOf(Color(0xFFFF7A59), Color(0xFFE5484D)))
        "mint" -> Brush.verticalGradient(listOf(Color(0xFF12B5A5), Color(0xFF22C55E)))
        "graphite" -> Brush.verticalGradient(listOf(Color(0xFF2A2C31), Color(0xFF111214)))
        "paper" -> Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFE7E9EE)))
        else -> null
    }
}

/**
 * The start page. Everything on it is real user data — shortcuts the user made,
 * frequently visited sites computed from stored history, and recent visits.
 * Nothing here is fabricated or seeded with sample content.
 */
@Composable
fun StartPage(
    shortcuts: List<Shortcut>,
    topSites: List<TopSite>,
    recent: List<HistoryEntry>,
    showClock: Boolean,
    backgroundId: String,
    onOpenUrl: (String) -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onDeleteShortcut: (Shortcut) -> Unit,
    onReorderShortcuts: (List<Shortcut>) -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = LocalNexaColors.current
    val bg = StartBackgrounds.brush(backgroundId, extras.isDark)

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(showClock) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(30_000)
        }
    }

    var editing by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Shortcut?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (bg != null) Modifier.background(bg) else Modifier)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (showClock) {
                item {
                    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
                    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val clockSize = MaterialTheme.typography.headlineSmall.fontSize * 2.2f
                        if (backgroundId.isNotBlank()) {
                            // Over a wallpaper the clock is drawn as translucent
                            // glass: a light rim plus a filled gradient.
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    timeFmt.format(now),
                                    style = TextStyle(
                                        fontSize = clockSize,
                                        fontWeight = FontWeight.Light,
                                        brush = Brush.verticalGradient(
                                            listOf(Color(0x59FFFFFF), Color(0x22FFFFFF))
                                        ),
                                        drawStyle = Stroke(width = 2f)
                                    )
                                )
                                Text(
                                    timeFmt.format(now),
                                    style = TextStyle(
                                        fontSize = clockSize,
                                        fontWeight = FontWeight.Light,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color(0xF2FFFFFF),
                                                Color(0x8CFFFFFF),
                                                Color(0x66FFFFFF)
                                            )
                                        )
                                    )
                                )
                            }
                        } else {
                            Text(
                                timeFmt.format(now),
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = clockSize),
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            dateFmt.format(now),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Favorites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editing = !editing }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (editing) "Done" else "Edit")
                    }
                }
            }

            item {
                val tiles: List<Shortcut?> = shortcuts + listOf<Shortcut?>(null)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(((tiles.size + 3) / 4 * 96).dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(tiles) { item ->
                        if (item == null) {
                            ShortcutTile(
                                title = "Add",
                                url = "",
                                isAdd = true,
                                editing = false,
                                onClick = { addDialog = true },
                                onLongClick = {}
                            )
                        } else {
                            ShortcutTile(
                                title = item.title,
                                url = item.url,
                                isAdd = false,
                                editing = editing,
                                onClick = { if (editing) editTarget = item else onOpenUrl(item.url) },
                                onLongClick = { editTarget = item }
                            )
                        }
                    }
                }
            }

            if (topSites.isNotEmpty()) {
                item { Text("Frequently Visited", style = MaterialTheme.typography.titleMedium) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topSites.take(6).forEach { site ->
                            SiteRow(
                                title = site.title.ifBlank { UrlUtils.displayUrl(site.url) },
                                url = site.url,
                                trailing = "${site.visits} visit" + if (site.visits == 1) "" else "s",
                                onClick = { onOpenUrl(site.url) }
                            )
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(NexaIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recently Visited", style = MaterialTheme.typography.titleMedium)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recent.take(6).forEach { entry ->
                            SiteRow(
                                title = entry.title.ifBlank { UrlUtils.displayUrl(entry.url) },
                                url = entry.url,
                                trailing = null,
                                onClick = { onOpenUrl(entry.url) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        ShortcutDialog(
            initialTitle = "",
            initialUrl = "",
            title = "Add Shortcut",
            onDismiss = { addDialog = false },
            onConfirm = { t, u -> onAddShortcut(t, u); addDialog = false }
        )
    }

    editTarget?.let { target ->
        val index = shortcuts.indexOfFirst { it.id == target.id }
        ShortcutEditDialog(
            shortcut = target,
            canMoveLeft = index > 0,
            canMoveRight = index in 0 until shortcuts.size - 1,
            onDismiss = { editTarget = null },
            onRename = { t, u ->
                val updated = target.copy(title = t, url = u)
                onReorderShortcuts(shortcuts.toMutableList().also { it[index] = updated })
                editTarget = null
            },
            onMoveLeft = {
                if (index > 0) {
                    val list = shortcuts.toMutableList()
                    val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                    onReorderShortcuts(list)
                }
                editTarget = null
            },
            onMoveRight = {
                if (index in 0 until shortcuts.size - 1) {
                    val list = shortcuts.toMutableList()
                    val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                    onReorderShortcuts(list)
                }
                editTarget = null
            },
            onDelete = { onDeleteShortcut(target); editTarget = null }
        )
    }
}

@Composable
private fun ShortcutTile(
    title: String,
    url: String,
    isAdd: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(4.dp)
        ) {
            GlassSurface(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.size(56.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "Add shortcut", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        CachedFavicon(null, url, size = 30.dp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (editing && !isAdd) {
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = onLongClick, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Edit shortcut", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SiteRow(title: String, url: String, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CachedFavicon(null, url, size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                UrlUtils.displayUrl(url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShortcutDialog(
    initialTitle: String,
    initialUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var t by remember { mutableStateOf(initialTitle) }
    var u by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = u,
                    onValueChange = { u = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (u.isNotBlank()) onConfirm(t.ifBlank { u }, u) }, enabled = u.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ShortcutEditDialog(
    shortcut: Shortcut,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit
) {
    var t by remember { mutableStateOf(shortcut.title) }
    var u by remember { mutableStateOf(shortcut.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Shortcut") },
        text = {
            Column {
                OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onMoveLeft, enabled = canMoveLeft) { Text("Move left") }
                    TextButton(onClick = onMoveRight, enabled = canMoveRight) { Text("Move right") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRename(t.ifBlank { u }, u) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } }
    )
}
