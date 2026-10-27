package com.nexabrowser.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.ui.theme.BarSurface
import com.nexabrowser.app.ui.theme.NexaIcons

/**
 * The navigation pill that sits under the address bar: back, forward, share,
 * bookmarks and tabs, all in the accent blue on glass, as in the reference.
 */
@Composable
fun LiquidGlassNav(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    tabCount: Int,
    style: AddressBarStyle,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onBookmarks: () -> Unit,
    onTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    BarSurface(
        style = style,
        shape = RoundedCornerShape(30.dp),
        elevation = 10.dp,
        modifier = modifier.fillMaxWidth().height(54.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavButton(
                enabled = canGoBack,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavButton(
                enabled = canGoForward,
                onClick = onForward,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavButton(enabled = true, onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavButton(enabled = true, onClick = onBookmarks, modifier = Modifier.weight(1f)) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else NexaIcons.StarBorder,
                    contentDescription = "Bookmarks",
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            NavButton(enabled = true, onClick = onTabs, modifier = Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        NexaIcons.Tab,
                        contentDescription = "Tabs",
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    if (tabCount > 0) {
                        Text(
                            text = tabCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = accent,
                            modifier = Modifier.offset(y = 13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayerAlpha(if (enabled) 1f else 0.30f),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

private fun Modifier.graphicsLayerAlpha(a: Float): Modifier = this.graphicsLayer { alpha = a }
