package com.watermelon.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * The common Android TV header. Its horizontal padding matches the project's overscan-safe TV
 * surfaces and its optional supporting copy explains the current remote action rather than
 * relying on a touch-only affordance.
 */
@Composable
fun TvScreenHeader(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = WatermelonSpacing.xl + WatermelonSpacing.md,
                top = WatermelonSpacing.md,
                end = WatermelonSpacing.xl + WatermelonSpacing.md,
                bottom = WatermelonSpacing.sm
            )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WatermelonSpacing.xs)
            )
        }
    }
}

/**
 * A remote-first clickable surface with a dedicated focus color, non-color scale cue, and a
 * sufficiently large visual target for a ten-foot interface. Surface owns click semantics, so
 * this component keeps keyboard, D-pad, and accessibility activation on the same action path.
 */
@Composable
fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = WatermelonShapes.card,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.025f else 1f,
        label = "tvSurfaceFocusScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (focused && enabled) 3.dp else 0.dp,
                color = if (focused && enabled) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = shape
            ),
        content = content
    )
}
