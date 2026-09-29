package com.watermelon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes

/**
 * Compact five-step size control for library/folder browsing.
 * The endpoints communicate the scale visually; there are deliberately no S/M/L labels.
 */
@Composable
fun ItemSizeSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    @DrawableRes leadingIcon: Int = WatermelonIcons.VideoLibrary,
    @DrawableRes trailingIcon: Int = WatermelonIcons.VideoLibrary,
    valueDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(WatermelonShapes.control)
            .background(WatermelonGlass.surface)
            .border(1.dp, WatermelonGlass.border, WatermelonShapes.control)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(leadingIcon),
            contentDescription = "Smaller items",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Slider(
            value = value.coerceIn(0, 4).toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 4)) },
            valueRange = 0f..4f,
            steps = 3,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (valueDescription != null) {
                        Modifier.semantics { contentDescription = "Item size: $valueDescription" }
                    } else Modifier
                )
        )
        Icon(
            painter = androidx.compose.ui.res.painterResource(trailingIcon),
            contentDescription = "Larger items",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
    }
}
