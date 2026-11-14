package com.nexabrowser.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexabrowser.app.domain.model.AccentPreset
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.domain.model.CornerStyle
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.domain.model.SearchEngine
import com.nexabrowser.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

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
    val showDownloadNotifications: Boolean = true,
    val addressBarStyle: AddressBarStyle = AddressBarStyle.LIQUID_GLASS,

    // Extensions
    val adBlockEnabled: Boolean = true,
    val forceHttpsEnabled: Boolean = true,
    val darkPagesEnabled: Boolean = false,
    val dataSaverEnabled: Boolean = false,
    val cleanLinksEnabled: Boolean = true,
    val customCssEnabled: Boolean = false,
    val customCss: String = "",
    val accent: AccentPreset = AccentPreset.BLUE,
    val corners: CornerStyle = CornerStyle.ROUNDED
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
        val BAR_STYLE = stringPreferencesKey("address_bar_style")
        val AD_BLOCK = booleanPreferencesKey("ext_ad_block")
        val FORCE_HTTPS = booleanPreferencesKey("ext_force_https")
        val DARK_PAGES = booleanPreferencesKey("ext_dark_pages")
        val DATA_SAVER = booleanPreferencesKey("ext_data_saver")
        val CLEAN_LINKS = booleanPreferencesKey("ext_clean_links")
        val CUSTOM_CSS_ON = booleanPreferencesKey("ext_custom_css_on")
        val CUSTOM_CSS = stringPreferencesKey("ext_custom_css")
        val ACCENT = stringPreferencesKey("ext_accent")
        val CORNERS = stringPreferencesKey("ext_corners")
    }

    /**
     * The stored preferences, or the defaults when the file cannot be read.
     *
     * DataStore throws [IOException] out of `data` when the preferences file is
     * unreadable or corrupt — a write that was cut short by process death, a
     * full data partition, a file damaged by a storage fault. Nothing caught
     * that here, and the consequences were not local: the flow is collected by
     * `BrowserViewModel` through `stateIn(viewModelScope, SharingStarted.Eagerly,
     * …)`, so the exception cancelled that collector and then the whole
     * viewModelScope — and `restoreSession()` reads the same flow through
     * [current], so the session-restore coroutine died with it. The browser came
     * up on defaults with no restored tabs and no way to change a preference,
     * for the rest of the process's life.
     *
     * The documented fallback is applied instead: a corrupt file is reported and
     * replaced by empty preferences, so every setting takes its default and the
     * next preference write recreates the file. The user keeps a working browser
     * rather than a dead one, and the app never crashes on a bad settings file.
     */
    val settings: Flow<BrowserSettings> = context.dataStore.data
        .catch { failure ->
            if (failure !is IOException) throw failure
            Log.w(TAG, "Settings unreadable; falling back to defaults", failure)
            emit(emptyPreferences())
        }
        .map { p ->
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
            showDownloadNotifications = p[Keys.DL_NOTIFY] ?: true,
            addressBarStyle = AddressBarStyle.fromId(p[Keys.BAR_STYLE]),
            adBlockEnabled = p[Keys.AD_BLOCK] ?: true,
            forceHttpsEnabled = p[Keys.FORCE_HTTPS] ?: true,
            darkPagesEnabled = p[Keys.DARK_PAGES] ?: false,
            dataSaverEnabled = p[Keys.DATA_SAVER] ?: false,
            cleanLinksEnabled = p[Keys.CLEAN_LINKS] ?: true,
            customCssEnabled = p[Keys.CUSTOM_CSS_ON] ?: false,
            customCss = p[Keys.CUSTOM_CSS] ?: "",
            accent = AccentPreset.fromId(p[Keys.ACCENT]),
            corners = CornerStyle.fromId(p[Keys.CORNERS])
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
    suspend fun setAddressBarStyle(style: AddressBarStyle) = edit { it[Keys.BAR_STYLE] = style.name }

    suspend fun setAdBlockEnabled(on: Boolean) = edit { it[Keys.AD_BLOCK] = on }
    suspend fun setForceHttpsEnabled(on: Boolean) = edit { it[Keys.FORCE_HTTPS] = on }
    suspend fun setDarkPagesEnabled(on: Boolean) = edit { it[Keys.DARK_PAGES] = on }
    suspend fun setDataSaverEnabled(on: Boolean) = edit { it[Keys.DATA_SAVER] = on }
    suspend fun setCleanLinksEnabled(on: Boolean) = edit { it[Keys.CLEAN_LINKS] = on }
    suspend fun setCustomCssEnabled(on: Boolean) = edit { it[Keys.CUSTOM_CSS_ON] = on }
    suspend fun setCustomCss(css: String) = edit { it[Keys.CUSTOM_CSS] = css }
    suspend fun setAccent(a: AccentPreset) = edit { it[Keys.ACCENT] = a.name }
    suspend fun setCorners(c: CornerStyle) = edit { it[Keys.CORNERS] = c.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        const val TAG = "NexaSettings"
    }
}
