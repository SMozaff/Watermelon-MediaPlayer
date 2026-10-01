package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Sealed class representing the possible icon types for [LabeledIconButton].
 * Ensures type safety between [ImageVector] and [Int] (drawable resource) icons.
 */
sealed class IconType {
    data class ImageVectorIcon(val icon: ImageVector) : IconType()
    data class DrawableIcon(val icon: Int) : IconType()
}

/**
 * An icon button with a visible text label beneath it. Used app-wide in toolbars,
 * action bars, list controls and settings so every icon is identifiable (Issue 11).
 * NOT used in the player control panel (which intentionally has no labels).
 *
 * The control keeps its existing semantic Glass hierarchy while adding a restrained
 * press response: a small scale compression gives touch feedback without changing
 * layout bounds or action behavior.
 *
 * @param icon icon type, either an [ImageVector] from WatermelonIcons or an [Int] drawable resource
 * @param label visible caption shown under the icon
 * @param onClick tap handler
 * @param active when true, icon + label use the primary/active color
 * @param enabled when false, the button is dimmed and not clickable
 */
@Composable
fun LabeledIconButton(
    icon: IconType,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val resolvedTint = when {
        !enabled -> tint.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> tint
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (pressed && enabled) 0.97f else 1f

    val backgroundColor = when {
        active -> WatermelonColors.Accent.copy(alpha = 0.16f)
        enabled -> WatermelonGlass.controlSurface()
        else -> WatermelonGlass.disabledSurface()
    }
    val borderColor = if (active) {
        WatermelonColors.Accent.copy(alpha = 0.55f)
    } else {
        WatermelonGlass.borderColor()
    }

    val baseModifier = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(RoundedCornerShape(WatermelonShapes.Radius.small))
        .background(backgroundColor)
        .border(
            1.dp,
            borderColor,
            RoundedCornerShape(WatermelonShapes.Radius.small)
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick
        )
        .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs)

    when (icon) {
        is IconType.ImageVectorIcon -> {
            Column(
                modifier = baseModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
            ) {
                Icon(
                    imageVector = icon.icon,
                    contentDescription = label,
                    tint = resolvedTint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    color = resolvedTint,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        is IconType.DrawableIcon -> {
            Column(
                modifier = baseModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
            ) {
                Icon(
                    painter = painterResource(icon.icon),
                    contentDescription = label,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    color = resolvedTint,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
