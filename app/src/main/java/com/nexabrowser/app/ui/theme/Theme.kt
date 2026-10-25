package com.nexabrowser.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import com.nexabrowser.app.domain.model.AccentPreset
import com.nexabrowser.app.domain.model.CornerStyle
import com.nexabrowser.app.domain.model.ThemeMode

/** Extra colours the Material scheme does not carry. */
data class NexaExtraColors(
    val glassTint: Color,
    val glassBorder: Color,
    val isDark: Boolean,
    val isPrivate: Boolean,
    val privateSurface: Color,
    val secure: Color,
    val insecure: Color
)

val LocalNexaColors = staticCompositionLocalOf {
    NexaExtraColors(
        glassTint = GlassLightTint,
        glassBorder = GlassLightBorder,
        isDark = false,
        isPrivate = false,
        privateSurface = NexaPrivateSurface,
        secure = NexaSuccess,
        insecure = NexaWarning
    )
}

private val LightScheme = lightColorScheme(
    primary = NexaBlue,
    onPrimary = Color.White,
    secondary = NexaTeal,
    onSecondary = Color.White,
    background = NexaPaper,
    onBackground = NexaInk,
    surface = NexaPaperElevated,
    onSurface = NexaInk,
    surfaceVariant = Color(0xFFE7E9EE),
    onSurfaceVariant = NexaInkSoft,
    error = NexaDanger,
    outline = Color(0xFFC7CBD3)
)

private val DarkScheme = darkColorScheme(
    primary = NexaBlueDark,
    onPrimary = Color(0xFF08122B),
    secondary = NexaTeal,
    onSecondary = Color(0xFF04231F),
    background = NexaNightBg,
    onBackground = Color(0xFFE9EAED),
    surface = NexaNightSurface,
    onSurface = Color(0xFFE9EAED),
    surfaceVariant = NexaNightElevated,
    onSurfaceVariant = Color(0xFFB9BCC3),
    error = NexaDanger,
    outline = Color(0xFF3A3D44)
)

@Composable
fun NexaTheme(
    themeMode: ThemeMode,
    isPrivate: Boolean = false,
    accent: AccentPreset = AccentPreset.BLUE,
    corners: CornerStyle = CornerStyle.ROUNDED,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // The Theme extension drives the accent directly, so the palette is
    // predictable across devices instead of following the wallpaper.
    val accentColor = if (isPrivate) NexaPrivateAccent else Color(accent.argb)
    val onAccent = if (accentColor.luminance() > 0.55f) Color(0xFF10131A) else Color.White

    val base = when {
        isPrivate -> DarkScheme.copy(background = NexaPrivateBg, surface = NexaPrivateSurface)
        dark -> DarkScheme
        else -> LightScheme
    }
    val scheme = base.copy(primary = accentColor, onPrimary = onAccent)

    val extras = NexaExtraColors(
        glassTint = when {
            isPrivate -> GlassPrivateTint
            dark -> GlassDarkTint
            else -> GlassLightTint
        },
        glassBorder = if (dark || isPrivate) GlassDarkBorder else GlassLightBorder,
        isDark = dark || isPrivate,
        isPrivate = isPrivate,
        privateSurface = NexaPrivateSurface,
        secure = NexaSuccess,
        insecure = NexaWarning
    )

    CompositionLocalProvider(LocalNexaColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NexaTypography,
            shapes = nexaShapes(corners.scale),
            content = content
        )
    }
}
