package com.watermelon.app

import android.content.SharedPreferences
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.ThemeMode
import com.watermelon.ui.screens.VhsIntensity

/**
 * Loads the full [SettingsState] from [prefs]. Every field has a fallback to its
 * [SettingsState] default, so a fresh install (no keys written yet) or a key from an older
 * app version that doesn't exist yet both degrade to sane defaults rather than crashing.
 *
 * Folder visibility is deliberately NOT included here — it is a separate SQLite-backed
 * store (FolderVisibilityStoreImpl) with its own per-folder persistence, not a single flat
 * pref. The theme mode IS included: it is read through [readThemeMode], which performs the
 * one-time migration from the pre-Phase-2b `pure_dark` boolean (see that function's doc).
 */
internal const val KEY_THEME_MODE = "theme_mode"
internal const val KEY_LEGACY_PURE_DARK = "pure_dark"

/**
 * Reads the persisted theme mode, migrating from the legacy `pure_dark` boolean exactly
 * once per install: when `theme_mode` is absent, the old setting is translated into its
 * closest equivalent (dark vs light — there was no follow-system option before), the new
 * key is written, and the old key is removed. Subsequent reads go straight to `theme_mode`.
 * The migration is idempotent: any failure to write leaves the pref store unchanged and
 * this function simply returns the derived value for this session.
 */
internal fun readThemeMode(prefs: SharedPreferences): ThemeMode {
    prefs.getString(KEY_THEME_MODE, null)?.let { stored ->
        runCatching { ThemeMode.valueOf(stored) }.getOrNull()?.let { return it }
    }
    val legacyDark = prefs.getBoolean(KEY_LEGACY_PURE_DARK, true)
    val migrated = if (legacyDark) ThemeMode.DARK else ThemeMode.LIGHT
    prefs.edit()
        .putString(KEY_THEME_MODE, migrated.name)
        .remove(KEY_LEGACY_PURE_DARK)
        .apply()
    return migrated
}

fun loadSettingsState(prefs: SharedPreferences): SettingsState = SettingsState(
    themeMode = readThemeMode(prefs),
    forcedRtl = prefs.getBoolean("forced_rtl", false),
    gridDefault = prefs.getBoolean("grid_default", false),
    tetrisViewEnabled = prefs.getBoolean("tetris_view_enabled", true),
    showThumbnails = prefs.getBoolean("show_thumbnails", true),
    showDurations = prefs.getBoolean("show_durations", true),
    showFileSize = prefs.getBoolean("show_file_size", false),
    vhsEnabled = prefs.getBoolean("vhs_enabled", true),
    vhsIntensity = runCatching {
        VhsIntensity.valueOf(prefs.getString("vhs_intensity", null) ?: VhsIntensity.MED.name)
    }.getOrDefault(VhsIntensity.MED),
    tunerSeekBarEnabled = prefs.getBoolean("tuner_seekbar_enabled", true),
    tunerSeekStepSeconds = prefs.getInt("tuner_seek_step_seconds", 5).coerceIn(1, 20),
    memorySafety = prefs.getBoolean("memory_safety", false),
    fullFolderAccess = prefs.getBoolean("full_folder_access", false),
    screenshotMode = runCatching {
        ScreenshotMode.valueOf(prefs.getString("screenshot_mode", null) ?: ScreenshotMode.SINGLE.name)
    }.getOrDefault(ScreenshotMode.SINGLE),
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
        textColorArgb = prefs.getLong("subtitle_color_argb", 0xFFFFFFFFL),
        position = runCatching {
            SubtitlePosition.valueOf(prefs.getString("subtitle_position", null) ?: SubtitlePosition.BOTTOM.name)
        }.getOrDefault(SubtitlePosition.BOTTOM),
        bold = prefs.getBoolean("subtitle_bold", false),
        italic = prefs.getBoolean("subtitle_italic", false),
        underline = prefs.getBoolean("subtitle_underline", false),
        direction = runCatching {
            SubtitleDirection.valueOf(prefs.getString("subtitle_direction", null) ?: SubtitleDirection.AUTO.name)
        }.getOrDefault(SubtitleDirection.AUTO),
        secondaryDirection = runCatching {
            SubtitleDirection.valueOf(
                prefs.getString("subtitle_secondary_direction", null) ?: SubtitleDirection.AUTO.name
            )
        }.getOrDefault(SubtitleDirection.AUTO)
    )
)

/**
 * Writes every field of [state] to [prefs] in one batch `apply()`. Called on every
 * SettingsScreen.onStateChange, so any toggle — not just the 3 that used to be wired
 * (vhsEnabled/vhsIntensity/tunerSeekBarEnabled) — survives an app restart.
 */
fun saveSettingsState(prefs: SharedPreferences, state: SettingsState) {
    prefs.edit()
        .putString(KEY_THEME_MODE, state.themeMode.name)
        .putBoolean("forced_rtl", state.forcedRtl)
        .putBoolean("grid_default", state.gridDefault)
        .putBoolean("tetris_view_enabled", state.tetrisViewEnabled)
        .putBoolean("show_thumbnails", state.showThumbnails)
        .putBoolean("show_durations", state.showDurations)
        .putBoolean("show_file_size", state.showFileSize)
        .putString("vhs_intensity", state.vhsIntensity.name)
        .putBoolean("tuner_seekbar_enabled", state.tunerSeekBarEnabled)
        .putInt("tuner_seek_step_seconds", state.tunerSeekStepSeconds)
        .putBoolean("memory_safety", state.memorySafety)
        .putBoolean("full_folder_access", state.fullFolderAccess)
        .putString("screenshot_mode", state.screenshotMode.name)
        .putBoolean("continue_watching_enabled", state.continueWatchingEnabled)
        .putString("mt_mp3_output_path", state.mp3OutputPath)
        .putString("mt_compressed_output_path", state.compressedOutputPath)
        .putString("mt_trimmed_output_path", state.trimmedOutputPath)
        .putBoolean("mt_premium_unlocked", state.isPremiumUnlocked)
        .putBoolean("subtitle_auto_sync_enabled", state.autoSyncEnabled)
        .putBoolean("subtitle_enabled", state.subtitleStyle.enabled)
        .putInt("subtitle_size_sp", state.subtitleStyle.sizeSp)
        .putLong("subtitle_color_argb", state.subtitleStyle.textColorArgb)
        .putString("subtitle_position", state.subtitleStyle.position.name)
        .putBoolean("subtitle_bold", state.subtitleStyle.bold)
        .putBoolean("subtitle_italic", state.subtitleStyle.italic)
        .putBoolean("subtitle_underline", state.subtitleStyle.underline)
        .putString("subtitle_direction", state.subtitleStyle.direction.name)
        .putString("subtitle_secondary_direction", state.subtitleStyle.secondaryDirection.name)
        .apply()
}
