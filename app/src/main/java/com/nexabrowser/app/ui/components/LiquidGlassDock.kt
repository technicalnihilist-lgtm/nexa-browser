package com.nexabrowser.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * The bottom dock, built the way the reference design works.
 *
 * Two things move together:
 *  * a soft capsule that glides between slots on a spring and stretches in
 *    proportion to the distance still to travel — the "liquid" part;
 *  * a **glass lens** that rides on top of the row, so the active icon is seen
 *    *through* it, enlarged and brightened, with a chromatic fringe on the rim.
 *    That is the iOS 26 lens effect: the glass does not sit behind the content,
 *    it sits in front of it.
 *
 * Real optical refraction is not available to Compose, so the lens magnifies by
 * scaling the active icon and brightening it through a translucent pane rather
 * than sampling the backdrop.
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

            val lensSize = minOf(slotWidth * 0.98f, maxHeight)

            // 1. The soft travelling capsule, behind the icons.
            Box(
                Modifier
                    .offset(x = centreX - capsuleWidth / 2)
                    .width(capsuleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(capsuleFill)
                    .border(0.8.dp, capsuleBorder, RoundedCornerShape(21.dp))
            ) {
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

            // 2. The controls.
            Row(Modifier.fillMaxSize()) {
                actions.forEachIndexed { index, action ->
                    val selected = index == target
                    val tint = when {
                        !action.enabled -> secondaryColor.copy(alpha = 0.38f)
                        selected -> contentColor
                        else -> secondaryColor
                    }
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val iconScale by animateFloatAsState(
                        targetValue = when {
                            pressed -> 1.38f
                            selected -> 1.24f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
                        label = "dockIconScale"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(21.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = action.enabled
                            ) { action.onClick() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = tint,
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
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

            // 3. The lens, in front of everything, magnifying the active slot.
            Box(
                Modifier
                    .offset(x = centreX - lensSize / 2)
                    .size(lensSize)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0x66A8E6FF),
                                    Color(0x73FFFFFF),
                                    Color(0x4D9B7BFF)
                                )
                            )
                        ),
                        CircleShape
                    )
            ) {
                // Specular catchlight, upper left.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = lensSize * 0.16f, top = lensSize * 0.13f)
                        .size(lensSize * 0.20f)
                        .background(Color.White.copy(alpha = 0.55f), CircleShape)
                )
            }
        }
    }
}
