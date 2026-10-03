package com.watermelon.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Watermelon MediaPlayer brand palette — single source of truth for both dark and light
 * schemes. Raw hex values live ONLY here; [darkScheme] / [lightScheme] project them into a
 * **complete** Material3 [ColorScheme], so retuning the brand means editing this one file.
 *
 * Palette per the Watermelon MediaPlayer UI Design System spec: OLED-first dark interface,
 * Swiss-inspired minimalism, Sony Walkman / Nakamichi deck industrial influence.
 *
 * ## Why the role set is total
 *
 * Material3's `darkColorScheme()` / `lightColorScheme()` default every unset role to the M3
 * *baseline* palette, which is purple-grey (`surfaceContainer` family) with a pink tertiary.
 * Those defaults are what Material3 components read internally — `ModalBottomSheet` reads
 * `surfaceContainerLow`, `AlertDialog` reads `surfaceContainerHigh`, `TextField` reads
 * `surfaceContainerHighest`, `NavigationBar` reads `surfaceContainer`, `Switch` reads
 * `surfaceContainerHighest`/`surfaceVariant`. Setting only the ~11 roles this codebase reads
 * by hand left every stock Material3 surface rendering in baseline lavender-grey, punching
 * through an otherwise red/black industrial brand.
 *
 * So both schemes below are **total**: every role Material3 can read is defined here, and each
 * one is derived from the six brand swatches rather than from the M3 baseline. `surfaceTint`
 * is set explicitly too (it otherwise defaults to `primary`, which happens to be correct but
 * by accident rather than by decision).
 */
object WatermelonColors {

    /** Raw brand swatches. Do not reference these directly from components. */
    object Palette {
        val WatermelonRed  = Color(0xFFE63946)  // Primary — brand accent
        val DeepCarbon      = Color(0xFF0D0D0D)  // Surface — near-black OLED background
        val SlateGray       = Color(0xFF1A1A1A)  // Elevated — subtle elevation step
        val PaperWhite      = Color(0xFFF1FAEE)  // Text — primary on-dark text
        val SoftTeal        = Color(0xFF457B9D)  // Secondary — cool accent
        val WarningYellow   = Color(0xFFF4A261)  // Accent — warnings, buffering, badges
    }

    // ── Dark scheme (default / OLED-first) ──────────────────────────────────
    val DarkBackground       = Palette.DeepCarbon
    val DarkSurface          = Palette.SlateGray
    val DarkSurfaceVariant   = Palette.SlateGray
    val DarkOnBackground     = Palette.PaperWhite
    val DarkOnSurface        = Palette.PaperWhite
    val DarkOnSurfaceVariant = Palette.PaperWhite.copy(alpha = 0.70f)
    val DarkOutline          = Palette.PaperWhite.copy(alpha = 0.18f)

    // ── Light scheme ─────────────────────────────────────────────────────────
    // Watermelon is dark-mode-first; the light scheme is a secondary, less-emphasized
    // mode for users who explicitly opt out of "Pure dark theme" in Settings.
    val LightBackground       = Palette.PaperWhite
    val LightSurface          = Color(0xFFFFFFFF)
    val LightSurfaceVariant   = Palette.SlateGray.copy(alpha = 0.08f)
    val LightOnBackground     = Palette.DeepCarbon
    val LightOnSurface        = Palette.DeepCarbon
    val LightOnSurfaceVariant = Palette.DeepCarbon.copy(alpha = 0.65f)
    val LightOutline          = Palette.DeepCarbon.copy(alpha = 0.20f)

    // ── Shared accents (same in both modes) ─────────────────────────────────
    val Accent        = Palette.WatermelonRed   // primary
    val AccentVariant = Palette.SoftTeal         // secondary
    val Warning       = Palette.WarningYellow    // warnings, buffering, "new" badges
    // Dark ink on Watermelon Red provides a stronger normal-text contrast pair than Paper White.
    val OnAccent      = Palette.DeepCarbon
    val Error         = Palette.WatermelonRed

    // ── Dark surface elevation ladder ───────────────────────────────────────
    // Steps from DeepCarbon up to SlateGray, monotonically lightening by ~3 per channel.
    // These replace the M3 baseline lavender-greys that stock sheets/dialogs/menus read.
    private val DarkContainerLowest  = Color(0xFF090909)
    private val DarkContainerLow     = Color(0xFF131313)
    private val DarkContainer        = Color(0xFF1A1A1A)  // == Palette.SlateGray
    private val DarkContainerHigh    = Color(0xFF232323)
    private val DarkContainerHighest = Color(0xFF2C2C2C)
    private val DarkSurfaceDim       = Color(0xFF0A0A0A)
    private val DarkSurfaceBright    = Color(0xFF333333)

    // ── Dark container roles (muted, tonal — not the raw accents) ───────────
    private val DarkPrimaryContainer    = Color(0xFF4E1218)
    private val DarkOnPrimaryContainer  = Color(0xFFFFDBDE)
    private val DarkSecondaryContainer  = Color(0xFF2B4757)
    private val DarkOnSecondaryContainer = Color(0xFFCFE4F3)
    private val DarkTertiaryContainer   = Color(0xFF573719)  // WarningYellow, muted
    private val DarkOnTertiaryContainer = Color(0xFFFFE1C2)
    private val DarkErrorContainer      = Color(0xFF5A1419)
    private val DarkOnErrorContainer    = Color(0xFFFFDAD6)

