package com.nexabrowser.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.domain.model.SearchEngine
import com.nexabrowser.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Immutable snapshot of every user preference. Defaults are chosen to be safe. */
data class BrowserSettings(
    val searchEngineId: String = SearchEngine.GOOGLE.id,
    val customSearchUrl: String = "",
    val homepageUrl: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val newTabBehavior: NewTabBehavior = NewTabBehavior.START_PAGE,
    val textZoom: Int = 100,
    val desktopSiteByDefault: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val popupsEnabled: Boolean = false,
    val cookiesEnabled: Boolean = true,
    val saveHistoryEnabled: Boolean = true,
    val doNotTrackEnabled: Boolean = true,
    val sessionRestoreEnabled: Boolean = true,
    val showClockOnStartPage: Boolean = true,
    val startPageBackground: String = "",
    val downloadOverWifiOnly: Boolean = false,
    val showDownloadNotifications: Boolean = true
) {
    val searchEngine: SearchEngine get() = SearchEngine.fromId(searchEngineId)

    /** Resolves the active query template, honouring a custom engine. */
    val activeQueryTemplate: String
        get() = if (searchEngine == SearchEngine.CUSTOM && customSearchUrl.isNotBlank()) {
            if (customSearchUrl.contains("%s")) customSearchUrl else customSearchUrl + "%s"
        } else {
            searchEngine.queryTemplate.ifBlank { SearchEngine.GOOGLE.queryTemplate }
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexa_settings")

/**
 * Persists [BrowserSettings] with Jetpack DataStore. Reads are a cold Flow so
 * the UI stays reactive; writes are suspending and atomic.
 */
class DataStoreManager(private val context: Context) {

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH = stringPreferencesKey("custom_search_url")
        val HOMEPAGE = stringPreferencesKey("homepage_url")
        val THEME = stringPreferencesKey("theme_mode")
        val NEW_TAB = stringPreferencesKey("new_tab_behavior")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val DESKTOP_BY_DEFAULT = booleanPreferencesKey("desktop_by_default")
        val JS_ENABLED = booleanPreferencesKey("js_enabled")
        val POPUPS = booleanPreferencesKey("popups_enabled")
        val COOKIES = booleanPreferencesKey("cookies_enabled")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val DNT = booleanPreferencesKey("do_not_track")
        val SESSION_RESTORE = booleanPreferencesKey("session_restore")
        val CLOCK = booleanPreferencesKey("start_clock")
        val START_BG = stringPreferencesKey("start_background")
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val DL_NOTIFY = booleanPreferencesKey("download_notifications")
    }

    val settings: Flow<BrowserSettings> = context.dataStore.data.map { p ->
        BrowserSettings(
            searchEngineId = p[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.id,
            customSearchUrl = p[Keys.CUSTOM_SEARCH] ?: "",
            homepageUrl = p[Keys.HOMEPAGE] ?: "",
            themeMode = ThemeMode.fromId(p[Keys.THEME]),
            newTabBehavior = NewTabBehavior.fromId(p[Keys.NEW_TAB]),
            textZoom = p[Keys.TEXT_ZOOM] ?: 100,
            desktopSiteByDefault = p[Keys.DESKTOP_BY_DEFAULT] ?: false,
            javaScriptEnabled = p[Keys.JS_ENABLED] ?: true,
            popupsEnabled = p[Keys.POPUPS] ?: false,
            cookiesEnabled = p[Keys.COOKIES] ?: true,
            saveHistoryEnabled = p[Keys.SAVE_HISTORY] ?: true,
            doNotTrackEnabled = p[Keys.DNT] ?: true,
            sessionRestoreEnabled = p[Keys.SESSION_RESTORE] ?: true,
            showClockOnStartPage = p[Keys.CLOCK] ?: true,
            startPageBackground = p[Keys.START_BG] ?: "",
            downloadOverWifiOnly = p[Keys.WIFI_ONLY] ?: false,
            showDownloadNotifications = p[Keys.DL_NOTIFY] ?: true
        )
    }

    suspend fun current(): BrowserSettings = settings.first()

    suspend fun setSearchEngine(id: String) = edit { it[Keys.SEARCH_ENGINE] = id }
    suspend fun setCustomSearchUrl(url: String) = edit { it[Keys.CUSTOM_SEARCH] = url.trim() }
    suspend fun setHomepage(url: String) = edit { it[Keys.HOMEPAGE] = url.trim() }
    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setNewTabBehavior(b: NewTabBehavior) = edit { it[Keys.NEW_TAB] = b.name }
    suspend fun setTextZoom(zoom: Int) = edit { it[Keys.TEXT_ZOOM] = zoom.coerceIn(50, 200) }
    suspend fun setDesktopByDefault(enabled: Boolean) = edit { it[Keys.DESKTOP_BY_DEFAULT] = enabled }
    suspend fun setJavaScriptEnabled(enabled: Boolean) = edit { it[Keys.JS_ENABLED] = enabled }
    suspend fun setPopupsEnabled(enabled: Boolean) = edit { it[Keys.POPUPS] = enabled }
    suspend fun setCookiesEnabled(enabled: Boolean) = edit { it[Keys.COOKIES] = enabled }
    suspend fun setSaveHistory(enabled: Boolean) = edit { it[Keys.SAVE_HISTORY] = enabled }
    suspend fun setDoNotTrack(enabled: Boolean) = edit { it[Keys.DNT] = enabled }
    suspend fun setSessionRestore(enabled: Boolean) = edit { it[Keys.SESSION_RESTORE] = enabled }
    suspend fun setShowClock(enabled: Boolean) = edit { it[Keys.CLOCK] = enabled }
    suspend fun setStartBackground(value: String) = edit { it[Keys.START_BG] = value }
    suspend fun setDownloadWifiOnly(enabled: Boolean) = edit { it[Keys.WIFI_ONLY] = enabled }
    suspend fun setShowDownloadNotifications(enabled: Boolean) = edit { it[Keys.DL_NOTIFY] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
