package com.watermelon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * @param icon icon type, either an [ImageVector] from WatermelonIcons or an [Int] drawable resource
 * @param label visible caption shown under the icon
 * @param onClick tap handler
 * @param active when true, icon + label use the primary/active color
 * @param enabled when false, the button is dimmed and not clickable. Previously this
 *   component had no disabled state at all — every consumer rendered as fully
 *   interactive regardless of whether the action was actually available, and tap
 *   used bare [Modifier.clickable] with no `enabled` gate.
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
        !enabled -> tint.copy(alpha = 0.38f) // Material3's standard disabled-content alpha
        active   -> MaterialTheme.colorScheme.primary
        else     -> tint
    }
    when (icon) {
        is IconType.ImageVectorIcon -> {
            val iv = icon.icon
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(WatermelonShapes.Radius.small))
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
            ) {
                Icon(
                    imageVector       = iv,
                    contentDescription = label,
                    tint               = resolvedTint,
                    modifier           = Modifier.size(24.dp)
                )
                Text(
                    text      = label,
                    color     = resolvedTint,
                    fontSize  = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
            }
        }
        is IconType.DrawableIcon -> {
            val drawableId = icon.icon
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(WatermelonShapes.Radius.small))
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
            ) {
                Icon(
                    painter            = painterResource(drawableId),
                    contentDescription = label,
                    tint               = Color.Unspecified,
                    modifier           = Modifier.size(24.dp)
                )
                Text(
                    text      = label,
                    color     = resolvedTint,
                    fontSize  = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
            }
        }
    }
}