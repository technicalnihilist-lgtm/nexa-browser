package com.nexabrowser.app.domain.model

/**
 * Built-in search engines. [queryTemplate] uses "%s" as the placeholder for the
 * percent-encoded query.
 */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    val queryTemplate: String,
    val suggestionsTemplate: String?
) {
    GOOGLE(
        "google",
        "Google",
        "https://www.google.com/search?q=%s",
        "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"
    ),
    BING(
        "bing",
        "Bing",
        "https://www.bing.com/search?q=%s",
        "https://api.bing.com/osjson.aspx?query=%s"
    ),
    DUCKDUCKGO(
        "duckduckgo",
        "DuckDuckGo",
        "https://duckduckgo.com/?q=%s",
        null
    ),
    BRAVE(
        "brave",
        "Brave Search",
        "https://search.brave.com/search?q=%s",
        null
    ),
    CUSTOM(
        "custom",
        "Custom",
        "",
        null
    );

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name.equals(id, true) } ?: SYSTEM
    }
}

enum class NewTabBehavior {
    START_PAGE, HOMEPAGE, BLANK;

    companion object {
        fun fromId(id: String?): NewTabBehavior = entries.firstOrNull { it.name.equals(id, true) } ?: START_PAGE
    }
}

/**
 * How the address bar and toolbar are drawn.
 *
 *  LIGHT     - opaque white surface with black text. Maximum contrast, never
 *              see-through.
 *  AUTOMATIC - opaque surface that follows the theme (dark bar in dark mode).
 *  GLASS     - the liquid-glass pane. This is the default: the chrome strip
 *              behind the bars keeps it readable.
 */
enum class AddressBarStyle {
    LIGHT, AUTOMATIC, GLASS;

    companion object {
        fun fromId(id: String?): AddressBarStyle =
            entries.firstOrNull { it.name.equals(id, true) } ?: GLASS
    }
}
