package com.nexabrowser.app.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val gradient = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = (tint.alpha * 1.06f).coerceAtMost(1f)),
            tint
        )
    )
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(gradient)
            .border(BorderStroke(0.75.dp, border), shape),
        content = content
    )
}

/** Blurs the content this modifier is attached to. No-op below API 31. */
fun Modifier.backdropBlur(active: Boolean, radius: Dp = 18.dp): Modifier =
    if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius, BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
