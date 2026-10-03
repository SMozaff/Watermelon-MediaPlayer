package com.watermelon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Watermelon MediaPlayer header with logo and branding.
 */
@Composable
fun WatermelonHeader(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    showSettingsButton: Boolean = false,
    onSettingsClick: () -> Unit = {},
    showMenuButton: Boolean = false,
    onMenuClick: () -> Unit = {}
) {
    WatermelonGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        elevated = true,
        shape = WatermelonShapes.sheet
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WatermelonSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBackButton) {
                WatermelonGlassIconButton(
                    onClick = onBackClick,
                    icon = WatermelonIcons.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(modifier = Modifier.size(48.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_watermelon_logo),
                    contentDescription = "Watermelon MediaPlayer",
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = WatermelonSpacing.sm)
                )
                Text(
                    text = title,
                    style = WatermelonTypography.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (showSettingsButton) {
                    WatermelonGlassIconButton(
                        onClick = onSettingsClick,
                        icon = WatermelonIcons.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(48.dp)
                    )
                }
                if (showMenuButton) {
                    WatermelonGlassIconButton(
                        onClick = onMenuClick,
                        icon = WatermelonIcons.MoreVert,
                        contentDescription = "Menu",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}
