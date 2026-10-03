package com.watermelon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Font scale tuned for Farsi/Arabic-friendly typefaces (Manifest §1.1 RTL-Native).
 * Uses the platform default family (which carries Noto Naskh / Vazir on most devices);
 * a bundled Farsi typeface can replace [FontFamily.Default] without touching call sites.
 *
 * Expanded per the UI Design System spec's "professional typography" / "engineering
 * presentation board" requirement: the previous scale only covered 5 Material3 slots
 * and had no caption, overline/badge, or tabular-numeral style — so timecodes (seek bar,
 * duration labels) shifted width per-digit and badges had no dedicated small label style.
 *
 * ## The scale is now total
 *
 * Material3 slots that are not explicitly set fall back to the M3 baseline, which had two
 * visible consequences here. `bodySmall` (18 call sites — list-item metadata, dialog helper
 * text, secondary lines) and `titleSmall` (2 call sites) rendered in stock Roboto metrics,
 * and `headlineSmall` (8 call sites) landed at 24sp *Regular* — both larger and lighter than
 * the neighbouring custom slots, so text hierarchy read inconsistently across screens.
 * Every slot this app actually uses is now defined below, in a strictly descending order:
 *
 * ```
 * displayLarge 34 > headlineSmall 26 > titleLarge 20 > titleMedium 18
 *              > titleSmall 16 = bodyLarge 16 > bodyMedium 14 = labelLarge 14
 *              > bodySmall 12 = labelMedium 12 > labelSmall 10
 * ```
 *
 * Equal sizes at different weights (titleSmall vs bodyLarge, bodySmall vs labelMedium) are
 * intentional and are the Material3 convention for a semibold vs regular pairing.
 */
object WatermelonTypography {

    private val farsiFriendly = FontFamily.Default

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 34.sp, fontWeight = FontWeight.SemiBold),

        // Genuine screen-level headings only (TV screen headers, the Compress screen title).
        // Note that dialog and sheet titles deliberately do NOT use this slot — a dialog's title
        // is a title, so those call sites read titleLarge. Material3's default headlineSmall is
        // 24sp Regular, which rendered stock dialog titles in a light weight that looked thin
        // and floaty against the rest of this semibold industrial scale.
        headlineSmall = TextStyle(fontFamily = farsiFriendly, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),

        titleLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = farsiFriendly, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontFamily = farsiFriendly,
            fontSize = 16.sp,
            // Content text follows the locale direction; alignment resolves per layout.
            textDirection = TextDirection.Content,
            textAlign = TextAlign.Start
        ),
        bodyMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = farsiFriendly, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp, fontWeight = FontWeight.Medium),

        // ── New in the expanded scale ────────────────────────────────────────
        // Caption: secondary metadata under list items (file size, folder counts).
        labelMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 12.sp),

        // Overline / badge label: small caps-style tag for status badges ("NEW",
        // "4K", duration chips). Deliberately compact and letter-spaced.
        labelSmall = TextStyle(
            fontFamily = farsiFriendly,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    )

    /**
     * Timecode / numeric readout style (seek bar position, duration, sleep-timer
     * countdown). Tabular figures keep digit width constant so numbers don't visually
     * shift as they update — an "engineering board" detail the base Typography scale
     * doesn't express, since Material3's [Typography] has no numeric-only slot.
     */
    val timecode = TextStyle(
        fontFamily = farsiFriendly,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    )
}