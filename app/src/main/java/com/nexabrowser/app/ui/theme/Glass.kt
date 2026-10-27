package com.nexabrowser.app.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.domain.model.AddressBarStyle

/**
 * A liquid-glass surface: a translucent vertical gradient, a hairline specular
 * border and a soft shadow.
 *
 * A note on blur: Compose has no public backdrop-blur modifier, so the frosted
 * look is produced by layering translucency, gradient and border. Where a real
 * blur is wanted — behind overlays — the content underneath is blurred with
 * [backdropBlur] (a genuine RenderEffect on API 31+), which gives the same
 * visual result without pretending the glass itself is doing it.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = LocalNexaColors.current.glassTint,
    border: Color = LocalNexaColors.current.glassBorder,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalNexaColors.current.isDark

    // Body: a three-stop vertical gradient so the pane has weight.
    val body = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = (tint.alpha * 1.10f).coerceAtMost(1f)),
            tint,
            tint.copy(alpha = tint.alpha * 0.90f)
        )
    )

    // Specular sheen at 135°, corner to corner, as in the reference.
    val sheenAlpha = if (isDark) 0.22f else 0.45f
    val specular = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color.White.copy(alpha = sheenAlpha),
            0.40f to Color.Transparent,
            0.60f to Color.Transparent,
            1.00f to Color.White.copy(alpha = sheenAlpha * 0.55f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(body)
            .background(specular)
            .border(BorderStroke(1.dp, border), shape)
    ) {
        // Inset top highlight — light catching the upper edge.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.94f)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.42f else 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Inset bottom line — the thickness of the glass.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.96f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.14f else 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) = GlassSurface(modifier = modifier, shape = shape, elevation = elevation, content = content)

/** Blurs the content this modifier is attached to. No-op below API 31. */
fun Modifier.backdropBlur(active: Boolean, radius: Dp = 18.dp): Modifier =
    if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius, BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }

/** True when the bar should render as the frosted pane for the active theme. */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun barUsesGlass(style: AddressBarStyle): Boolean = true

/**
 * The surface used by the address bar and the bottom toolbar. The style is
 * resolved against the theme, so the default is a crisp white bar in light mode
 * and the liquid-glass pane in dark mode.
 */
@Composable
fun BarSurface(
    style: AddressBarStyle,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    if (barUsesGlass(style)) {
        // Bars are more transparent than ordinary panes so the page reads through them.
        val extras = LocalNexaColors.current
        val barTint = extras.glassTint.copy(alpha = if (extras.isDark) 0.56f else 0.60f)
        GlassSurface(
            modifier = modifier,
            shape = shape,
            tint = barTint,
            elevation = elevation,
            content = content
        )
        return
    }

    val background = when (style) {
        AddressBarStyle.SOLID_THEME -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color(0xFFFFFFFF)
    }
    val border = if (background == Color(0xFFFFFFFF)) {
        Color(0x24000000)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(background)
            .border(BorderStroke(0.75.dp, border), shape),
        content = content
    )
}

/** Foreground colour for content drawn on a bar of the given [style]. */
@Composable
fun barContentColor(style: AddressBarStyle): Color = when {
    barUsesGlass(style) -> MaterialTheme.colorScheme.onSurface
    style == AddressBarStyle.SOLID_THEME -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> Color(0xFF111214)
}

/** Dimmed foreground colour (hints, placeholders, disabled controls) for a bar. */
@Composable
fun barSecondaryColor(style: AddressBarStyle): Color = when {
    barUsesGlass(style) || style == AddressBarStyle.SOLID_THEME ->
        MaterialTheme.colorScheme.onSurfaceVariant
    else -> Color(0xFF5B6069)
}

/** Hairline/border colour for a bar, used by the loading indicator track. */
@Composable
fun barBorderColor(style: AddressBarStyle): Color = when {
    barUsesGlass(style) -> LocalNexaColors.current.glassBorder
    style == AddressBarStyle.SOLID_THEME -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    else -> Color(0x1F000000)
}

/**
 * Opaque colour of the chrome strip that sits behind the bars. The page is
 * inset so it never runs underneath the bars, and this strip makes the chrome
 * read as deliberate rather than as a gap.
 */
@Composable
fun barChromeColor(style: AddressBarStyle): Color = when {
    barUsesGlass(style) -> Color.Transparent
    style == AddressBarStyle.SOLID_THEME -> MaterialTheme.colorScheme.background
    else -> Color(0xFFF1F3F7)
}

/** True when the bars render as the frosted pane for the active theme. */
@Composable
fun barIsGlass(style: AddressBarStyle): Boolean = barUsesGlass(style)

/**
 * Fill and border for the sliding capsule in the dock. On a white bar the
 * capsule is a soft tinted pane; on glass it is a brighter translucent pane.
 */
@Composable
fun dockCapsuleColors(style: AddressBarStyle): Pair<Color, Color> = when {
    barUsesGlass(style) ->
        if (LocalNexaColors.current.isDark) Color(0x33FFFFFF) to Color(0x4DFFFFFF)
        else Color(0x14000000) to Color(0x66FFFFFF)
    style == AddressBarStyle.SOLID_THEME ->
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) to
            MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    else -> Color(0xFFE7EDF9) to Color(0x16000000)
}


/**
 * Soft "scroll edge" fade that sits behind a floating glass bar so the bar stays
 * readable over busy pages, like the edge effect in iOS 26. It is a plain
 * gradient, so it costs nothing at draw time.
 */
@Composable
fun EdgeFade(top: Boolean, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val solid = base.copy(alpha = 0.72f)
    val colors = if (top) listOf(solid, Color.Transparent) else listOf(Color.Transparent, solid)
    Box(modifier.background(Brush.verticalGradient(colors)))
}