    // ── Light surface elevation ladder ──────────────────────────────────────
    private val LightContainerLowest  = Color(0xFFFFFFFF)
    private val LightContainerLow     = Color(0xFFFAFDF6)
    private val LightContainer        = Color(0xFFF1FAEE)  // == Palette.PaperWhite
    private val LightContainerHigh    = Color(0xFFE8F1E5)
    private val LightContainerHighest = Color(0xFFDFE8DC)
    private val LightSurfaceDim       = Color(0xFFD5E0D2)
    private val LightSurfaceBright    = Color(0xFFFFFFFF)

    // ── Light container roles ───────────────────────────────────────────────
    private val LightPrimaryContainer    = Color(0xFFFFDAD9)
    private val LightOnPrimaryContainer  = Color(0xFF40060B)
    private val LightSecondaryContainer  = Color(0xFFD3E6F2)
    private val LightOnSecondaryContainer = Color(0xFF12242F)
    private val LightTertiaryContainer   = Color(0xFFFBE3CB)
    private val LightOnTertiaryContainer = Color(0xFF331C06)
    private val LightErrorContainer      = Color(0xFFFFDAD6)
    private val LightOnErrorContainer    = Color(0xFF410006)

    // ── Fixed roles: identical in both themes by definition ────────────────
    // These keep one tone across light/dark. Watermelon uses them for surfaces that must not
    // shift with the theme toggle — e.g. the tuner seeker's yellow indicator and timecode.
    private val PrimaryFixed            = Palette.WatermelonRed
    private val PrimaryFixedDim         = Color(0xFFB3261E)
    private val OnPrimaryFixed          = Color(0xFFFFFFFF)
    private val OnPrimaryFixedVariant   = Color(0xFFFFDAD9)
    private val SecondaryFixed          = Palette.SoftTeal
    private val SecondaryFixedDim       = Color(0xFF2C4759)
    private val OnSecondaryFixed        = Color(0xFFFFFFFF)
    private val OnSecondaryFixedVariant = Color(0xFFCFE4F3)
    private val TertiaryFixed           = Palette.WarningYellow
    private val TertiaryFixedDim        = Color(0xFF7A4A1E)
    private val OnTertiaryFixed         = Palette.DeepCarbon
    private val OnTertiaryFixedVariant  = Color(0xFFFFE1C2)

    /** Total dark [ColorScheme]. Every role Material3 can read is defined — no baseline leaks. */
    fun darkScheme(): ColorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = Color(0xFFFF8A93),
        secondary = AccentVariant,
        onSecondary = Palette.DeepCarbon,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = Warning,
        onTertiary = Palette.DeepCarbon,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = Accent,
        inverseSurface = Palette.PaperWhite,
        inverseOnSurface = Palette.DeepCarbon,
        error = Error,
        onError = Palette.DeepCarbon,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        outline = DarkOutline,
        outlineVariant = Palette.PaperWhite.copy(alpha = 0.12f),
        scrim = Color.Black,
        surfaceBright = DarkSurfaceBright,
        surfaceContainer = DarkContainer,
        surfaceContainerHigh = DarkContainerHigh,
        surfaceContainerHighest = DarkContainerHighest,
        surfaceContainerLow = DarkContainerLow,
        surfaceContainerLowest = DarkContainerLowest,
        surfaceDim = DarkSurfaceDim,
        primaryFixed = PrimaryFixed,
        primaryFixedDim = PrimaryFixedDim,
        onPrimaryFixed = OnPrimaryFixed,
        onPrimaryFixedVariant = OnPrimaryFixedVariant,
        secondaryFixed = SecondaryFixed,
        secondaryFixedDim = SecondaryFixedDim,
        onSecondaryFixed = OnSecondaryFixed,
        onSecondaryFixedVariant = OnSecondaryFixedVariant,
        tertiaryFixed = TertiaryFixed,
        tertiaryFixedDim = TertiaryFixedDim,
        onTertiaryFixed = OnTertiaryFixed,
        onTertiaryFixedVariant = OnTertiaryFixedVariant
    )

    /** Total light [ColorScheme]. Mirrors [darkScheme] role-for-role. */
    fun lightScheme(): ColorScheme = lightColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = Color(0xFFB3261E),
        secondary = AccentVariant,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = Color(0xFFB4701F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = Accent,
        inverseSurface = Color(0xFF2B2B2B),
        inverseOnSurface = Palette.PaperWhite,
        error = Error,
        onError = Palette.PaperWhite,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        outline = LightOutline,
        outlineVariant = Palette.DeepCarbon.copy(alpha = 0.12f),
        scrim = Color.Black,
        surfaceBright = LightSurfaceBright,
        surfaceContainer = LightContainer,
        surfaceContainerHigh = LightContainerHigh,
        surfaceContainerHighest = LightContainerHighest,
        surfaceContainerLow = LightContainerLow,
        surfaceContainerLowest = LightContainerLowest,
        surfaceDim = LightSurfaceDim,
        primaryFixed = PrimaryFixed,
        primaryFixedDim = PrimaryFixedDim,
        onPrimaryFixed = OnPrimaryFixed,
        onPrimaryFixedVariant = OnPrimaryFixedVariant,
        secondaryFixed = SecondaryFixed,
        secondaryFixedDim = SecondaryFixedDim,
        onSecondaryFixed = OnSecondaryFixed,
        onSecondaryFixedVariant = OnSecondaryFixedVariant,
        tertiaryFixed = TertiaryFixed,
        tertiaryFixedDim = TertiaryFixedDim,
        onTertiaryFixed = OnTertiaryFixed,
        onTertiaryFixedVariant = OnTertiaryFixedVariant
    )
}