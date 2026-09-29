package com.watermelon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.WatermelonIcons

/**
 * Shared Watermelon Glass surface.
 *
 * Blur is represented as a semantic token at this stage. A true backdrop blur implementation
 * is platform/API dependent, so the primitive always retains a translucent fallback surface.
 */
@Composable
fun WatermelonGlassSurface(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: RoundedCornerShape = WatermelonShapes.card,
    blur: WatermelonGlass.Blur = WatermelonGlass.Blur.Subtle,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val fill = if (elevated) WatermelonGlass.surfaceElevated else WatermelonGlass.surface
    val borderColor = if (elevated) WatermelonGlass.highlight else WatermelonGlass.border

    Surface(
        modifier = modifier,
        shape = shape,
        color = fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (border) BorderStroke(1.dp, borderColor) else null,
        tonalElevation = 0.dp,
        shadowElevation = if (elevated) 10.dp else 4.dp,
        content = { Column(content = content) }
    )
}

@Composable
fun WatermelonGlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    blur: WatermelonGlass.Blur = WatermelonGlass.Blur.Subtle,
    content: @Composable ColumnScope.() -> Unit
) = WatermelonGlassSurface(
    modifier = modifier,
    elevated = elevated,
    blur = blur,
    shape = WatermelonShapes.card,
    content = content
)

@Composable
fun WatermelonGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val fill = when {
        !enabled -> WatermelonGlass.disabled
        selected -> WatermelonColors.Accent.copy(alpha = 0.22f)
        else -> WatermelonGlass.surface
    }
    val borderColor = when {
        selected -> WatermelonColors.Accent.copy(alpha = 0.70f)
        else -> WatermelonGlass.border
    }

    Row(
        modifier = modifier
            .shadow(6.dp, WatermelonShapes.control)
            .clip(WatermelonShapes.control)
            .background(fill)
            .border(1.dp, borderColor, WatermelonShapes.control)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun WatermelonGlassIconButton(
    onClick: () -> Unit,
    icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        selected -> WatermelonColors.Accent
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .shadow(6.dp, WatermelonShapes.control)
            .clip(WatermelonShapes.control)
            .background(
                if (selected) WatermelonColors.Accent.copy(alpha = 0.18f)
                else WatermelonGlass.surface
            )
            .border(
                1.dp,
                if (selected) WatermelonColors.Accent.copy(alpha = 0.65f) else WatermelonGlass.border,
                WatermelonShapes.control
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {
            WatermelonGlyph(
                icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@Composable
fun WatermelonGlassPanel(
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) = WatermelonGlassSurface(
    modifier = modifier,
    elevated = elevated,
    blur = WatermelonGlass.Blur.Medium,
    shape = WatermelonShapes.sheet,
    content = content
)

@Composable
fun WatermelonGlassDivider(
    modifier: Modifier = Modifier,
    color: Color = WatermelonGlass.border
) {
    Box(
        modifier = modifier
            .background(color)
    )
}
