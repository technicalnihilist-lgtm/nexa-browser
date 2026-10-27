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
 *  LIQUID_GLASS - an opaque white bar with black text in light mode, and the
 *                 liquid-glass pane in dark mode. This is the default and is
 *                 what the design renders show.
 *  SOLID_LIGHT  - always an opaque white bar with black text.
 *  SOLID_THEME  - always opaque, taking its colour from the active theme.
 */
enum class AddressBarStyle {
    LIQUID_GLASS, SOLID_LIGHT, SOLID_THEME;

    companion object {
        fun fromId(id: String?): AddressBarStyle = when (id?.uppercase()) {
            "LIQUID_GLASS", "GLASS" -> LIQUID_GLASS
            "SOLID_LIGHT", "LIGHT" -> SOLID_LIGHT
            "SOLID_THEME", "AUTOMATIC" -> SOLID_THEME
            else -> LIQUID_GLASS
        }
    }
}

/** Accent choices offered by the Theme extension. */
enum class AccentPreset(val argb: Long, val displayName: String) {
    BLUE(0xFF007AFF, "Nexa Blue"),
    TEAL(0xFF12B5A5, "Teal"),
    VIOLET(0xFF8B5CF6, "Violet"),
    ROSE(0xFFEC4899, "Rose"),
    AMBER(0xFFE0A106, "Amber"),
    GRAPHITE(0xFF6B7280, "Graphite");

    companion object {
        fun fromId(id: String?): AccentPreset =
            entries.firstOrNull { it.name.equals(id, true) } ?: BLUE
    }
}

/** Corner roundness offered by the Theme extension. */
enum class CornerStyle(val displayName: String, val scale: Float) {
    SQUARE("Square", 0.30f),
    ROUNDED("Rounded", 1.0f),
    EXTRA("Extra rounded", 1.65f);

    companion object {
        fun fromId(id: String?): CornerStyle =
            entries.firstOrNull { it.name.equals(id, true) } ?: ROUNDED
    }
}
