package com.watermelon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Composition-local flag for whether the app is currently in dark mode. Read by
 * [PlayerColors.current] so the custom-drawn player controls (which bypass Material3's
 * colorScheme entirely) stay in sync with the same toggle as everything else.
 */
val LocalWatermelonDarkTheme = compositionLocalOf { true }

/**
 * Material 3 theme with RTL-first layout overrides (Manifest §1.1, Teams §6) and a
 * dark/light toggle driven by Settings → Appearance → "Pure dark theme".
 *
 * Watermelon is RTL-native: for Persian/Arabic locales the entire layout direction is
 * inverted at the theme root, not mirrored per-widget. Pass [forceRtl] = true to force RTL
 * regardless of system locale (the "Forced RTL overrides per locale" setting).
 *
 * @param darkTheme true = dark scheme (default), false = light scheme. Bound to
 *   [com.watermelon.ui.screens.SettingsState.pureDark] by the app shell.
 */
@Composable
fun WatermelonTheme(
    darkTheme: Boolean = true,
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WatermelonColors.darkScheme() else WatermelonColors.lightScheme()

    val layoutDirection =
        if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalWatermelonDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WatermelonTypography.typography,
            // Not optional. Without this every stock Material3 surface (sheets, dialogs, text
            // fields, navigation bar) silently falls back to the M3 baseline 28dp radius and
            // renders pill-round next to this brand's sharp 0-14dp custom components.
            shapes = WatermelonShapes.shapes,
            content = content
        )
    }
}
