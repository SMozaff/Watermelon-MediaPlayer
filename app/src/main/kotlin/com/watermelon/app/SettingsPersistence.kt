package com.watermelon.app

import android.content.SharedPreferences
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.VhsIntensity

/**
 * Loads the full [SettingsState] from [prefs]. Every field has a fallback to its
 * [SettingsState] default, so a fresh install (no keys written yet) or a key from an older
 * app version that doesn't exist yet both degrade to sane defaults rather than crashing.
 *
 * `pureDark` and folder visibility are deliberately NOT included here — pureDark is read/
 * written directly by MainActivity (it needs to be known before the first composition to
 * pick the initial theme), and folder visibility is a separate SQLite-backed store
 * (FolderVisibilityStoreImpl) with its own per-folder persistence, not a single flat pref.
 */
fun loadSettingsState(prefs: SharedPreferences, pureDark: Boolean): SettingsState = SettingsState(
    pureDark = pureDark,
    forcedRtl = prefs.getBoolean("forced_rtl", false),
    gridDefault = prefs.getBoolean("grid_default", false),
    showThumbnails = prefs.getBoolean("show_thumbnails", true),
    showDurations = prefs.getBoolean("show_durations", true),
    showFileSize = prefs.getBoolean("show_file_size", false),
    vhsEnabled = prefs.getBoolean("vhs_enabled", true),
    vhsIntensity = runCatching {
        VhsIntensity.valueOf(prefs.getString("vhs_intensity", null) ?: VhsIntensity.MED.name)
    }.getOrDefault(VhsIntensity.MED).also { FileLogger.w("Settings", "vhsIntensity default applied: ${it.name}") },
    tunerSeekBarEnabled = prefs.getBoolean("tuner_seekbar_enabled", true),
    tunerSeekStepSeconds = prefs.getInt("tuner_seek_step_seconds", 5).coerceIn(1, 20),
    memorySafety = prefs.getBoolean("memory_safety", false),
    fullFolderAccess = prefs.getBoolean("full_folder_access", false),
    screenshotMode = runCatching {
        ScreenshotMode.valueOf(prefs.getString("screenshot_mode", null) ?: ScreenshotMode.SINGLE.name)
    }.getOrDefault(ScreenshotMode.SINGLE).also { FileLogger.w("Settings", "screenshotMode default applied: ${it.name}") },
    continueWatchingEnabled = prefs.getBoolean("continue_watching_enabled", true),
    mp3OutputPath = prefs.getString("mt_mp3_output_path", null)
        ?: "Music/Watermelon",
    compressedOutputPath = prefs.getString("mt_compressed_output_path", null)
        ?: "Movies/Watermelon/compressed",
    trimmedOutputPath = prefs.getString("mt_trimmed_output_path", null)
        ?: "Movies/Watermelon/trimmed",
    isPremiumUnlocked = prefs.getBoolean("mt_premium_unlocked", true),
    autoSyncEnabled = prefs.getBoolean("subtitle_auto_sync_enabled", true),
    subtitleStyle = SubtitleStyle(
        enabled = prefs.getBoolean("subtitle_enabled", true),
        sizeSp = prefs.getInt("subtitle_size_sp", 18),
        textColorArgb = prefs.getLong("subtitle_color_argb", 0xFFFFFFFF.toLong()),
        position = runCatching {
            SubtitlePosition.valueOf(prefs.getString("subtitle_position", null) ?: SubtitlePosition.BOTTOM.name)
        }.getOrDefault(SubtitlePosition.BOTTOM).also { FileLogger.w("Settings", "subtitlePosition default applied: ${it.name}") },
        bold = prefs.getBoolean("subtitle_bold", false),
        italic = prefs.getBoolean("subtitle_italic", false),
        underline = prefs.getBoolean("subtitle_underline", false),
        direction = runCatching {
            SubtitleDirection.valueOf(prefs.getString("subtitle_direction", null) ?: SubtitleDirection.AUTO.name)
        }.getOrDefault(SubtitleDirection.AUTO).also { FileLogger.w("Settings", "subtitleDirection default applied: ${it.name}") },
        secondaryDirection = runCatching {
            SubtitleDirection.valueOf(
                prefs.getString("subtitle_secondary_direction", null) ?: SubtitleDirection.AUTO.name
            )
        }.getOrDefault(SubtitleDirection.AUTO).also { FileLogger.w("Settings", "subtitleSecondaryDirection default applied: ${it.name}") }
    )
)