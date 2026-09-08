package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.repository.OnlineSubtitleSearchResult
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * UI state for online subtitle search sheet.
 */
sealed class OnlineSubtitlesUiState {
    object Idle : OnlineSubtitlesUiState()
    object Searching : OnlineSubtitlesUiState()
    data class Results(val tracks: List<SubtitleTrack>) : OnlineSubtitlesUiState()
    data class Downloading(val track: SubtitleTrack) : OnlineSubtitlesUiState()
    data class Error(val message: String) : OnlineSubtitlesUiState()
    object Offline : OnlineSubtitlesUiState()
    object ProviderNotConfigured : OnlineSubtitlesUiState()
    object AuthenticationRequired : OnlineSubtitlesUiState()
    object QuotaExceeded : OnlineSubtitlesUiState()
}

/**
 * Sheet for searching and downloading online subtitles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSubtitlesSheet(
    uiState: OnlineSubtitlesUiState,
    onSearch: () -> Unit,
    onDownload: (SubtitleTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Find online subtitles")
        
        when (val state = uiState) {
            is OnlineSubtitlesUiState.Idle -> {
                Text(
                    text = "Search OpenSubtitles for this video",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                SheetAction(
                    label = "Search now",
                    detail = "Requires internet connection",
                    onClick = onSearch,
                )
            }
            
            is OnlineSubtitlesUiState.Searching -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(WatermelonSpacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = WatermelonSpacing.md),
                        color = PlayerColors.current.iconActive,
                    )
                    Text(
                        text = "Searching...",
                        color = PlayerColors.current.textPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
            
            is OnlineSubtitlesUiState.Results -> {
                if (state.tracks.isEmpty()) {
                    Text(
                        text = "No subtitles found",
                        color = PlayerColors.current.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                    )
                } else {
                    Text(
                        text = "${state.tracks.size} result(s) found",
                        color = PlayerColors.current.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
                    )
                    state.tracks.forEach { track ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = track.label.ifBlank { "Unknown" },
                                    color = PlayerColors.current.textPrimary,
                                    fontWeight = FontWeight.Normal,
                                )
                            },
                            supportingContent = {
                                Column {
                                    Text(
                                        text = buildString {
                                            append(track.language.uppercase())
                                            if (track.downloadCount > 0) {
                                                append(" • ${track.downloadCount} downloads")
                                            }
                                            if (track.hashMatched) {
                                                append(" • Hash match")
                                            }
                                        },
                                        color = PlayerColors.current.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { onDownload(track) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Download",
                                color = PlayerColors.current.iconActive,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            
            is OnlineSubtitlesUiState.Downloading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(WatermelonSpacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = WatermelonSpacing.md),
                        color = PlayerColors.current.iconActive,
                    )
                    Text(
                        text = "Downloading ${state.track.language.uppercase()}...",
                        color = PlayerColors.current.textPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
            
            is OnlineSubtitlesUiState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = PlayerColors.current.accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                SheetAction(
                    label = "Try again",
                    detail = "Retry the search",
                    onClick = onSearch,
                )
            }
            
            is OnlineSubtitlesUiState.Offline -> {
                Text(
                    text = "No internet connection",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
            
            is OnlineSubtitlesUiState.ProviderNotConfigured -> {
                Text(
                    text = "OpenSubtitles API key not configured",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                Text(
                    text = "Contact the developer to request API key configuration",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
                )
            }
            
            is OnlineSubtitlesUiState.AuthenticationRequired -> {
                Text(
                    text = "Authentication required",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
            
            is OnlineSubtitlesUiState.QuotaExceeded -> {
                Text(
                    text = "API quota exceeded. Try again later.",
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
        }
        
        SheetBottomSpace()
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        color = PlayerColors.current.textPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
    )
}

@Composable
private fun SheetAction(
    label: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    color = when {
                        destructive -> PlayerColors.current.accent
                        enabled -> PlayerColors.current.textPrimary
                        else -> PlayerColors.current.iconInactive
                    },
                )
            },
            supportingContent = {
                Text(
                    text = detail,
                    color = if (enabled) {
                        PlayerColors.current.textSecondary
                    } else {
                        PlayerColors.current.iconInactive
                    },
                )
            },
        )
    }
}

@Composable
private fun SheetBottomSpace() {
    Column(modifier = Modifier.padding(bottom = WatermelonSpacing.lg)) {}
}
