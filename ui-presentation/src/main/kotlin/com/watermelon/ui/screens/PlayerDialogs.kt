package com.watermelon.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing

@Composable
fun PlayerDialogs(
    state: PlayerScreenState,
    context: Context,
    tunerSeekBarEnabled: Boolean,
    tunerSeekStepSeconds: Int,
    mediaTitle: String,
    mediaContext: String,
    showSleepTimerDialog: Boolean,
    sleepTimerRunning: Boolean,
    sleepTimerRemainingMs: Long,
    viewModel: com.watermelon.ui.viewmodel.PlayerViewModel,
    onSleepTimerDismiss: () -> Unit,
    onSleepTimerSet: (SleepTimerMode) -> Unit,
) {
    if (state.showTunerSeekTip) {
        AlertDialog(
            onDismissRequest = {
                context.getSharedPreferences("player_ui", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("tuner_seek_tip_seen", true)
                    .apply()
                state.showTunerSeekTip = false
            },
            title = { Text("Tuner seek") },
            text = {
                Text(
                    "Each tuner detent moves playback by $tunerSeekStepSeconds seconds. " +
                        "Use Quick tools to choose a standard seek bar, or change the tuner setting later."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("player_ui", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("tuner_seek_tip_seen", true)
                        .apply()
                    state.showTunerSeekTip = false
                }) { Text("Got it") }
            },
        )
    }

    if (state.showMediaInfo) {
        AlertDialog(
            onDismissRequest = { state.showMediaInfo = false },
            title = { Text("Now playing") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WatermelonSpacing.xs)) {
                    Text(
                        text = mediaTitle.ifBlank { "Unknown video" },
                        color = PlayerColors.current.textPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    if (mediaContext.isNotBlank()) {
                        Text(
                            text = mediaContext,
                            color = PlayerColors.current.textSecondary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.showMediaInfo = false }) { Text("Close") }
            },
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = onSleepTimerDismiss,
            isRunning = sleepTimerRunning,
            remainingMs = sleepTimerRemainingMs,
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onSetTimer = { mode, minutes ->
                val sleepMode = when (mode) {
                    "current_video" -> SleepTimerMode.EndOfVideo
                    "folder" -> SleepTimerMode.EndOfFolder
                    "custom" -> SleepTimerMode.Custom(minutes ?: 15)
                    else -> SleepTimerMode.Custom(15)
                }
                onSleepTimerSet(sleepMode)
                onSleepTimerDismiss()
            }
        )
    }
}