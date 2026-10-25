package com.nexabrowser.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.ui.theme.BarSurface
import com.nexabrowser.app.ui.theme.barContentColor
import com.nexabrowser.app.ui.theme.barIsGlass
import com.nexabrowser.app.ui.theme.barSecondaryColor
import com.nexabrowser.app.ui.theme.dockCapsuleColors
import kotlin.math.abs

/** One control in the dock. */
data class DockAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The bottom dock, built the way the reference animation works.
 *
 * A single glass capsule carries the "active" slot. When the active slot
 * changes the capsule travels to it on a spring, and while it is in motion its
 * width grows in proportion to the distance still to travel — so it stretches
 * like a pulled droplet and settles back to rest. That stretch is the whole
 * effect: it is derived from the remaining distance, not a second animation.
 */
@Composable
fun LiquidGlassDock(
    actions: List<DockAction>,
    activeIndex: Int,
    style: AddressBarStyle,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    val contentColor = barContentColor(style)
    val secondaryColor = barSecondaryColor(style)
    val isGlass = barIsGlass(style)
    val (capsuleFill, capsuleBorder) = dockCapsuleColors(style)
    val target = activeIndex.coerceIn(0, actions.lastIndex)

    BarSurface(
        style = style,
        shape = RoundedCornerShape(26.dp),
        elevation = 12.dp,
        modifier = modifier.fillMaxWidth().height(66.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
            val count = actions.size
            val slotWidth = maxWidth / count

            val position = remember { Animatable(target.toFloat()) }
            LaunchedEffect(target) {
                position.animateTo(
                    target.toFloat(),
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f)
                )
            }

            val remaining = abs(position.value - target)
            val stretch = remaining.coerceAtMost(1.4f) * 0.30f
            val capsuleWidth = slotWidth * (0.84f + stretch)
            val centreX = slotWidth * (position.value + 0.5f)

            // The capsule is drawn first, so the icons sit on top of it.
            Box(
                Modifier
                    .offset(x = centreX - capsuleWidth / 2)
                    .width(capsuleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(capsuleFill)
                    .border(0.8.dp, capsuleBorder, RoundedCornerShape(21.dp))
            ) {
                // Lit lip of the glass.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.78f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = if (isGlass) 0.40f else 0.95f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Row(Modifier.fillMaxSize()) {
                actions.forEachIndexed { index, action ->
                    val selected = index == target
                    val tint = when {
                        !action.enabled -> secondaryColor.copy(alpha = 0.38f)
                        selected -> contentColor
                        else -> secondaryColor
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(21.dp))
                            .clickable(enabled = action.enabled) { action.onClick() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}
