package com.nexabrowser.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexabrowser.app.util.UrlUtils
import com.nexabrowser.app.util.FaviconPathCache
import java.io.File

/**
 * The avatar palette. It used to be rebuilt inside [accentFor], i.e. a new
 * eight-element List and eight Color objects for every row drawn — on every
 * recomposition of the history, bookmark, reading-list and shortcut lists.
 */
private val AVATAR_PALETTE = listOf(
    Color(0xFF2F6BFF), Color(0xFF12B5A5), Color(0xFFE5484D),
    Color(0xFFE0A106), Color(0xFF8B5CF6), Color(0xFF0EA5E9),
    Color(0xFFEC4899), Color(0xFF22C55E)
)

/** Deterministic accent colour for a host, used for letter avatars. */
private fun accentFor(host: String): Color {
    if (host.isEmpty()) return AVATAR_PALETTE[0]
    // Masked rather than negated: abs(Int.MIN_VALUE) is still negative, which
    // would be a negative index the moment the palette has a size that does not
    // divide 2^31 (it divides it today, which is luck, not correctness).
    return AVATAR_PALETTE[(host.hashCode() and 0x7FFFFFFF) % AVATAR_PALETTE.size]
}

/** A round avatar showing the site's first letter, used when no icon exists. */
@Composable
fun LetterAvatar(url: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    // UrlUtils.hostOf() parses the URL with java.net.URI, and it used to run
    // twice per row — once here and once inside accentFor() — on every
    // recomposition of every list that shows an avatar. Both the parse and the
    // colour are remembered per URL now; neither depends on anything else.
    val host = remember(url) { UrlUtils.hostOf(url).ifBlank { url } }
    val letter = remember(host) {
        host.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }
    val accent = remember(host) { accentFor(host) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(accent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Favicon from a live bitmap (tabs, address bar). */
@Composable
fun BitmapFavicon(bitmap: Bitmap?, url: String, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    // asImageBitmap() hands back a fresh wrapper on every call, and this
    // composable sits in the tab strip and the address bar — both of which
    // recompose on every progress tick, title change and navigation callback of
    // a page load. Remembering it per bitmap keeps those frames allocation-free
    // without changing what is drawn.
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(RoundedCornerShape(6.dp))
        )
    } else {
        LetterAvatar(url, size, modifier)
    }
}

/** Favicon from a cached file path (history, bookmarks, shortcuts). */
@Composable
fun CachedFavicon(path: String?, url: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    // Answered from memory after the first lookup: a stat() per row per
    // recomposition was happening on the main thread while the list scrolled.
    // Remembered per path as well: this runs for every row of the history,
    // bookmark and reading-list lists, and a new File per recomposition was
    // allocation (and a new Coil cache key lookup) on every scrolled frame.
    val file = remember(path) { path?.takeIf { FaviconPathCache.exists(it) }?.let { File(it) } }
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(RoundedCornerShape(7.dp))
        )
    } else {
        LetterAvatar(url, size, modifier)
    }
}

/** Shared empty-state block for the list screens. */
@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A consistent header for the secondary screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

/** A settings row with a trailing switch. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A settings row that opens a value picker. */
@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
        }
    }
}

/** A small pill used for counters and status chips. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
