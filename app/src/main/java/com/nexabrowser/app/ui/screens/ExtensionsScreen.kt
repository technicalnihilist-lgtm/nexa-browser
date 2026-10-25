package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.domain.model.AccentPreset
import com.nexabrowser.app.domain.model.CornerStyle
import com.nexabrowser.app.ui.components.SwitchRow

/**
 * The Extensions screen. Every switch here changes real behaviour: the ad
 * blocker and HTTPS upgrade act on the network layer, dark pages and custom CSS
 * are injected into each page, and the Theme Studio drives the app's accent and
 * corner roundness.
 */
@Composable
fun ExtensionsScreen(
    settings: BrowserSettings,
    blockedHosts: Int,
    onAdBlock: (Boolean) -> Unit,
    onForceHttps: (Boolean) -> Unit,
    onDarkPages: (Boolean) -> Unit,
    onDataSaver: (Boolean) -> Unit,
    onCleanLinks: (Boolean) -> Unit,
    onCustomCssEnabled: (Boolean) -> Unit,
    onCustomCss: (String) -> Unit,
    onAccent: (AccentPreset) -> Unit,
    onCorners: (CornerStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var cssDraft by remember(settings.customCss) { mutableStateOf(settings.customCss) }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Header("Content") }

        item {
            SwitchRow(
                title = "Ad & Tracker Blocker",
                subtitle = if (blockedHosts > 0) {
                    "$blockedHosts known ad and tracking hosts are blocked at the network layer"
                } else {
                    "Blocks well-known advertising and tracking hosts"
                },
                checked = settings.adBlockEnabled,
                onCheckedChange = onAdBlock
            )
        }
        item {
            SwitchRow(
                title = "Force HTTPS",
                subtitle = "Upgrades http:// links to https:// (local addresses are left alone)",
                checked = settings.forceHttpsEnabled,
                onCheckedChange = onForceHttps
            )
        }
        item {
            SwitchRow(
                title = "Dark Mode for Websites",
                subtitle = "Inverts pages that have no dark theme of their own",
                checked = settings.darkPagesEnabled,
                onCheckedChange = onDarkPages
            )
        }
        item {
            SwitchRow(
                title = "Data Saver",
                subtitle = "Does not load images — useful on a slow connection",
                checked = settings.dataSaverEnabled,
                onCheckedChange = onDataSaver
            )
        }
        item {
            SwitchRow(
                title = "Clean Links",
                subtitle = "Removes utm_, fbclid, gclid and similar tracking parameters",
                checked = settings.cleanLinksEnabled,
                onCheckedChange = onCleanLinks
            )
        }

        item { Header("Theme Studio") }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Accent", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentPreset.entries.forEach { preset ->
                        val selected = preset == settings.accent
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(preset.argb))
                                .then(
                                    if (selected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onAccent(preset) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    settings.accent.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Corners", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CornerStyle.entries.forEach { style ->
                        val selected = style == settings.corners
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.small
                                )
                                .clickable { onCorners(style) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                style.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item { Header("Custom CSS") }
        item {
            SwitchRow(
                title = "Apply Custom CSS",
                subtitle = "Your stylesheet is injected into every page you open",
                checked = settings.customCssEnabled,
                onCheckedChange = onCustomCssEnabled
            )
        }
        item {
            OutlinedTextField(
                value = cssDraft,
                onValueChange = { cssDraft = it },
                label = { Text("CSS") },
                placeholder = { Text("body { font-family: system-ui; }") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onCustomCss(cssDraft) }) { Text("Save CSS") }
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
    )
}
