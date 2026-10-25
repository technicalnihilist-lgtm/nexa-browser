package com.nexabrowser.app.domain

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.domain.model.SearchEngine
import com.nexabrowser.app.domain.model.ThemeMode
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `unknown engine id falls back to google`() {
        assertThat(SearchEngine.fromId("nope")).isEqualTo(SearchEngine.GOOGLE)
        assertThat(SearchEngine.fromId(null)).isEqualTo(SearchEngine.GOOGLE)
    }

    @Test
    fun `each built-in engine has an https template containing the placeholder`() {
        SearchEngine.entries
            .filter { it != SearchEngine.CUSTOM }
            .forEach { engine ->
                assertThat(engine.queryTemplate).startsWith("https://")
                assertThat(engine.queryTemplate).contains("%s")
            }
    }

    @Test
    fun `default settings use google`() {
        val settings = BrowserSettings()
        assertThat(settings.searchEngine).isEqualTo(SearchEngine.GOOGLE)
        assertThat(settings.activeQueryTemplate).isEqualTo(SearchEngine.GOOGLE.queryTemplate)
    }

    @Test
    fun `custom engine without placeholder still gets one appended`() {
        val settings = BrowserSettings(
            searchEngineId = SearchEngine.CUSTOM.id,
            customSearchUrl = "https://example.org/find?q="
        )
        assertThat(settings.activeQueryTemplate).isEqualTo("https://example.org/find?q=%s")
    }

    @Test
    fun `custom engine with a blank url falls back to google`() {
        val settings = BrowserSettings(searchEngineId = SearchEngine.CUSTOM.id, customSearchUrl = "")
        assertThat(settings.activeQueryTemplate).isEqualTo(SearchEngine.GOOGLE.queryTemplate)
    }

    @Test
    fun `theme and new tab enums parse case-insensitively with safe fallbacks`() {
        assertThat(ThemeMode.fromId("dark")).isEqualTo(ThemeMode.DARK)
        assertThat(ThemeMode.fromId("nonsense")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(NewTabBehavior.fromId("homepage")).isEqualTo(NewTabBehavior.HOMEPAGE)
        assertThat(NewTabBehavior.fromId("nonsense")).isEqualTo(NewTabBehavior.START_PAGE)
    }
}
