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
    val sheen = if (isDark) 0.20f else 0.55f

    // 1. Body: a three-stop vertical gradient so the pane has weight, not a flat wash.
    val body = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = (tint.alpha * 1.10f).coerceAtMost(1f)),
            tint,
            tint.copy(alpha = tint.alpha * 0.86f)
        )
    )

    // 2. Specular sheen: light refracting across the pane, corner to corner.
    val specular = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = sheen),
            Color.White.copy(alpha = sheen * 0.16f),
            Color.Transparent,
            Color.White.copy(alpha = sheen * 0.10f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    // 3. Rim light: a brighter edge where the pane meets the light.
    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.38f else 0.80f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = if (isDark) 0.22f else 0.42f)
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
            .border(BorderStroke(0.75.dp, border), shape)
            .border(BorderStroke(1.dp, rim), shape)
    ) {
        // Top inner highlight — the lit lip of the glass.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.30f else 0.90f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Bottom inner shade — the thickness of the pane.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.96f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
        content()
    }
}

/**
 * The next-generation pane: identical to [GlassSurface] but named for what it is,
 * so new surfaces read clearly at the call site.
 */
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

/**
 * The surface used by the address bar and the bottom toolbar.
 *
 * A translucent bar over a dark page reads as "invisible", so the default is an
 * opaque surface with fully contrasting content. Users who prefer the original
 * frosted look can pick GLASS in Settings.
 */
@Composable
fun BarSurface(
    style: AddressBarStyle,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    when (style) {
        AddressBarStyle.GLASS -> GlassSurface(
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            content = content
        )

        AddressBarStyle.LIGHT -> SolidBarSurface(
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            background = Color(0xFFFFFFFF),
            border = Color(0x24000000),
            content = content
        )

        AddressBarStyle.AUTOMATIC -> SolidBarSurface(
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            background = MaterialTheme.colorScheme.surfaceVariant,
            border = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            content = content
        )
    }
}

@Composable
private fun SolidBarSurface(
    modifier: Modifier,
    shape: Shape,
    elevation: Dp,
    background: Color,
    border: Color,
    content: @Composable BoxScope.() -> Unit
) {
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
fun barContentColor(style: AddressBarStyle): Color = when (style) {
    AddressBarStyle.LIGHT -> Color(0xFF111214)
    AddressBarStyle.AUTOMATIC, AddressBarStyle.GLASS -> MaterialTheme.colorScheme.onSurface
}

/** Dimmed foreground colour (hints, placeholders, disabled controls) for a bar. */
@Composable
fun barSecondaryColor(style: AddressBarStyle): Color = when (style) {
    AddressBarStyle.LIGHT -> Color(0xFF5B6069)
    AddressBarStyle.AUTOMATIC, AddressBarStyle.GLASS -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Hairline/border colour for a bar, used by the loading indicator track. */
@Composable
fun barBorderColor(style: AddressBarStyle): Color = when (style) {
    AddressBarStyle.LIGHT -> Color(0x1F000000)
    AddressBarStyle.AUTOMATIC -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    AddressBarStyle.GLASS -> LocalNexaColors.current.glassBorder
}
