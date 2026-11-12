package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.HistoryEntry
import com.nexabrowser.app.ui.HistoryRange
import com.nexabrowser.app.ui.components.CachedFavicon
import com.nexabrowser.app.ui.components.EmptyState
import com.nexabrowser.app.ui.theme.NexaIcons
import com.nexabrowser.app.util.DateLabels
import com.nexabrowser.app.util.HistoryGrouping
import com.nexabrowser.app.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * How long the search box waits for typing to pause before the history is
 * scanned again. Short enough that the list still feels like it is following
 * the keyboard, long enough to fold a burst of keystrokes into one pass.
 */
private const val HISTORY_SEARCH_DEBOUNCE_MS = 140L

/**
 * Browsing history, grouped by day and searchable.
 *
 * Entries are removed by swiping a row to the right; there is no delete button
 * on the row. The toolbar still offers bulk clearing by time range.
 */
@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClear: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }

    // What the history is filtered by, which trails what the user has typed.
    //
    // The pass below scans every stored row, so binding it to the text field
    // meant a full scan of the history per *character*: on a 20,000-visit
    // history that is a few hundred thousand case-insensitive comparisons on the
    // main thread for each key, i.e. a dropped frame in the middle of the one
    // interaction where the user is watching the list react. The field itself is
    // driven by [query] and still updates on every key, so typing is as
    // responsive as it was; the list catches up once typing pauses. A cleared
    // field applies at once, so emptying the box never leaves stale results on
    // screen.
    var filterQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            filterQuery = ""
            return@LaunchedEffect
        }
        delay(HISTORY_SEARCH_DEBOUNCE_MS)
        filterQuery = query
    }

    // Filtering and day grouping in one remembered pass. It used to be two:
    // a `filter` that copied the list, then a `groupBy` whose key lambda ran
    // DateLabels.dayLabel for every row — 20,000 Calendar writes and, past the
    // last two days, 20,000 SimpleDateFormat calls on the main thread, redone on
    // every keystroke in the search box. HistoryGrouping walks the (already
    // sorted) list once, formats a label only when the day changes, and returns
    // the sections the list draws, so a keystroke costs a scan and a handful of
    // formats. See HistoryGrouping for the details and the order fallback.
    //
    // One "now" for the whole pass, so a list drawn across midnight cannot put
    // two entries of the same day under different headings.
    //
    // The pass itself runs off the frame thread. It walks every stored row, and
    // it is re-run whenever the history changes — which, with this screen open,
    // is every completed page load in another tab — and again on every debounced
    // keystroke. On a 20,000-visit history that is a scan and a section build
    // that used to happen inside composition, i.e. between the frame that
    // arrived and the frame that could be drawn. The first result is still built
    // inline so the screen draws its list immediately instead of flashing the
    // empty state; every later pass lands a moment after the frame, which is
    // exactly when the previous list is still correct to look at.
    var grouped by remember {
        mutableStateOf(HistoryGrouping.build(history, filterQuery, System.currentTimeMillis()))
    }
    LaunchedEffect(history, filterQuery) {
        grouped = withContext(Dispatchers.Default) {
            HistoryGrouping.build(history, filterQuery, System.currentTimeMillis())
        }
    }
    val filteredCount = remember(grouped) { HistoryGrouping.count(grouped) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search history") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$filteredCount entr" + if (filteredCount == 1) "y" else "ies",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            IconButton(onClick = { clearDialog = true }) {
                Icon(NexaIcons.DeleteSweep, contentDescription = "Clear history")
            }
        }

        if (filteredCount == 0) {
            EmptyState(
                title = if (filterQuery.isBlank()) "No history yet" else "No matches",
                subtitle = if (filterQuery.isBlank()) {
                    "Pages you visit will appear here. Swipe a row right to delete it. Private tabs are never recorded."
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                grouped.forEach { section ->
                    val day = section.label
                    val entries = section.entries
                    item(key = "header-$day") {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        SwipeToDeleteRow(
                            entry = entry,
                            onOpen = { onOpen(entry.url) },
                            onDelete = { onDelete(entry) }
                        )
                    }
                }
            }
            Text(
                "Swipe a row right to delete it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
            )
            }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear History") },
            text = {
                Column {
                    Text("Choose what to remove:")
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        HistoryRange.LAST_HOUR to "The last hour",
                        HistoryRange.TODAY to "Today",
                        HistoryRange.LAST_WEEK to "The last week",
                        HistoryRange.ALL to "All history"
                    ).forEach { (range, label) ->
                        TextButton(onClick = { onClear(range); clearDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } }
        )
    }
}

/** One history row: swipe right to delete, tap to open. */
@Composable
private fun SwipeToDeleteRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachedFavicon(entry.faviconUrl, entry.url, size = 28.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    UrlUtils.displayUrl(entry.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                DateLabels.timeLabel(entry.visitedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
