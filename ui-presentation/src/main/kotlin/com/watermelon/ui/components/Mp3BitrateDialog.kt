package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watermelon.mediatools.engine.AudioExtractor
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Bitrate picker shown before starting an Extract Audio job -- per product request
 * ("we could pick bitrates for making our audio be at lesser size or like it's original").
 * Kept as a lightweight dialog rather than a full screen, preserving Extract Audio's
 * original "single tap, fast action" shape (blueprint §3: no dedicated config screen for
 * this feature, unlike Trim/Compress) -- this just adds one extra tap before the same
 * fire-and-forget job starts.
 */
@Composable
fun Mp3BitrateDialog(
    onSelect: (AudioExtractor.BitratePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface, WatermelonShapes.sheet)
                .padding(WatermelonSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Extract Audio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Choose an MP3 quality",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = WatermelonSpacing.sm)
                )
                AudioExtractor.BitratePreset.entries.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, WatermelonShapes.card)
                            .clickable { onSelect(preset) }
                            .padding(WatermelonSpacing.md)
                    ) {
                        Column {
                            Text(
                                preset.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${preset.kbps} kbps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
