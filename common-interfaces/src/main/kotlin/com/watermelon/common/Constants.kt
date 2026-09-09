package com.watermelon.common

/**
 * Shared constants for the Watermelon MediaPlayer.
 */

/**
 * Preferred language priority for subtitle search and discovery.
 * Used by:
 * - Online subtitle search (OpenSubtitlesProvider)
 * - Sidecar discovery
 * - Cache ordering
 */
val PLAYER_SUBTITLE_LANGUAGE_PRIORITY = listOf("fa", "ar", "ur", "ku", "en")