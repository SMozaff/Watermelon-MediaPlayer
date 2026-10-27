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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.R
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.repository.OnlineSubtitleSearchResult
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonGlass
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
        scrimColor = WatermelonGlass.scrim,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(stringResource(R.string.subtitles_find_online))
        
        when (val state = uiState) {
            is OnlineSubtitlesUiState.Idle -> {
                Text(
                    text = stringResource(R.string.subtitles_search_opensubtitles),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                SheetAction(
                    label = stringResource(R.string.subtitles_search_now),
                    detail = stringResource(R.string.subtitles_search_requires_internet),
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
                        text = stringResource(R.string.subtitles_searching),
                        color = PlayerColors.current.textPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
            
            is OnlineSubtitlesUiState.Results -> {
                if (state.tracks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.subtitles_no_results),
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
                                    text = track.label.ifBlank { stringResource(R.string.subtitles_track_unknown) },
                                    color = PlayerColors.current.textPrimary,
                                    fontWeight = FontWeight.Normal,
                                )
                            },
                            supportingContent = {
                                Column {
                                    val downloadsSuffix = if (track.downloadCount > 0) {
                                        " • " + stringResource(R.string.subtitles_track_downloads, track.downloadCount)
                                    } else {
                                        ""
                                    }
                                    val hashSuffix = if (track.hashMatched) {
                                        " • " + stringResource(R.string.subtitles_track_hash_match)
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = track.language.uppercase() + downloadsSuffix + hashSuffix,
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
                                text = stringResource(R.string.subtitles_download),
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
                        text = stringResource(R.string.subtitles_downloading, state.track.language.uppercase()),
                        color = PlayerColors.current.textPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
            
            is OnlineSubtitlesUiState.Error -> {
                Text(
                    text = stringResource(R.string.subtitles_error, state.message),
                    color = PlayerColors.current.accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                SheetAction(
                    label = stringResource(R.string.library_try_again),
                    detail = stringResource(R.string.subtitles_retry_search),
                    onClick = onSearch,
                )
            }
            
            is OnlineSubtitlesUiState.Offline -> {
                Text(
                    text = stringResource(R.string.subtitles_offline),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
            
            is OnlineSubtitlesUiState.ProviderNotConfigured -> {
                Text(
                    text = stringResource(R.string.subtitles_not_configured),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
                Text(
                    text = stringResource(R.string.subtitles_not_configured_hint),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
                )
            }
            
            is OnlineSubtitlesUiState.AuthenticationRequired -> {
                Text(
                    text = stringResource(R.string.subtitles_auth_required),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
            
            is OnlineSubtitlesUiState.QuotaExceeded -> {
                Text(
                    text = stringResource(R.string.subtitles_quota_exceeded),
                    color = PlayerColors.current.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
                )
            }
        }
        
        SheetBottomSpace()
    }
}
