package com.nexabrowser.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.BuildConfig
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.domain.model.SearchEngine
import com.nexabrowser.app.domain.model.SitePermissionRecord
import com.nexabrowser.app.domain.model.ThemeMode
import com.nexabrowser.app.ui.components.ActionRow
import com.nexabrowser.app.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    permissions: List<SitePermissionRecord>,
    onSetSearchEngine: (SearchEngine) -> Unit,
    onSetCustomSearch: (String) -> Unit,
    onSetHomepage: (String) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetNewTab: (NewTabBehavior) -> Unit,
    onSetTextZoom: (Int) -> Unit,
    onSetDesktopByDefault: (Boolean) -> Unit,
    onSetJavaScript: (Boolean) -> Unit,
    onSetPopups: (Boolean) -> Unit,
    onSetCookies: (Boolean) -> Unit,
    onSetSaveHistory: (Boolean) -> Unit,
    onSetDoNotTrack: (Boolean) -> Unit,
    onSetSessionRestore: (Boolean) -> Unit,
    onSetShowClock: (Boolean) -> Unit,
    onSetBackground: (String) -> Unit,
    onSetAddressBarStyle: (AddressBarStyle) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit,
    onClearCookies: () -> Unit,
    onClearWebsiteData: () -> Unit,
    onClearPermissions: () -> Unit,
    onRevokePermission: (SitePermissionRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var choiceDialog by remember { mutableStateOf<ChoiceDialogConfig?>(null) }
    var confirmAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var homepageDraft by remember(settings.homepageUrl) { mutableStateOf(settings.homepageUrl) }
    var customSearchDraft by remember(settings.customSearchUrl) { mutableStateOf(settings.customSearchUrl) }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {

        item { SectionHeader("Search") }
        item {
            ActionRow(
                title = "Search Engine",
                subtitle = "Used when you type a phrase instead of an address",
                value = settings.searchEngine.displayName
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "Search Engine",
                    options = SearchEngine.entries.filter { it != SearchEngine.CUSTOM }.map { it.id to it.displayName },
                    selected = settings.searchEngineId
                ) { id -> onSetSearchEngine(SearchEngine.fromId(id)) }
            }
        }
        item {
            ActionRow(
                title = "Use a Custom Search Engine",
                subtitle = "Switch to a URL of your own",
                value = if (settings.searchEngineId == SearchEngine.CUSTOM.id) "Custom" else "Off"
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "Custom search engine",
                    options = listOf("custom" to "Use a custom URL", "google" to "Back to Google"),
                    selected = if (settings.searchEngineId == SearchEngine.CUSTOM.id) "custom" else "google"
                ) { id -> onSetSearchEngine(SearchEngine.fromId(id)) }
            }
        }
        item {
            OutlinedTextField(
                value = customSearchDraft,
                onValueChange = { customSearchDraft = it },
                label = { Text("Custom search URL (must contain %s)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        item { ActionRow(title = "Save Custom Search URL") { onSetCustomSearch(customSearchDraft) } }

        item { DividerLine() }
        item { SectionHeader("Start Page") }
        item {
            OutlinedTextField(
                value = homepageDraft,
                onValueChange = { homepageDraft = it },
                label = { Text("Homepage") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        item { ActionRow(title = "Save Homepage", subtitle = "Leave blank to use the start page") { onSetHomepage(homepageDraft) } }
        item {
            ActionRow(
                title = "New Tab Opens",
                value = when (settings.newTabBehavior) {
                    NewTabBehavior.START_PAGE -> "Start Page"
                    NewTabBehavior.HOMEPAGE -> "Homepage"
                    NewTabBehavior.BLANK -> "Blank"
                }
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "New Tab Opens",
                    options = listOf(
                        NewTabBehavior.START_PAGE.name to "Start Page",
                        NewTabBehavior.HOMEPAGE.name to "Homepage",
                        NewTabBehavior.BLANK.name to "Blank"
                    ),
                    selected = settings.newTabBehavior.name
                ) { id -> onSetNewTab(NewTabBehavior.fromId(id)) }
            }
        }
        item {
            SwitchRow("Show Clock and Date", "Displayed at the top of the start page", settings.showClockOnStartPage, onCheckedChange = onSetShowClock)
        }
        item {
            ActionRow(
                title = "Start Page Background",
                value = settings.startPageBackground.ifBlank { "default" }
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "Start Page Background",
                    options = StartBackgrounds.options.map { it to it.replaceFirstChar { c -> c.uppercase() } },
                    selected = settings.startPageBackground.ifBlank { "default" }
                ) { id -> onSetBackground(if (id == "default") "" else id) }
            }
        }

        item { DividerLine() }
        item { SectionHeader("Appearance") }
        item {
            ActionRow(
                title = "Address Bar Style",
                subtitle = "Solid gives the bar a black-on-white, fully opaque look",
                value = when (settings.addressBarStyle) {
                    AddressBarStyle.LIGHT -> "Solid light"
                    AddressBarStyle.AUTOMATIC -> "Solid themed"
                    AddressBarStyle.GLASS -> "Liquid Glass"
                }
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "Address Bar Style",
                    options = listOf(
                        AddressBarStyle.LIGHT.name to "Solid light (black text on white)",
                        AddressBarStyle.AUTOMATIC.name to "Solid, follows the theme",
                        AddressBarStyle.GLASS.name to "Liquid Glass (gen 2, translucent)"
                    ),
                    selected = settings.addressBarStyle.name
                ) { id -> onSetAddressBarStyle(AddressBarStyle.fromId(id)) }
            }
        }
        item {
            ActionRow(
                title = "Theme",
                value = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "Automatic"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            ) {
                choiceDialog = ChoiceDialogConfig(
                    title = "Theme",
                    options = listOf(
                        ThemeMode.SYSTEM.name to "Automatic",
                        ThemeMode.LIGHT.name to "Light",
                        ThemeMode.DARK.name to "Dark"
                    ),
                    selected = settings.themeMode.name
                ) { id -> onSetTheme(ThemeMode.fromId(id)) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row {
                    Text("Text Size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${settings.textZoom}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.textZoom.toFloat(),
                    onValueChange = { onSetTextZoom(it.toInt()) },
                    valueRange = 50f..200f,
                    steps = 14
                )
            }
        }

        item { DividerLine() }
        item { SectionHeader("Browsing") }
        item { SwitchRow("Request Desktop Site by Default", "New pages load the desktop layout", settings.desktopSiteByDefault, onCheckedChange = onSetDesktopByDefault) }
        item { SwitchRow("JavaScript", "Required by most modern sites", settings.javaScriptEnabled, onCheckedChange = onSetJavaScript) }
        item { SwitchRow("Allow Pop-ups", "Let pages open new windows automatically", settings.popupsEnabled, onCheckedChange = onSetPopups) }
        item { SwitchRow("Accept Cookies", "Turning this off breaks most sign-ins", settings.cookiesEnabled, onCheckedChange = onSetCookies) }
        item { SwitchRow("Save Browsing History", "Private tabs are never recorded", settings.saveHistoryEnabled, onCheckedChange = onSetSaveHistory) }
        item { SwitchRow("Do Not Track", "Ask sites not to track you through page scripts", settings.doNotTrackEnabled, onCheckedChange = onSetDoNotTrack) }
        item { SwitchRow("Restore Tabs on Start", "Reopen your regular tabs after a restart", settings.sessionRestoreEnabled, onCheckedChange = onSetSessionRestore) }

        item { DividerLine() }
        item { SectionHeader("Downloads") }
        item { SwitchRow("Download Over Wi-Fi Only", "Mobile data will not be used for downloads", settings.downloadOverWifiOnly, onCheckedChange = onSetWifiOnly) }
        item { SwitchRow("Show Download Notifications", "A notification appears while a file downloads", settings.showDownloadNotifications, onCheckedChange = onSetNotifications) }

        item { DividerLine() }
        item { SectionHeader("Privacy and Data") }
        item { ActionRow("Clear History", "Removes every recorded visit") { confirmAction = "Clear history" to onClearHistory } }
        item { ActionRow("Clear Cache", "Frees space used by cached pages") { confirmAction = "Clear cache" to onClearCache } }
        item { ActionRow("Clear Cookies", "Signs you out of websites") { confirmAction = "Clear cookies" to onClearCookies } }
        item { ActionRow("Clear Website Data", "Local storage, databases and cached icons") { confirmAction = "Clear website data" to onClearWebsiteData } }
        item { ActionRow("Reset Site Permissions", "Forgets every allow/deny decision") { confirmAction = "Reset site permissions" to onClearPermissions } }

        item { DividerLine() }
        item { SectionHeader("Website Permissions") }
        if (permissions.isEmpty()) {
            item {
                Text(
                    "No site has been granted camera, microphone or location access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        } else {
            items(permissions.size) { index ->
                val record = permissions[index]
                ActionRow(
                    title = record.origin,
                    subtitle = record.permission.name.lowercase().replaceFirstChar { it.uppercase() } +
                        " — " + record.decision.name.lowercase(),
                    value = "Revoke"
                ) { onRevokePermission(record) }
            }
        }

        item { DividerLine() }
        item { SectionHeader("About") }
        item { ActionRow("Nexa Browser", "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") {} }
        item {
            Text(
                "Nexa renders pages with the system Android WebView (Chromium). Pages that need features the " +
                    "installed WebView does not support may behave differently from other browsers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }

    choiceDialog?.let { config ->
        AlertDialog(
            onDismissRequest = { choiceDialog = null },
            title = { Text(config.title) },
            text = {
                Column {
                    config.options.forEach { (id, label) ->
                        TextButton(
                            onClick = { config.onSelect(id); choiceDialog = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (config.selected == id) "\u2713  " else "      ") + label,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choiceDialog = null }) { Text("Cancel") } }
        )
    }

    confirmAction?.let { (label, action) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(label) },
            text = { Text("This cannot be undone. Continue?") },
            confirmButton = { TextButton(onClick = { action(); confirmAction = null }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Cancel") } }
        )
    }
}

private data class ChoiceDialogConfig(
    val title: String,
    val options: List<Pair<String, String>>,
    val selected: String,
    val onSelect: (String) -> Unit
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun DividerLine() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}
