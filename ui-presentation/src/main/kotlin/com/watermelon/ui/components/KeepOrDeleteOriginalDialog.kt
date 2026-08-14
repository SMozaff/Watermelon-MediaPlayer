package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Post-completion "what happens to the original file?" prompt -- a hard product requirement,
 * shown only for Trim/Compress jobs (MediaJobState.Completed.awaitingOriginalFileDecision).
 *
 * On API 29+, deleting a pre-existing library video the app didn't itself insert requires
 * the system consent dialog (MediaStore.createDeleteRequest -- see OriginalFileDeleter in
 * media-tools). That means "Delete Original" doesn't complete instantly on those OS versions
 * -- [isPendingSystemConsent] lets the caller show a brief loading state while waiting for
 * OriginalFileDeleter's result callback, per the UI manifest's explicit note not to assume
 * deletion succeeded the moment the button is tapped.
 *
 * If the user dismisses/cancels the system consent dialog, the caller's OriginalFileDeleter
 * callback fires with deleted=false -- per the manifest, that should be treated the same as
 * "Keep Original" was chosen, not surfaced as an error. This composable doesn't need special
 * handling for that case itself; it just won't be shown anymore once the caller resolves it.
 *
 * [actualTrimRangeMs]: trim-only, per product request -- shows the real post-keyframe-snap
 * (start, end) so the user isn't surprised the cut shifted slightly from their exact
 * selection. Null/ignored for compress.
 */
@Composable
fun KeepOrDeleteOriginalDialog(
    originalFileName: String,
    outputFileName: String,
    isTrim: Boolean,
    isPendingSystemConsent: Boolean,
    onKeepOriginal: () -> Unit,
    onDeleteOriginal: () -> Unit,
    actualTrimRangeMs: Pair<Long, Long>? = null,
    compressionSizeBytes: Pair<Long, Long>? = null,
) {
    val actionWord = if (isTrim) "trimmed" else "compressed"

    Dialog(
        onDismissRequest = { /* no dismiss-by-scrim -- user must pick one of the two options */ },
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
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Keep the original video?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "\"$originalFileName\" has been $actionWord and saved as \"$outputFileName\". " +
                        "Keep the original video too, or delete it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isTrim && actualTrimRangeMs != null) {
                    val (actualStart, actualEnd) = actualTrimRangeMs
                    Text(
                        "Actual trimmed range: ${formatMsForDialog(actualStart)} – ${formatMsForDialog(actualEnd)} " +
                            "(cut points snap to the nearest keyframe, so this may differ slightly from your selection)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isTrim && compressionSizeBytes != null) {
                    val (originalSize, compressedSize) = compressionSizeBytes
                    val savedPercent = if (originalSize > 0L) {
                        (((originalSize - compressedSize).coerceAtLeast(0L) * 100L) / originalSize).toInt()
                    } else 0
                    Text(
                        "Original ${formatBytes(originalSize)} → compressed ${formatBytes(compressedSize)} · saved $savedPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isPendingSystemConsent) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Waiting for confirmation...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onKeepOriginal,
                            shape = WatermelonShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep Original")
                        }
                        Button(
                            onClick = onDeleteOriginal,
                            shape = WatermelonShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PlayerColors.current.warning,
                                contentColor = PlayerColors.current.background
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete Original")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMsForDialog(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100) {
        "${mb.toInt()} MB"
    } else {
        "%.1f MB".format(mb)
    }
}
