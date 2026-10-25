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
import java.io.File

/** Deterministic accent colour derived from a URL, used for letter avatars. */
private fun accentFor(url: String): Color {
    val host = UrlUtils.hostOf(url).ifBlank { url }
    val palette = listOf(
        Color(0xFF2F6BFF), Color(0xFF12B5A5), Color(0xFFE5484D),
        Color(0xFFE0A106), Color(0xFF8B5CF6), Color(0xFF0EA5E9),
        Color(0xFFEC4899), Color(0xFF22C55E)
    )
    if (host.isEmpty()) return palette[0]
    return palette[(host.hashCode().let { if (it < 0) -it else it }) % palette.size]
}

/** A round avatar showing the site's first letter, used when no icon exists. */
@Composable
fun LetterAvatar(url: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val host = UrlUtils.hostOf(url).ifBlank { url }
    val letter = host.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(accentFor(url)),
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
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
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
    val file = path?.let { File(it) }?.takeIf { it.exists() }
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
