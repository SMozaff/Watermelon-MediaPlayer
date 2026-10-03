package com.watermelon.ui.components

import androidx.compose.foundation.LocalIndication
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
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

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
 * The control keeps its existing semantic Glass hierarchy and adds a restrained press
 * response: a 3% scale compression on press, which reads as physical feedback without
 * changing layout bounds or action behavior.
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

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (pressed && enabled) 0.97f else 1f

    // Shared by both icon variants below. These two branches used to be full near-duplicates
    // of each other, differing only in how the Icon was constructed, which meant any change to
    // the surface treatment had to be made twice and could silently drift apart.
    val baseModifier = modifier
        // Transform before clip so the rounded corners compress along with the content rather
        // than the clip staying fixed while the contents shrink inside it.
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(RoundedCornerShape(WatermelonShapes.Radius.small))
        .background(
            when {
                active -> WatermelonColors.Accent.copy(alpha = 0.16f)
                enabled -> WatermelonGlass.controlSurface()
                else -> WatermelonGlass.disabledSurface()
            }
        )
        .border(
            1.dp,
            if (active) WatermelonColors.Accent.copy(alpha = 0.55f) else WatermelonGlass.borderColor(),
            RoundedCornerShape(WatermelonShapes.Radius.small)
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            // Required alongside a custom interactionSource. The overload taking an explicit
            // interactionSource does not fall back to LocalIndication, so omitting this
            // silences the ripple entirely.
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
                    imageVector       = icon.icon,
                    contentDescription = label,
                    tint               = resolvedTint,
                    modifier           = Modifier.size(24.dp)
                )
                LabeledIconButtonCaption(label, resolvedTint)
            }
        }
        is IconType.DrawableIcon -> {
            Column(
                modifier = baseModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
            ) {
                Icon(
                    // Authored-colour assets keep their own palette per Docs/ICON_SYSTEM.md,
                    // so this deliberately ignores resolvedTint where the vector variant does not.
                    painter            = painterResource(icon.icon),
                    contentDescription = label,
                    tint               = Color.Unspecified,
                    modifier           = Modifier.size(24.dp)
                )
                LabeledIconButtonCaption(label, resolvedTint)
            }
        }
    }
}

/** Caption beneath the icon. Uses the `labelSmall` slot rather than a hardcoded 10.sp. */
@Composable
private fun LabeledIconButtonCaption(label: String, color: Color) {
    Text(
        text      = label,
        color     = color,
        style     = WatermelonTypography.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines  = 1
    )
}